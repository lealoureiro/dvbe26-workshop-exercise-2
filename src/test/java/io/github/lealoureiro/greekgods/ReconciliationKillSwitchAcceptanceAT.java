package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Specification requirement:
 * <em>Let an operator stop reconciliation</em>.
 *
 * <p>"No call is made" is asserted positively with {@code verifyNoInteractions} on
 * the mock standing in for the third-party source — not inferred from the
 * catalogue being unchanged, which would also be true if the source had been
 * called and simply offered nothing new.
 */
class ReconciliationKillSwitchAcceptanceAT extends ReconciliationAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: Reconciliation is stopped")
    void reconciliationIsStopped() {
        // WHEN an operator stops reconciliation and a run would otherwise have
        // been due
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers("Poseidon");
        System.setProperty(ReconciliationKillSwitch.PROPERTY, "false");

        reconciliationRuns();

        // THEN no call is made to the third-party source
        verifyNoInteractions(sourceClient);

        // AND the catalogue still holds every record it held
        // AND a request for the Greek gods still succeeds
        assertThat(whenTheGreekGodsAreRequested())
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }

    @Test
    @DisplayName("Scenario: Reconciliation is started again")
    void reconciliationIsStartedAgain() {
        givenTheSourceOffers("Poseidon");

        // Stopped first: a run that would otherwise have been due does nothing.
        System.setProperty(ReconciliationKillSwitch.PROPERTY, "false");
        reconciliationRuns();

        // WHEN an operator starts reconciliation again and a run becomes due
        System.setProperty(ReconciliationKillSwitch.PROPERTY, "true");
        reconciliationRuns();

        // THEN names offered by the third-party source and absent from the
        // catalogue are taken up
        assertThat(whenTheGreekGodsAreRequested())
            .extracting(GreekGod::name)
            .contains("Poseidon");
    }

    @Test
    @DisplayName("Scenario: The control cannot be read")
    void theControlCannotBeRead() {
        // WHEN the setting governing reconciliation cannot be read and a run
        // would otherwise have been due
        long zeus = givenTheCatalogueHolds("Zeus");
        givenTheSourceOffers("Poseidon");
        System.setProperty(ReconciliationKillSwitch.PROPERTY, "maybe");

        reconciliationRuns();

        // THEN no call is made to the third-party source
        verifyNoInteractions(sourceClient);

        // AND a request for the Greek gods still succeeds
        assertThat(whenTheGreekGodsAreRequested())
            .extracting(GreekGod::id)
            .containsExactly(zeus);
    }
}
