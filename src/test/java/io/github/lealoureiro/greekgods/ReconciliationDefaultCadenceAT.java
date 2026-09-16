package io.github.lealoureiro.greekgods;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Specification requirement:
 * <em>Reconcile repeatedly without operator intervention</em> — the default
 * cadence.
 */
@TestPropertySource(properties = "greek-gods.sync.enabled=true")
class ReconciliationDefaultCadenceAT extends SchedulingAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: A newly offered name appears within one cadence period")
    void aNewlyOfferedNameAppearsWithinOneCadencePeriod() {
        when(sourceClient.fetchNames()).thenReturn(List.of("Poseidon"));

        ScheduledRun scheduledRun = capturedScheduledRun();

        // The run follows the default one-hour cadence (design D6) when none
        // is configured.
        assertThat(scheduledRun.delay()).isEqualTo(Duration.ofHours(1));

        // WHEN the third-party source begins offering "Poseidon" and one
        // cadence period elapses — simulated by invoking the very Runnable the
        // real scheduler would invoke once that period lapses, so this test
        // does not wait on wall-clock time.
        scheduledRun.runnable().run();

        // THEN a request for the Greek gods contains a record for "Poseidon"
        assertThat(whenTheGreekGodsAreRequested()).extracting(GreekGod::name).contains("Poseidon");
    }
}
