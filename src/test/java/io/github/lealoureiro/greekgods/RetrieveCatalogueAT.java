package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement: <em>Retrieve the Greek gods catalogue</em>.
 */
class RetrieveCatalogueAT extends AcceptanceTestBase {

    @Test
    @DisplayName("Scenario: Catalogue with several records is retrieved")
    void catalogueWithSeveralRecordsIsRetrieved() {
        // WHEN an API consumer requests the Greek gods
        // and the catalogue holds "Zeus" and "Hera"
        givenTheCatalogueHolds("Zeus");
        givenTheCatalogueHolds("Hera");

        // THEN the request succeeds AND the response is JSON
        // (both asserted inside whenTheGreekGodsAreRequested)
        var records = whenTheGreekGodsAreRequested();

        // AND the response contains a record for "Zeus" and a record for "Hera"
        assertThat(records).extracting(GreekGod::name)
            .containsExactlyInAnyOrder("Zeus", "Hera");

        // AND every record carries a whole-number identifier and a name
        assertThat(records).allSatisfy(god -> {
            assertThat(god.id()).isPositive();
            assertThat(god.name()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Scenario: Catalogue with a single record is retrieved")
    void catalogueWithASingleRecordIsRetrieved() {
        // WHEN an API consumer requests the Greek gods
        // and the catalogue holds only "Zeus"
        long zeus = givenTheCatalogueHolds("Zeus");

        // THEN the request succeeds
        var records = whenTheGreekGodsAreRequested();

        // AND the response contains exactly one record AND that record is for "Zeus"
        assertThat(records).containsExactly(new GreekGod(zeus, "Zeus"));
    }

    @Test
    @DisplayName("Scenario: No credential is required")
    void noCredentialIsRequired() {
        givenTheCatalogueHolds("Hestia");

        // WHEN an API consumer requests the Greek gods without presenting any credential
        // (no Authorization header, no cookie, no API key is ever set on this client)
        consumer.get()
            .uri(ENDPOINT)
            .exchange()
            // THEN the request succeeds
            .expectStatus().isOk()
            // and the service issues no challenge, so nothing prompts for a credential
            .expectHeader().doesNotExist("WWW-Authenticate");
    }
}
