package io.github.lealoureiro.greekgods;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.verify;

/**
 * Shared setup for the scenarios that are about the scheduling mechanism
 * itself — cadence and the start-up run — rather than about what one
 * reconciliation run does (design D6/D7; task 4.5's controllable-clock and
 * controllable-scheduler seams).
 *
 * <p>Unlike {@link ReconciliationAcceptanceTestBase}, which leaves the real
 * {@link TaskScheduler} bean in place and triggers a run on demand, these
 * scenarios need to observe <em>how</em> a run gets scheduled: the exact
 * {@link Duration} passed to {@code scheduleWithFixedDelay} (default or
 * configured), and that the first run's {@code startTime} is "now" rather than
 * one cadence period away. So here the {@link TaskScheduler} bean named
 * {@link GreekGodReconciliationScheduler#SCHEDULER_BEAN_NAME} is replaced with
 * a Mockito mock that records the call {@link GreekGodReconciliationScheduler}
 * makes at start-up (design D7's {@code @PostConstruct}) without ever really
 * scheduling anything — a captured {@link Runnable} is then invoked directly
 * to simulate "a cadence period elapses" without waiting on wall-clock time.
 *
 * <p>The {@link Clock} bean is replaced with a fixed one via {@code @TestBean}
 * (a real instance, not a mock — {@link GreekGodReconciliationScheduler} calls
 * {@link Instant#now(Clock)} on it), so the captured {@code startTime} is a
 * known, deterministic value rather than an approximation of "now".
 *
 * <p>{@code @PostConstruct} registers the scheduled run exactly once, when the
 * bean is created — which happens once per cached Spring context, not once
 * per test method. Subclasses sharing an identical configuration (as
 * {@code ReconciliationDefaultCadenceAT} and {@code ReconciliationStartupAT}
 * do) therefore share that one context and that one recorded invocation.
 * {@code @MockitoBean}'s default behaviour is to reset every mock's recorded
 * interactions after each test method, which would erase that one-off
 * invocation before a second test method ever gets to look at it — a
 * context-sharing hazard, not a behaviour of the production code. Declaring
 * {@code reset = MockReset.NONE} on {@link #taskScheduler} keeps the
 * registration call observable for the whole context's lifetime; the mock is
 * used only to capture and inspect that single call, never restubbed, so
 * nothing here needs a reset between methods.
 */
abstract class SchedulingAcceptanceTestBase extends AcceptanceTestBase {

    protected static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @MockitoBean(name = GreekGodReconciliationScheduler.SCHEDULER_BEAN_NAME, reset = MockReset.NONE)
    protected TaskScheduler taskScheduler;

    @MockitoBean
    protected GreekGodSourceClient sourceClient;

    @TestBean
    protected Clock clock;

    static Clock clock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    /**
     * The {@link Runnable} and {@link Duration} the scheduler registered at
     * start-up (design D7's immediate first run, design D6's cadence),
     * captured from the mocked {@link TaskScheduler} — {@code startTime} is
     * asserted separately by each test since its expected value differs.
     */
    protected record ScheduledRun(Runnable runnable, Instant startTime, Duration delay) {
    }

    protected ScheduledRun capturedScheduledRun() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> startTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> delayCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(taskScheduler).scheduleWithFixedDelay(
            runnableCaptor.capture(), startTimeCaptor.capture(), delayCaptor.capture());
        return new ScheduledRun(runnableCaptor.getValue(), startTimeCaptor.getValue(), delayCaptor.getValue());
    }
}
