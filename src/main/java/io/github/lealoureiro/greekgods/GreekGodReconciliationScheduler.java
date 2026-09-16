package io.github.lealoureiro.greekgods;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Schedules reconciliation (design D6: configurable cadence, defaulting to one
 * hour; design D7: in-process, plus one run at start-up) and evaluates the
 * kill switch as a single decision at the entry point (design D9).
 *
 * <p>Deliberately built on an injected {@link TaskScheduler} and {@link Clock}
 * rather than {@code @Scheduled}: both are ordinary constructor-injected
 * collaborators, so both can be substituted, which is what makes the seams
 * below possible without touching production wiring.
 *
 * <p><strong>Seams for the acceptance handoff</strong> (task 4.5; design
 * D7/D9 testability) — none of the following requires waiting on wall-clock
 * time:
 * <ul>
 *   <li><strong>Trigger a run on demand</strong> — call
 *       {@link #runDueReconciliation()} directly. It does not depend on the
 *       scheduler having fired; it is the same method the scheduler calls.</li>
 *   <li><strong>Advance or control cadence</strong> — substitute the
 *       {@link TaskScheduler} bean (registered under the name
 *       {@value #SCHEDULER_BEAN_NAME}) with a test double that captures the
 *       {@code Runnable} passed to {@code scheduleWithFixedDelay} and invokes
 *       it whenever the test chooses, instead of a real thread waiting a real
 *       {@link Duration}. The {@link Clock} bean is likewise substitutable, so
 *       a test can assert or control the {@code startTime} passed to the
 *       scheduler without depending on the system clock.</li>
 *   <li><strong>Flip the kill switch</strong> — set
 *       {@code greek-gods.sync.enabled} to {@code false} or {@code true}
 *       between calls to {@link #runDueReconciliation()};
 *       {@link ReconciliationKillSwitch} re-reads it every time, so no restart
 *       is needed.</li>
 *   <li><strong>Simulate an unreadable control</strong> — set
 *       {@code greek-gods.sync.enabled} to a non-boolean value (for example
 *       {@code "maybe"}), or substitute an {@code Environment} whose
 *       {@code getProperty} itself throws; {@link ReconciliationKillSwitch}
 *       treats either as "off" (design D9, fail-safe).</li>
 * </ul>
 */
@Component
class GreekGodReconciliationScheduler {

    static final String SCHEDULER_BEAN_NAME = "greekGodSyncTaskScheduler";

    private static final Logger log = LoggerFactory.getLogger(GreekGodReconciliationScheduler.class);

    private final GreekGodReconciliationService reconciliationService;
    private final ReconciliationKillSwitch killSwitch;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final Duration fixedDelay;

    GreekGodReconciliationScheduler(
            GreekGodReconciliationService reconciliationService,
            ReconciliationKillSwitch killSwitch,
            @Qualifier(SCHEDULER_BEAN_NAME) TaskScheduler taskScheduler,
            Clock clock,
            GreekGodSyncProperties properties) {
        this.reconciliationService = reconciliationService;
        this.killSwitch = killSwitch;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.fixedDelay = properties.fixedDelay();
    }

    /**
     * Registers the recurring schedule (design D6) with an immediate first
     * execution (design D7): {@code startTime} is "now" by the injected
     * clock, so a cold catalogue does not wait a full cadence period before
     * its first name is taken up.
     */
    @PostConstruct
    void scheduleReconciliation() {
        taskScheduler.scheduleWithFixedDelay(this::runDueReconciliation, Instant.now(clock), fixedDelay);
    }

    /**
     * The scheduled entry point, and the only place the kill switch is
     * evaluated (design D9) — {@link GreekGodReconciliationService} holds no
     * toggle check of its own. When reconciliation is off, this method returns
     * without calling {@link GreekGodReconciliationService#reconcile()} at
     * all, so no call ever reaches the third party this run.
     */
    void runDueReconciliation() {
        if (!killSwitch.reconciliationEnabled()) {
            log.info("event=reconciliation.skipped reason=kill_switch_disabled");
            return;
        }
        reconciliationService.reconcile();
    }
}
