package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Take up names the catalogue has not seen</em>.
 */
class ReconciliationTakeUpAT extends ReconciliationAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: An unseen name is taken up")
    void anUnseenNameIsTakenUp() {
        // WHEN the third-party source offers "Poseidon", the catalogue does not
        // hold it, and reconciliation runs
        givenTheSourceOffers("Poseidon");

        reconciliationRuns();

        // THEN a later request for the Greek gods contains a record for "Poseidon"
        // AND "Poseidon" is published with an identifier no other record holds
        var records = whenTheGreekGodsAreRequested();
        assertThat(records).extracting(GreekGod::name).contains("Poseidon");
        assertThat(records).extracting(GreekGod::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Scenario: Only the unseen names are taken up")
    void onlyTheUnseenNamesAreTakenUp() {
        // WHEN the third-party source offers "Zeus", "Hera" and "Poseidon",
        // the catalogue already holds "Zeus" and "Hera", and reconciliation runs
        long zeus = givenTheCatalogueHolds("Zeus");
        long hera = givenTheCatalogueHolds("Hera");
        givenTheSourceOffers("Zeus", "Hera", "Poseidon");

        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN "Zeus" and "Hera" keep the identifiers they were already published with
        assertThat(records).filteredOn(god -> god.name().equals("Zeus"))
            .extracting(GreekGod::id).containsExactly(zeus);
        assertThat(records).filteredOn(god -> god.name().equals("Hera"))
            .extracting(GreekGod::id).containsExactly(hera);

        // AND a record for "Poseidon" is published with an identifier of its own
        assertThat(records).filteredOn(god -> god.name().equals("Poseidon"))
            .extracting(GreekGod::id)
            .hasSize(1)
            .doesNotContain(zeus, hera);
    }
}
