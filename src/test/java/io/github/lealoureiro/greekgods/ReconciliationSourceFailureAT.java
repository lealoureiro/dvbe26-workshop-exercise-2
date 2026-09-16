package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Leave the catalogue intact when the third-party source fails</em>.
 *
 * <p>The second scenario needs more than "nothing crashed" — design D5 promises a
 * failure is recorded "in a form an operator can observe", so this test attaches a
 * real Logback {@link ListAppender} to {@link GreekGodReconciliationService}'s own
 * logger and asserts against an actual captured log record, rather than assuming the
 * failure was logged because the source code says so.
 */
class ReconciliationSourceFailureAT extends ReconciliationAcceptanceTestBase {

    private Logger reconciliationServiceLogger;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void captureReconciliationServiceLogs() {
        reconciliationServiceLogger =
            (Logger) LoggerFactory.getLogger(GreekGodReconciliationService.class);
        logCapture = new ListAppender<>();
        logCapture.start();
        reconciliationServiceLogger.addAppender(logCapture);
    }

    @AfterEach
    void detachLogCapture() {
        reconciliationServiceLogger.detachAppender(logCapture);
    }

    @Test
    @DisplayName("Scenario: The third-party source is unavailable during a run")
    void theThirdPartySourceIsUnavailableDuringARun() {
        // WHEN reconciliation runs and the third-party source is unavailable
        long gaia = givenTheCatalogueHolds("Gaia");
        long uranus = givenTheCatalogueHolds("Uranus");
        givenTheSourceIsUnavailable();

        reconciliationRuns();

        // THEN the catalogue still holds the records it held before the run
        // AND a request for the Greek gods still succeeds
        var records = whenTheGreekGodsAreRequested();
        assertThat(records).extracting(GreekGod::id).containsExactlyInAnyOrder(gaia, uranus);
    }

    @Test
    @DisplayName("Scenario: A failed run is observable")
    void aFailedRunIsObservable() {
        // WHEN reconciliation runs and the third-party source fails
        givenTheSourceIsUnavailable();

        reconciliationRuns();

        // THEN the failure is recorded in a form an operator can observe —
        // asserted against an actual captured log record, not assumed
        assertThat(logCapture.list)
            .as("a failed run must leave an observable record of the failure (design D5)")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("reconciliation.run_failed");
            });

        // AND the request path is unaffected
        whenTheGreekGodsAreRequested();
    }
}
