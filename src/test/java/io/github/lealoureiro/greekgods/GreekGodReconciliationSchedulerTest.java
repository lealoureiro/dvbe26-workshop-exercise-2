package io.github.lealoureiro.greekgods;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GreekGodReconciliationScheduler}: registers on a configurable cadence
 * with an immediate first run (task 4.1/4.2, design D6/D7), and evaluates the
 * kill switch as the single decision at the entry point (task 4.3, design D9)
 * — never touching {@link GreekGodReconciliationService} when reconciliation is
 * off.
 *
 * <p>A context-free unit test: {@link TaskScheduler}, {@link ReconciliationKillSwitch}
 * and {@link GreekGodReconciliationService} are Mockito mocks, demonstrating
 * the seams task 4.5 asks for — a fake {@code TaskScheduler} and a fixed
 * {@link Clock} are exactly what the acceptance handoff will substitute to
 * control cadence without waiting.
 */
@ExtendWith(MockitoExtension.class)
class GreekGodReconciliationSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration FIXED_DELAY = Duration.ofMinutes(30);

    @Mock
    private GreekGodReconciliationService reconciliationService;

    @Mock
    private ReconciliationKillSwitch killSwitch;

    @Mock
    private TaskScheduler taskScheduler;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private GreekGodReconciliationScheduler scheduler() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        var properties = new GreekGodSyncProperties(FIXED_DELAY, null);
        return new GreekGodReconciliationScheduler(
            reconciliationService, killSwitch, taskScheduler, fixedClock, properties);
    }

    @Test
    void registersAnImmediateFirstRunAndTheConfiguredCadence() {
        scheduler().scheduleReconciliation();

        // startTime is "now" by the injected clock (design D7: no waiting for a
        // full cadence period before the first run) and the delay is the
        // configured one (design D6), not a hardcoded default.
        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(FIXED_NOW), eq(FIXED_DELAY));
    }

    @Test
    void theEntryPointRunsReconciliationWhenTheKillSwitchIsOn() {
        when(killSwitch.reconciliationEnabled()).thenReturn(true);

        scheduler().runDueReconciliation();

        verify(reconciliationService).reconcile();
    }

    @Test
    void theEntryPointNeverCallsReconciliationWhenTheKillSwitchIsOff() {
        when(killSwitch.reconciliationEnabled()).thenReturn(false);

        scheduler().runDueReconciliation();

        // Design D9: when stopped, no call is made at all — not to the source,
        // and not even into the reconciliation service that would call it.
        verify(reconciliationService, never()).reconcile();
    }

    @Test
    void theRunnableRegisteredWithTheSchedulerIsTheSameEntryPoint() {
        GreekGodReconciliationScheduler scheduler = scheduler();
        when(killSwitch.reconciliationEnabled()).thenReturn(true);

        scheduler.scheduleReconciliation();

        verify(taskScheduler).scheduleWithFixedDelay(runnableCaptor.capture(), any(Instant.class), any(Duration.class));
        // Invoking the captured Runnable is the same seam the acceptance handoff
        // uses to "trigger a run on demand" without waiting for the real scheduler.
        runnableCaptor.getValue().run();

        verify(reconciliationService).reconcile();
    }
}
