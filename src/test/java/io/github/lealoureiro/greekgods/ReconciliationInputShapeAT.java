package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Take up only names that conform to the expected shape</em>.
 */
class ReconciliationInputShapeAT extends ReconciliationAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: A padded form of a stored name is offered")
    void aPaddedFormOfAStoredNameIsOffered() {
        // WHEN the catalogue holds "Zeus", an API consumer has noted its
        // published identifier, the third-party source offers " Zeus " with
        // surrounding whitespace, and reconciliation runs
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers(" Zeus ");

        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN the catalogue holds one record for that god
        // AND it keeps the identifier the consumer noted
        assertThat(records).filteredOn(god -> god.name().equalsIgnoreCase("zeus")).hasSize(1);
        assertThat(records).filteredOn(god -> god.name().equalsIgnoreCase("zeus"))
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }

    @Test
    @DisplayName("Scenario: An empty name is offered alongside valid ones")
    void anEmptyNameIsOfferedAlongsideValidOnes() {
        // WHEN the third-party source offers an empty name together with
        // "Hera", and reconciliation runs
        givenTheSourceOffers("", "Hera");

        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN no record is created for the empty name
        assertThat(records).extracting(GreekGod::name).doesNotContain("", "  ");

        // AND a record for "Hera" is published with an identifier of its own
        assertThat(records).filteredOn(god -> god.name().equals("Hera"))
            .hasSize(1)
            .allSatisfy(god -> assertThat(god.id()).isPositive());
    }
}
