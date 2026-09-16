package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Publish unique identifiers that the service cannot reassign</em>.
 */
class CatalogueIdentifiersAT extends AcceptanceTestBase {

    @Test
    @DisplayName("Scenario: Identifiers distinguish records")
    void identifiersDistinguishRecords() {
        // WHEN an API consumer requests the Greek gods
        // and the catalogue holds more than one record
        givenTheCatalogueHolds("Selene");
        givenTheCatalogueHolds("Helios");
        givenTheCatalogueHolds("Eos");

        var records = whenTheGreekGodsAreRequested();

        // THEN no two records share an identifier
        assertThat(records).hasSize(3);
        assertThat(records).extracting(GreekGod::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Scenario: The published interface offers no way to alter a record")
    void thePublishedInterfaceOffersNoWayToAlterARecord() {
        long pan = givenTheCatalogueHolds("Pan");

        // WHEN an API consumer examines the operations this capability publishes
        // THEN no operation removes a record, changes a name, or changes an identifier.
        // Every method that could alter a record is refused at the published endpoint.
        for (HttpMethod altering : new HttpMethod[] {
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE }) {

            consumer.method(altering)
                .uri(ENDPOINT)
                .exchange()
                .expectStatus().isEqualTo(405);
        }

        // and the record is still there, unchanged, after all of them were attempted
        assertThat(whenTheGreekGodsAreRequested()).containsExactly(new GreekGod(pan, "Pan"));
    }
}
