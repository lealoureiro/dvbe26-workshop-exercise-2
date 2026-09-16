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
 * <em>Reconcile repeatedly without operator intervention</em> — a configured
 * cadence.
 */
@TestPropertySource(properties = {
    "greek-gods.sync.enabled=true",
    "greek-gods.sync.fixed-delay=PT10M"
})
class ReconciliationConfiguredCadenceAT extends SchedulingAcceptanceTestBase {

    @Test
    @DisplayName("Scenario: A configured cadence replaces the default")
    void aConfiguredCadenceReplacesTheDefault() {
        when(sourceClient.fetchNames()).thenReturn(List.of("Poseidon"));

        // WHEN an operator configures a cadence other than the default
        ScheduledRun scheduledRun = capturedScheduledRun();

        // AND that period elapses...
        // THEN runs follow the configured cadence rather than the default
        assertThat(scheduledRun.delay())
            .as("runs must follow the configured ten-minute cadence, not the one-hour default")
            .isEqualTo(Duration.ofMinutes(10))
            .isNotEqualTo(Duration.ofHours(1));

        // ...simulated by invoking the Runnable directly, so this test does
        // not wait on wall-clock time.
        scheduledRun.runnable().run();

        // AND a name newly offered by the third-party source appears in the catalogue
        assertThat(whenTheGreekGodsAreRequested()).extracting(GreekGod::name).contains("Poseidon");
    }
}
