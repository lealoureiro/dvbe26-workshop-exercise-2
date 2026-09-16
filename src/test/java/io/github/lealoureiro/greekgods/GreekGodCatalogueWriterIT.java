package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GreekGodCatalogueWriter} against real PostgreSQL (design D5): the
 * case-insensitive unique constraint decides what is new (task 3.2, design
 * D2/D3), a name already held keeps its identifier (task 3.1, design D1), and
 * nothing here has any path that removes a record or reassigns an identifier
 * (task 3.4).
 *
 * <p>Names are deliberately disjoint from other {@code *IT} classes sharing
 * this database, so this class stays independent without needing a delete
 * path that this change deliberately does not ship (design D2).
 */
@Testcontainers
@SpringBootTest
class GreekGodCatalogueWriterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private GreekGodCatalogueWriter catalogueWriter;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void takesUpANameTheCatalogueHasNotSeen() {
        boolean inserted = catalogueWriter.takeUpIfUnseen("Hypnos");

        assertThat(inserted).isTrue();
        assertThat(countGodsNamed("Hypnos")).isEqualTo(1);
    }

    @Test
    void leavesAnAlreadyHeldNameUntouchedAndDoesNotChangeItsIdentifier() {
        long nike = insertDirectly("Nike");

        boolean insertedAgain = catalogueWriter.takeUpIfUnseen("Nike");

        assertThat(insertedAgain)
            .as("a name already held must not be taken up a second time")
            .isFalse();
        assertThat(countGodsNamed("Nike")).isEqualTo(1);
        assertThat(identifierOf("Nike"))
            .as("the identifier a consumer may already hold must never change")
            .isEqualTo(nike);
    }

    @Test
    void recognisesACaseVariantOfAStoredNameAsTheSameGod() {
        long janus = insertDirectly("Janus");

        boolean insertedVariant = catalogueWriter.takeUpIfUnseen("JANUS");

        assertThat(insertedVariant).isFalse();
        assertThat(countGodsNamed("Janus")).isEqualTo(1);
        assertThat(identifierOf("Janus")).isEqualTo(janus);
    }

    @Test
    void takingUpTheSameNameTwiceInARowChangesNothingTheSecondTime() {
        // Repeating reconciliation must change nothing (design: convergence
        // under repetition) — exercised here directly against the writer,
        // independent of GreekGodReconciliationService's own loop.
        boolean firstAttempt = catalogueWriter.takeUpIfUnseen("Tyche");
        boolean secondAttempt = catalogueWriter.takeUpIfUnseen("Tyche");

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        assertThat(countGodsNamed("Tyche")).isEqualTo(1);
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
