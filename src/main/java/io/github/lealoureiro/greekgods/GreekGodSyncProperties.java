package io.github.lealoureiro.greekgods;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for reconciliation's cadence and its outbound client to the
 * third-party name source (design D6).
 *
 * <p>Bound eagerly at start-up via {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * in {@link GreekGodSyncConfiguration}: an absent value is fine (every field has
 * a sensible default, applied in the compact constructors below), so binding
 * cannot fail merely because an operator left something unset.
 *
 * <p>Deliberately excludes the kill switch. See {@link ReconciliationKillSwitch}
 * for why that one setting is read lazily, per run, instead of bound here once
 * at start-up.
 *
 * @param fixedDelay the cadence between the end of one reconciliation run and
 *                    the start of the next, defaulting to one hour (design D6)
 * @param upstream    the third-party source's location and timeouts
 */
@ConfigurationProperties(prefix = "greek-gods.sync")
record GreekGodSyncProperties(Duration fixedDelay, Upstream upstream) {

    GreekGodSyncProperties {
        if (fixedDelay == null) {
            fixedDelay = Duration.ofHours(1);
        }
        if (upstream == null) {
            upstream = new Upstream(null, null, null);
        }
    }

    /**
     * @param baseUrl        the third-party source's base URL, per
     *                       {@code oas/my-json-server-oas.yaml}
     * @param connectTimeout bounds how long establishing the connection may
     *                       take, so a hanging source cannot stall a run
     * @param readTimeout    bounds how long waiting for the response body may
     *                       take, so a slow source cannot stall a run
     */
    record Upstream(String baseUrl, Duration connectTimeout, Duration readTimeout) {

        Upstream {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://my-json-server.typicode.com/jabrena/latency-problems";
            }
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(2);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(5);
            }
        }
    }
}
