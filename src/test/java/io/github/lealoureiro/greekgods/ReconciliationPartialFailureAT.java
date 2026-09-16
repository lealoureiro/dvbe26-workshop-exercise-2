package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Keep names taken up before a run fails</em>.
 *
 * <p>The failing name is deterministic and real rather than simulated: one name
 * offered is too long for the {@code name} column (design D8 is exercised against
 * a genuine {@code DataIntegrityViolationException} from PostgreSQL, matching the
 * technique {@link GreekGodReconciliationServiceIT} already established at the
 * integration level).
 */
class ReconciliationPartialFailureAT extends ReconciliationAcceptanceTestBase {

    private static final String NAME_TOO_LONG_FOR_THE_COLUMN = "X".repeat(101);

    @Test
    @DisplayName("Scenario: A run fails partway through")
    void aRunFailsPartwayThrough() {
        // WHEN the third-party source offers "Zeus", "Hera" and "Poseidon", and
        // reconciliation fails after taking up "Zeus" and "Hera"
        // (the third name here plays the role of "Poseidon": it is offered, but
        // fails to be taken up, so the run does not complete cleanly)
        givenTheSourceOffers("Zeus", "Hera", NAME_TOO_LONG_FOR_THE_COLUMN);

        reconciliationRuns();

        // THEN a request for the Greek gods contains records for "Zeus" and "Hera"
        // AND they hold the identifiers they were published with (there is only
        // one run, so "the identifiers they were published with" are whichever
        // this run minted — verified stable across a later run below)
        var records = whenTheGreekGodsAreRequested();
        assertThat(records).extracting(GreekGod::name).contains("Zeus", "Hera");
        assertThat(records).extracting(GreekGod::name).doesNotContain(NAME_TOO_LONG_FOR_THE_COLUMN);
    }

    @Test
    @DisplayName("Scenario: A later run completes what a failed run left")
    void aLaterRunCompletesWhatAFailedRunLeft() {
        // WHEN a run has failed after taking up only some of the offered names
        givenTheSourceOffers("Zeus", NAME_TOO_LONG_FOR_THE_COLUMN, "Hera");
        reconciliationRuns();

        long zeusIdFromTheFailedRun = identifierOf("Zeus");
        long heraIdFromTheFailedRun = identifierOf("Hera");

        // AND a later run completes (the source no longer offers the bad name,
        // and now also offers "Poseidon")
        givenTheSourceOffers("Zeus", "Hera", "Poseidon");
        reconciliationRuns();

        var records = whenTheGreekGodsAreRequested();

        // THEN a request for the Greek gods contains a record for every name the
        // third-party source offers
        assertThat(records).extracting(GreekGod::name).contains("Zeus", "Hera", "Poseidon");

        // AND the names taken up by the earlier run keep their identifiers
        assertThat(identifierOf("Zeus")).isEqualTo(zeusIdFromTheFailedRun);
        assertThat(identifierOf("Hera")).isEqualTo(heraIdFromTheFailedRun);
    }

    private long identifierOf(String name) {
        return whenTheGreekGodsAreRequested().stream()
            .filter(god -> god.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a record for " + name))
            .id();
    }
}
