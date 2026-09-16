package io.github.lealoureiro.greekgods;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the current name list from the third-party source, per
 * {@code oas/my-json-server-oas.yaml}: {@code GET /greek} on
 * {@code https://my-json-server.typicode.com/jabrena/latency-problems},
 * returning a JSON array of plain strings.
 *
 * <p>The {@link RestClient} injected here is built once, in
 * {@link GreekGodSyncConfiguration}, with connect and read timeouts (design:
 * task 2.2) so a slow or hanging source cannot stall a reconciliation run
 * indefinitely.
 *
 * <p>Failures are not caught here. A {@link RestClientException} — a 4xx/5xx
 * status such as the {@code 500} and {@code 504} the contract advertises, a
 * connection failure, or a timeout (surfaced as
 * {@link org.springframework.web.client.ResourceAccessException}, a
 * {@code RestClientException} subtype) — propagates to
 * {@link GreekGodReconciliationService}, which decides what a failed run means
 * (design D5) and records it. This type's only job is "ask the source, and say
 * what it said."
 */
@Component
class GreekGodSourceClient {

    private final RestClient restClient;

    GreekGodSourceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return the names the third-party source currently offers, exactly as
     *         received — untrimmed, and including any empty or blank entries;
     *         an empty list when the source offers nothing. Trimming and
     *         skipping blanks is {@link GreekGodReconciliationService}'s
     *         concern (design D4), not this client's.
     * @throws RestClientException when the source responds with an error
     *                              status, cannot be reached, or does not
     *                              respond within the configured timeouts
     */
    List<String> fetchNames() {
        List<String> names = restClient.get()
            .uri("/greek")
            .retrieve()
            .body(new ParameterizedTypeReference<List<String>>() { });
        return names == null ? List.of() : names;
    }
}
