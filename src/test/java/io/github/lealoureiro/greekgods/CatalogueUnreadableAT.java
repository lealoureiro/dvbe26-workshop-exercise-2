package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Report a service-side failure distinctly from an empty catalogue</em>.
 *
 * <p>This class deliberately does <strong>not</strong> extend {@code AcceptanceTestBase}.
 * It makes the catalogue genuinely unreadable by stopping its database mid-test, which
 * would poison every sibling test sharing that container. Its own container, its own
 * context, and {@code @DirtiesContext} keep the damage inside this class.
 *
 * <p>The failure is provoked for real rather than simulated: PostgreSQL is stopped, so
 * the read fails the way it would in production when the database is unreachable. The
 * connection timeout is shortened so the failure arrives promptly instead of after
 * Hikari's default thirty seconds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.hikari.connection-timeout=2000",
    "spring.datasource.hikari.validation-timeout=1000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogueUnreadableAT {

    private static final String ENDPOINT = "/api/v1/gods/greek";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private static void assertThatJson(String body, java.util.function.Consumer<DocumentContext> assertions) {
        assertions.accept(JsonPath.parse(body));
    }

    @Test
    @DisplayName("Scenario: Catalogue cannot be read")
    void catalogueCannotBeRead() {
        RestTestClient consumer = RestTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        // The catalogue holds a record, and is readable, before anything is broken.
        // Without this the 500 below could not be distinguished from a service that
        // never worked at all.
        jdbcClient.sql("INSERT INTO greek_god (name) VALUES ('Tartarus')").update();
        consumer.get().uri(ENDPOINT).exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$[0].name").isEqualTo("Tartarus");

        // WHEN an API consumer requests the Greek gods and the catalogue cannot be read
        postgres.stop();

        String body = consumer.get().uri(ENDPOINT).exchange()
            // THEN the consumer is told the failure is the service's own
            .expectStatus().isEqualTo(500)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(body).isNotNull();
        assertThatJson(body, problem -> {
            assertThat(problem.read("$.title", String.class)).isEqualTo("Catalogue unavailable");
            assertThat(problem.read("$.status", Integer.class)).isEqualTo(500);
            assertThat(problem.read("$.errorId", String.class)).isNotBlank();
            assertThat(problem.read("$.detail", String.class))
                .as("the cause stays server-side")
                .doesNotContain("greek_god")
                .doesNotContain("postmaster");
        });

        // AND the response does not present an empty catalogue as a success
        assertThat(body)
            .as("a consumer must be able to tell this apart from 'nothing is recorded'")
            .isNotEqualTo("[]")
            .isNotBlank();
    }
}
