package io.github.lealoureiro.greekgods;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural guarantees of the {@code greek_god} schema, exercised against real
 * PostgreSQL (design D5). The guarantees under test — identity generation and a
 * case-insensitive unique constraint — are engine-specific, so an in-memory
 * substitute would test a different engine than the one that runs in production.
 *
 * <p>No test cleans up after itself. Each test owns a distinct set of names
 * instead, so the tests stay independent and order-insensitive without needing a
 * DELETE path that this change deliberately does not ship (design D2).
 */
@Testcontainers
@SpringBootTest
class GreekGodSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void migrationCreatesGreekGodWithADatabaseGeneratedIdentity() {
        assertThat(flyway.info().applied())
            .extracting(migration -> migration.getVersion().getVersion())
            .containsExactly("1");

        var identityGeneration = jdbcClient.sql("""
                SELECT identity_generation
                FROM information_schema.columns
                WHERE table_name = 'greek_god'
                  AND column_name = 'id'
                """)
            .query(String.class)
            .optional();

        assertThat(identityGeneration)
            .as("id must be minted by the database, and not overridable by a caller")
            .contains("ALWAYS");
    }

    @Test
    void rejectsAnExactDuplicateName() {
        insertGod("Zeus");

        assertThatThrownBy(() -> insertGod("Zeus"))
            .isInstanceOf(DuplicateKeyException.class);

        assertThat(countGodsNamed("Zeus")).isEqualTo(1);
    }

    @Test
    void rejectsANameDifferingOnlyInLetterCase() {
        insertGod("Hera");

        assertThatThrownBy(() -> insertGod("HERA"))
            .as("the natural key is case-insensitive, so HERA is the same god as Hera")
            .isInstanceOf(DuplicateKeyException.class);

        assertThatThrownBy(() -> insertGod("hera"))
            .isInstanceOf(DuplicateKeyException.class);

        assertThat(countGodsNamed("Hera")).isEqualTo(1);
    }

    @Test
    void assignsDistinctIdentifiersToDistinctNames() {
        long poseidon = insertGod("Poseidon");
        long athena = insertGod("Athena");

        assertThat(poseidon).isNotEqualTo(athena);
    }

    private long insertGod(String name) {
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
}
