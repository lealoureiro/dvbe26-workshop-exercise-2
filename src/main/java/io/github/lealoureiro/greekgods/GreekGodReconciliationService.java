package io.github.lealoureiro.greekgods;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * One reconciliation run: bring every name the third-party source currently
 * offers, and the catalogue has not yet seen, into the catalogue — one name at
 * a time (design D1).
 *
 * <p>This type holds no kill-switch check. Whether a run happens at all is
 * decided once, at the scheduled entry point
 * ({@link GreekGodReconciliationScheduler#runDueReconciliation()}), by
 * {@link ReconciliationKillSwitch} (design D9); by the time {@link #reconcile()}
 * is called, the answer was already "yes."
 *
 * <p>{@link #reconcile()} never throws. A failed fetch (design: the
 * third-party source is unavailable, times out, or fails) leaves the catalogue
 * exactly as it was before the run — no name is taken up, because none was
 * even seen. A failure taking up one particular name does not stop the run
 * (design D8): the remaining offered names are still attempted, so a run that
 * fails partway keeps everything it already added, and a later run completes
 * the rest. Either way the outcome is recorded (design D5) so a stale
 * catalogue is observable rather than silently presenting as healthy.
 */
@Service
class GreekGodReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(GreekGodReconciliationService.class);

    private final GreekGodSourceClient sourceClient;
    private final GreekGodCatalogueWriter catalogueWriter;

    GreekGodReconciliationService(GreekGodSourceClient sourceClient, GreekGodCatalogueWriter catalogueWriter) {
        this.sourceClient = sourceClient;
        this.catalogueWriter = catalogueWriter;
    }

    void reconcile() {
        List<String> offeredNames;
        try {
            offeredNames = sourceClient.fetchNames();
        } catch (RestClientException ex) {
            // The catalogue is left exactly as it was: no name was even seen,
            // so nothing here could have taken one up (design: source unavailable).
            log.error("event=reconciliation.run_failed reason=source_unavailable", ex);
            return;
        }

        int takenUp = 0;
        int alreadyHeld = 0;
        int skippedBlank = 0;
        int failed = 0;

        for (String offeredName : offeredNames) {
            // Untrusted input (design D4): trim before matching, and skip a
            // name that is empty or whitespace-only without ending the run.
            String candidate = offeredName == null ? "" : offeredName.trim();
            if (candidate.isEmpty()) {
                skippedBlank++;
                continue;
            }

            try {
                if (catalogueWriter.takeUpIfUnseen(candidate)) {
                    takenUp++;
                } else {
                    alreadyHeld++;
                }
            } catch (DataAccessException ex) {
                // Per-name isolation (design D8): this name's failure does not
                // stop the run, and does not undo a name already taken up.
                failed++;
                log.error("event=reconciliation.name_failed name={}", candidate, ex);
            }
        }

        // The outcome of every run is recorded (design D5), success or not —
        // this is the only signal that the catalogue is going stale silently.
        log.info(
            "event=reconciliation.run_completed offered={} takenUp={} alreadyHeld={} skippedBlank={} failed={}",
            offeredNames.size(), takenUp, alreadyHeld, skippedBlank, failed);
    }
}
