package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Serve the catalogue independently of the third-party service</em>.
 *
 * <p><strong>What this test proves, and what it does not.</strong> The scenario says
 * the request succeeds while the third-party name source is unavailable. Design D3
 * makes that true by construction rather than by resilience: the service holds no
 * client for that source, so there is no call to fail and no timeout to absorb.
 *
 * <p>That construction is what makes the scenario awkward to test in the obvious way.
 * Stubbing the third-party host with WireMock and asserting zero requests would prove
 * nothing here: the application is not configured with that stub's address and never
 * was, so the stub would sit unused whether or not the read path were independent.
 * The assertion would pass for a reason unrelated to the behaviour, which is exactly
 * the kind of test that looks like coverage and is not. The same goes for making the
 * real host unreachable — the run would be identical either way.
 *
 * <p>So this test asserts the two things that are genuinely observable end-to-end:
 * the request succeeds and returns the catalogue's own records, and the running
 * application holds no HTTP client at all with which it could contact anyone. The
 * second is the honest substitute for "while the third party is unavailable": the
 * third party's availability cannot affect a request, because nothing in the running
 * service is capable of reaching it.
 *
 * <p>The complementary source-level evidence — that no HTTP client type and no
 * reference to the third-party host appears anywhere in {@code src/main} — is
 * recorded against design D3 and is what the neighbouring scenario "Third-party
 * service is never contacted during a request" rests on.
 */
class CatalogueIndependenceAT extends AcceptanceTestBase {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Scenario: Third-party service is unavailable")
    void thirdPartyServiceIsUnavailable() {
        // WHEN an API consumer requests the Greek gods
        // while the third-party name source is unavailable
        long gaia = givenTheCatalogueHolds("Gaia");
        long uranus = givenTheCatalogueHolds("Uranus");

        // THEN the request succeeds
        var records = whenTheGreekGodsAreRequested();

        // AND the response contains the records the catalogue holds
        assertThat(records).containsExactly(
            new GreekGod(gaia, "Gaia"),
            new GreekGod(uranus, "Uranus"));
    }

    @Test
    @DisplayName("The running service holds no client with which to contact anyone")
    void theRunningServiceHoldsNoHttpClient() {
        assertThat(applicationContext.getBeanNamesForType(RestTemplate.class))
            .as("a RestTemplate bean would give the read path a way out to the network")
            .isEmpty();
        assertThat(applicationContext.getBeanNamesForType(RestClient.class))
            .as("a RestClient bean would give the read path a way out to the network")
            .isEmpty();
    }
}
