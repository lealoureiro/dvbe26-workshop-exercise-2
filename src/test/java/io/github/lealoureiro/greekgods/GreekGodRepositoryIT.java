package io.github.lealoureiro.greekgods;

import java.util.List;

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
 * The read path against real PostgreSQL (design D5).
 *
 * <p>The ordering guarantee is the reason this test exists. {@code GreekGodControllerTest}
 * mocks the repository, so it cannot tell an ordered query from an unordered one —
 * only a real database can, and only with rows inserted out of alphabetical order so
 * that ascending-by-identifier and ascending-by-name disagree.
 *
 * <p>Names here are deliberately disjoint from those in {@code GreekGodSchemaIT}, so
 * the two classes stay independent without needing a delete path (design D2).
 */
@Testcontainers
@SpringBootTest
class GreekGodRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private GreekGodRepository greekGodRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void returnsEveryRecordInAscendingIdentifierOrder() {
        // Inserted in an order that is neither alphabetical nor reverse-alphabetical,
        // so sorting by name would produce a different sequence than sorting by id.
        long hermes = insertGod("Hermes");
        long aphrodite = insertGod("Aphrodite");
        long dionysus = insertGod("Dionysus");

        // Hermes holds the lowest of the three identifiers but now sits last in
        // physical storage: PostgreSQL's MVCC writes a new tuple version on update,
        // after the existing rows. Without this, a freshly seeded table returns rows
        // in insertion order whether or not the query asks for an order, and the
        // assertions below would pass with the ORDER BY deleted.
        jdbcClient.sql("UPDATE greek_god SET name = name WHERE id = ?")
            .param(hermes)
            .update();

        var catalogue = greekGodRepository.findAll();

        // Holds whatever else the shared database contains, and fails outright if
        // the query stops ordering or lets the planner choose.
        assertThat(catalogue).extracting(GreekGod::id).isSorted();

        // These three must appear in insertion order. Ordering by name instead would
        // yield Aphrodite, Dionysus, Hermes; no ordering at all would be arbitrary.
        var inserted = List.of(hermes, aphrodite, dionysus);
        assertThat(catalogue)
            .filteredOn(god -> inserted.contains(god.id()))
            .extracting(GreekGod::name)
            .containsExactly("Hermes", "Aphrodite", "Dionysus");
    }

    @Test
    void readsTheNameStoredForEachIdentifier() {
        long apollo = insertGod("Apollo");

        assertThat(greekGodRepository.findAll())
            .contains(new GreekGod(apollo, "Apollo"));
    }

    private long insertGod(String name) {
        return jdbcClient.sql("INSERT INTO greek_god (name) VALUES (?) RETURNING id")
            .param(name)
            .query(Long.class)
            .single();
    }
}
