package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link GreekGodReconciliationService} wired to the real
 * {@link GreekGodCatalogueWriter} against PostgreSQL (design D5), with only
 * {@link GreekGodSourceClient} replaced — this is the seam between "what the
 * third party offers" and "what the catalogue holds", exercised end to end
 * except for the actual outbound HTTP call.
 *
 * <p>Covers, against a real database rather than mocks: per-name isolation on
 * failure (task 3.3, design D8) using a name too long for the {@code name}
 * column as a deterministic, real {@code DataIntegrityViolationException}
 * — no database outage needs to be simulated to prove the remaining names
 * still get taken up.
 */
@Testcontainers
@SpringBootTest
class GreekGodReconciliationServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @MockitoBean
    private GreekGodSourceClient sourceClient;

    @Autowired
    private GreekGodReconciliationService reconciliationService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void takesUpOnlyTheNamesNotAlreadyHeld() {
        long athena = insertDirectly("Athena");
        when(sourceClient.fetchNames()).thenReturn(List.of("athena", "Demeter"));

        reconciliationService.reconcile();

        assertThat(countGodsNamed("Athena")).isEqualTo(1);
        assertThat(identifierOf("Athena"))
            .as("a name already held, offered again in a different case, keeps its identifier")
            .isEqualTo(athena);
        assertThat(countGodsNamed("Demeter")).isEqualTo(1);
    }

    @Test
    void aNameThatFailsToBeTakenUpDoesNotStopTheRun() {
        String tooLongForTheColumn = "X".repeat(101);
        when(sourceClient.fetchNames()).thenReturn(List.of("Ares", tooLongForTheColumn, "Hephaestus"));

        reconciliationService.reconcile();

        assertThat(countGodsNamed("Ares"))
            .as("a name taken up before the failing one must still be committed (design D8)")
            .isEqualTo(1);
        assertThat(countGodsNamed("Hephaestus"))
            .as("a name offered after the failing one must still be taken up (design D8)")
            .isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM greek_god WHERE length(name) > 100")
                .query(Integer.class).single())
            .as("the name that failed must not have been partially written")
            .isZero();
    }

    @Test
    void skipsAnEmptyNameOfferedAlongsideValidOnes() {
        when(sourceClient.fetchNames()).thenReturn(Arrays.asList("", "  ", "Hecate"));

        reconciliationService.reconcile();

        assertThat(countGodsNamed("Hecate")).isEqualTo(1);
    }

    @Test
    void aSourceFailureLeavesTheCatalogueExactlyAsItWas() {
        long iris = insertDirectly("Iris");
        when(sourceClient.fetchNames())
            .thenThrow(new ResourceAccessException(
                "simulated source unavailable", new IOException("connection refused")));

        reconciliationService.reconcile();

        assertThat(countGodsNamed("Iris")).isEqualTo(1);
        assertThat(identifierOf("Iris")).isEqualTo(iris);
    }

    private long insertDirectly(String name) {
        return jdbcClient.sql("INSERT INTO greek_god (name) VALUES (?) RETURNING id")
            .param(name)
            .query(Long.class)
            .single();
    }

    private int countGodsNamed(String name) {
        return jdbcClient.sql("SELECT COUNT(*) FROM greek_god WHERE lower(name) = lower(?)")
            .param(name)
            .query(Integer.class)
            .single();
    }

    private long identifierOf(String name) {
        return jdbcClient.sql("SELECT id FROM greek_god WHERE lower(name) = lower(?)")
            .param(name)
            .query(Long.class)
            .single();
    }
}
