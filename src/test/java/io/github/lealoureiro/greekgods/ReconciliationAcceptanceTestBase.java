package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.Mockito.when;

/**
 * Shared setup for the reconciliation acceptance tests (group 5): the whole
 * application on a real port, backed by real PostgreSQL, exactly like
 * {@link AcceptanceTestBase} — with {@link GreekGodSourceClient} replaced by a
 * controllable mock so a test can dictate exactly what the third-party source
 * offers (a normal payload, nothing, or a failure) without any real network
 * call.
 *
 * <p><strong>Triggering a run without waiting.</strong> Every scenario here
 * triggers reconciliation by calling
 * {@link GreekGodReconciliationScheduler#runDueReconciliation()} directly —
 * the on-demand seam {@link GreekGodReconciliationScheduler}'s Javadoc
 * documents for exactly this purpose. The real {@link org.springframework.scheduling.TaskScheduler}
 * bean is left untouched: it still registers its one immediate, kill-switch-off
 * run at context start-up (harmless — see below), but nothing in these tests
 * depends on it firing again, so cadence and wall-clock time never enter the
 * picture. Scenarios that need to observe cadence itself (a newly offered name
 * appearing within one period, a configured cadence, the start-up run) are
 * covered separately in {@link SchedulingAcceptanceTestBase} and its
 * subclasses, which substitute the scheduler.
 *
 * <p><strong>Controlling the kill switch.</strong> {@code greek-gods.sync.enabled}
 * defaults to {@code false} for the whole test suite
 * (see {@code src/test/resources/application.yaml}), so the context's one
 * automatic start-up run — which fires essentially immediately, before any
 * {@code @BeforeEach} here runs — finds it off and skips without ever calling
 * {@link #sourceClient}. Each test method here turns it on for its own
 * duration via the {@link ReconciliationKillSwitch#PROPERTY} system-property
 * seam {@link ReconciliationKillSwitch}'s Javadoc documents, and
 * {@link #restoreTheKillSwitchToTheSuiteDefault()} always clears it afterwards
 * — a JVM {@code System} property is global, not scoped to one Spring context,
 * so leaving it set would silently turn reconciliation on for every other test
 * in the same JVM.
 */
abstract class ReconciliationAcceptanceTestBase extends AcceptanceTestBase {

    @MockitoBean
    protected GreekGodSourceClient sourceClient;

    @Autowired
    protected GreekGodReconciliationScheduler scheduler;

    @BeforeEach
    void enableReconciliationForThisTest() {
        System.setProperty(ReconciliationKillSwitch.PROPERTY, "true");
    }

    @AfterEach
    void restoreTheKillSwitchToTheSuiteDefault() {
        System.clearProperty(ReconciliationKillSwitch.PROPERTY);
    }

    /** The third-party source currently offers exactly these names, in this order. */
    protected void givenTheSourceOffers(String... names) {
        when(sourceClient.fetchNames()).thenReturn(List.of(names));
    }

    /** The third-party source is unavailable: every call fails. */
    protected void givenTheSourceIsUnavailable() {
        when(sourceClient.fetchNames())
            .thenThrow(new ResourceAccessException(
                "simulated source unavailable", new IOException("connection refused")));
    }

    /**
     * Triggers one reconciliation run through the same entry point the real
     * scheduler calls, without waiting for it to fire.
     */
    protected void reconciliationRuns() {
        scheduler.runDueReconciliation();
    }
}
