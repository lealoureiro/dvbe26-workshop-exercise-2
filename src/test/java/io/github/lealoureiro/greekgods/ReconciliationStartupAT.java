package io.github.lealoureiro.greekgods;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Specification requirement:
 * <em>Reconcile repeatedly without operator intervention</em> — the start-up
 * run (design D7).
 */
@TestPropertySource(properties = "greek-gods.sync.enabled=true")
class ReconciliationStartupAT extends SchedulingAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: A run happens at start-up")
    void aRunHappensAtStartUp() {
        when(sourceClient.fetchNames()).thenReturn(List.of("Poseidon"));

        // WHEN the service starts with a catalogue that is missing a name the
        // third-party source offers: the first run's startTime is exactly
        // "now" by the injected clock — not offset by the cadence delay — so
        // no full cadence period has to elapse before it (design D7).
        ScheduledRun scheduledRun = capturedScheduledRun();
        assertThat(scheduledRun.startTime())
            .as("the first run must be scheduled for now, not one cadence period from now")
            .isEqualTo(FIXED_NOW);

        // Invoking it directly is what proves the name is taken up without
        // that period elapsing, deterministically and without depending on
        // the real scheduler's own thread timing.
        scheduledRun.runnable().run();

        // THEN that name is taken up without waiting for a full cadence period
        assertThat(whenTheGreekGodsAreRequested()).extracting(GreekGod::name).contains("Poseidon");
    }
}
