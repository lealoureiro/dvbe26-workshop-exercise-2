package io.github.lealoureiro.greekgods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The permanent operational kill switch for reconciliation (design D9): a
 * single typed decision, made once per scheduled entry point
 * ({@link GreekGodReconciliationScheduler#runDueReconciliation()}), about
 * whether this run may reach the third-party source at all.
 *
 * <p>Deliberately <strong>not</strong> bound through
 * {@code @ConfigurationProperties}. Binding it eagerly would make a value
 * Spring cannot convert to {@code Boolean} an application start-up failure —
 * exactly the wrong failure mode for a safety switch. Reading the raw setting
 * lazily, on every call, and containing a bad value to "this run is off"
 * instead, is what makes the fail-safe default actually safe: a configuration
 * mistake can degrade freshness, never cause an unintended call outward.
 *
 * <p>This type makes exactly one decision and holds no reconciliation logic of
 * its own (design D9): flag checks belong here and only here, never scattered
 * through {@link GreekGodReconciliationService}.
 *
 * <p><strong>Test seam</strong> (for the acceptance handoff): set
 * {@value #PROPERTY} to {@code false} to stop reconciliation, back to
 * {@code true} (or unset) to resume it, or to a non-boolean value such as
 * {@code "maybe"} to simulate a control that cannot be read — each is
 * re-evaluated on the next call, with no restart needed. Substituting an
 * {@link Environment} whose {@code getProperty} itself throws simulates the
 * same "unreadable" outcome at a lower level.
 */
@Component
class ReconciliationKillSwitch {

    static final String PROPERTY = "greek-gods.sync.enabled";

    private static final Logger log = LoggerFactory.getLogger(ReconciliationKillSwitch.class);

    private final Environment environment;

    ReconciliationKillSwitch(Environment environment) {
        this.environment = environment;
    }

    /**
     * @return {@code true} unless reconciliation was explicitly turned off, or
     *         unless {@value #PROPERTY} could not be read or parsed as a
     *         boolean — either of which is treated as off (design D9,
     *         fail-safe default is on, fail-safe failure mode is off)
     */
    boolean reconciliationEnabled() {
        try {
            String raw = environment.getProperty(PROPERTY);
            return raw == null || parseStrictBoolean(raw);
        } catch (RuntimeException ex) {
            log.warn("event=reconciliation.kill_switch_unreadable property={} treating_as=disabled",
                PROPERTY, ex);
            return false;
        }
    }

    private static boolean parseStrictBoolean(String raw) {
        String normalized = raw.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new IllegalArgumentException(
            "Property '" + PROPERTY + "' is not a recognisable boolean: '" + raw + "'");
    }
}
