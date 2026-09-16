package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 6.3: the read capability's requirements still hold, unchanged, after a
 * reconciliation run — including the ascending-identifier-order guarantee
 * {@code greek-gods-catalogue-read} publishes and {@link OpenApiContractTest}
 * already guards at the contract level for every response the controller can
 * serve. This change adds no API surface of its own — design.md states
 * "changing anything about what a consumer receives" as an explicit non-goal —
 * so there is nothing new to add to {@code oas/greekController-oas.yaml}; this
 * test is the acceptance-level evidence that reconciliation does not quietly
 * break what is already published there.
 *
 * <p>Rows are deliberately moved to the end of physical storage before
 * reconciliation runs, exactly as {@link CatalogueOrderingAT} does, so an
 * unordered query would fail this assertion rather than pass it by accident of
 * insertion order.
 */
class ReconciliationPreservesReadCapabilityAT extends ReconciliationAcceptanceTestBase {

    @Test
    @DisplayName("Reconciliation preserves ascending identifier order and the published read contract")
    void reconciliationPreservesAscendingIdentifierOrderAndThePublishedContract() {
        // Given records already held, physically stored out of identifier order.
        long nyx = givenTheCatalogueHolds("Nyx");
        long eros = givenTheCatalogueHolds("Eros");
        moveRowToEndOfPhysicalStorage(nyx);

        givenTheSourceOffers("Nyx", "Thanatos");

        reconciliationRuns();

        // THEN a request for the Greek gods still succeeds and still serves
        // JSON (both asserted inside whenTheGreekGodsAreRequested, matching
        // the contract OpenApiContractTest guards)...
        var records = whenTheGreekGodsAreRequested();

        // ...contains the pre-existing and the newly taken-up records alike...
        assertThat(records).extracting(GreekGod::name)
            .containsExactlyInAnyOrder("Nyx", "Eros", "Thanatos");

        // ...with no identifier ever reused...
        assertThat(records).extracting(GreekGod::id).doesNotHaveDuplicates();

        // ...and still lists them ascending by identifier, despite the
        // physical storage disturbance above.
        assertThat(records).extracting(GreekGod::id).isSorted();
        assertThat(records).filteredOn(god -> god.name().equals("Nyx"))
            .extracting(GreekGod::id).containsExactly(nyx);
        assertThat(records).filteredOn(god -> god.name().equals("Eros"))
            .extracting(GreekGod::id).containsExactly(eros);
    }
}
