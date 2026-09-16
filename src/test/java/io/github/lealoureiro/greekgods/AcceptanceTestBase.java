package io.github.lealoureiro.greekgods;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared setup for the acceptance tests: the whole application on a real port,
 * backed by real PostgreSQL (design D5), driven over real HTTP.
 *
 * <p>Every subclass starts each test from an <em>empty</em> catalogue and then states
 * the catalogue contents the scenario calls for. The specification scenarios are
 * written in terms of what the catalogue holds — "holds Zeus and Hera", "holds only
 * Zeus", "is empty" — so a test cannot cover one faithfully unless it controls the
 * contents exactly. Truncating here is what makes that possible, and it also makes
 * the tests independent of each other and of execution order.
 *
 * <p>The truncate lives in test code only. The application itself still ships no
 * delete and no update path (design D2); nothing in {@code src/main} can remove a
 * record, and these fixtures do not change that.
 *
 * <p>Identifiers are deliberately <em>not</em> reset with {@code RESTART IDENTITY}.
 * Letting them keep climbing across tests stops any assertion from quietly depending
 * on a god having id 1, which would be a fact about the fixture rather than about the
 * published behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AcceptanceTestBase {

    protected static final String ENDPOINT = "/api/v1/gods/greek";

    protected static final ParameterizedTypeReference<List<GreekGod>> CATALOGUE =
        new ParameterizedTypeReference<>() { };

    /**
     * One PostgreSQL for every acceptance class, started once and left running.
     *
     * <p>Deliberately started here rather than through {@code @Testcontainers} and
     * {@code @Container}: that extension stops a static container when the class
     * declaring it finishes, and an inherited static field is stopped after the
     * <em>first</em> subclass runs — leaving every later subclass pointing at a dead
     * container through a cached Spring context. Starting it manually keeps it up for
     * the whole JVM; Testcontainers' own reaper removes it when the JVM exits.
     * {@code @ServiceConnection} is honoured either way, since Spring Boot discovers
     * it from the static field rather than from the JUnit extension.
     */
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static {
        postgres.start();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    /** Drives the running application over real HTTP, exactly as a consumer would. */
    protected RestTestClient consumer;

    @BeforeEach
    void startFromAnEmptyCatalogue() {
        consumer = RestTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        jdbcClient.sql("TRUNCATE TABLE greek_god").update();
    }

    /**
     * Puts a god in the catalogue, as reconciliation eventually will.
     *
     * <p>Only the name is supplied: {@code id} is {@code GENERATED ALWAYS AS IDENTITY},
     * so not even a fixture can choose an identifier.
     *
     * @return the identifier the database minted
     */
    protected long givenTheCatalogueHolds(String name) {
        return jdbcClient.sql("INSERT INTO greek_god (name) VALUES (?) RETURNING id")
            .param(name)
            .query(Long.class)
            .single();
    }

    /**
     * Moves a row to the end of the table's physical storage without changing
     * anything a consumer can observe.
     *
     * <p>PostgreSQL's MVCC writes a new tuple version on every {@code UPDATE}, even
     * one that assigns a column to itself, and the new version lands after the
     * existing rows. A query with no {@code ORDER BY} is then very likely to return
     * this row <em>last</em>, although it still holds the lowest identifier.
     *
     * <p>This is what gives the ordering tests something to discriminate. Without it
     * a freshly seeded table returns rows in insertion order anyway, so an
     * unordered query satisfies an ascending-order assertion by accident — the whole
     * guarantee could be deleted from the production query and the tests would stay
     * green.
     *
     * <p>The catalogue's <em>contents</em> are unchanged: same identifiers, same
     * names. Only the storage layout moves. This is fixture manipulation in test
     * code alone; the application still ships no update and no delete path, so D2
     * remains structurally true.
     */
    protected void moveRowToEndOfPhysicalStorage(long id) {
        jdbcClient.sql("UPDATE greek_god SET name = name WHERE id = ?")
            .param(id)
            .update();
    }

    /** Requests the Greek gods and asserts only that the request succeeded with JSON. */
    protected List<GreekGod> whenTheGreekGodsAreRequested() {
        return consumer.get()
            .uri(ENDPOINT)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(CATALOGUE)
            .returnResult()
            .getResponseBody();
    }
}
