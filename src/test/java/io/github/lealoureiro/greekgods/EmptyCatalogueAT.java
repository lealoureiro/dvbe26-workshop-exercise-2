package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Represent an empty catalogue as a successful empty result</em>.
 *
 * <p>This class seeds nothing. The base class truncates before each test, so the
 * catalogue really is empty when the request is made — the emptiness is observed
 * end-to-end through HTTP against real PostgreSQL, not simulated by stubbing the
 * repository.
 */
class EmptyCatalogueAT extends AcceptanceTestBase {

    @Test
    @DisplayName("Scenario: Catalogue holds no records")
    void catalogueHoldsNoRecords() {
        // WHEN an API consumer requests the Greek gods and the catalogue is empty

        // THEN the request succeeds
        var records = whenTheGreekGodsAreRequested();

        // AND the response contains no records
        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("An empty catalogue is served as an empty array, not as a failure")
    void anEmptyCatalogueIsAnEmptyArrayAndNotAFailure() {
        consumer.get()
            .uri(ENDPOINT)
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }
}
