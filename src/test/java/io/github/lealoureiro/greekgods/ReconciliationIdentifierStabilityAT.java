package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirements:
 * <em>Never reassign a published identifier</em>,
 * <em>Keep records the third-party source no longer offers</em>, and
 * <em>Recognise a name differing only in letter case as the same god</em>.
 */
class ReconciliationIdentifierStabilityAT extends ReconciliationAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: A name already held is offered again")
    void aNameAlreadyHeldIsOfferedAgain() {
        // WHEN the catalogue holds "Zeus", an API consumer has noted its
        // published identifier, the third-party source offers "Zeus", and
        // reconciliation runs
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers("Zeus");

        reconciliationRuns();

        // THEN "Zeus" keeps the identifier the consumer noted
        assertThat(whenTheGreekGodsAreRequested())
            .filteredOn(god -> god.name().equals("Zeus"))
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }

    @Test
    @DisplayName("Scenario: Repeating reconciliation changes nothing")
    void repeatingReconciliationChangesNothing() {
        // WHEN the catalogue holds "Zeus" and "Hera" and reconciliation runs
        // twice with the third-party source unchanged
        long zeus = givenTheCatalogueHolds("Zeus");
        long hera = givenTheCatalogueHolds("Hera");
        givenTheSourceOffers("Zeus", "Hera");

        reconciliationRuns();
        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN each god keeps the identifier it was published with
        // AND the catalogue holds no further records
        assertThat(records).hasSize(2);
        assertThat(records).extracting(GreekGod::id).containsExactlyInAnyOrder(zeus, hera);
    }

    @Test
    @DisplayName("Scenario: An offered name disappears")
    void anOfferedNameDisappears() {
        // WHEN the catalogue holds "Zeus", an API consumer has noted its
        // published identifier, the third-party source stops offering "Zeus",
        // and reconciliation runs
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers(); // the source now offers nothing, including not "Zeus"

        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN a later request for the Greek gods still contains a record for "Zeus"
        // AND "Zeus" keeps the identifier the consumer noted
        assertThat(records).filteredOn(god -> god.name().equals("Zeus"))
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }

    @Test
    @DisplayName("Scenario: A case variant of a stored name is offered")
    void aCaseVariantOfAStoredNameIsOffered() {
        // WHEN the catalogue holds "Zeus", an API consumer has noted its
        // published identifier, the third-party source offers "zeus", and
        // reconciliation runs
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers("zeus");

        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN the catalogue holds one record for that god
        // AND it keeps the identifier the consumer noted
        assertThat(records).filteredOn(god -> god.name().equalsIgnoreCase("zeus")).hasSize(1);
        assertThat(records).filteredOn(god -> god.name().equalsIgnoreCase("zeus"))
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }
}
