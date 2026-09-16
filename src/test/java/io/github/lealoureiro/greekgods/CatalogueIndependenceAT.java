package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement:
 * <em>Serve the catalogue independently of the third-party service</em>.
 *
 * <p><strong>What this test proves, and what it does not.</strong> The scenario says
 * the request succeeds while the third-party name source is unavailable. Design D3
 * makes that true by construction rather than by resilience: the read path holds no
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
 * the request succeeds and returns the catalogue's own records, and neither
 * {@link GreekGodController} nor {@link GreekGodRepository} — the whole read path —
 * declares a dependency on an HTTP client. The second is the honest substitute for
 * "while the third party is unavailable": the third party's availability cannot
 * affect a request, because nothing the read path depends on is capable of reaching
 * it.
 *
 * <p><strong>Since {@code greek-gods-upstream-sync}</strong>, the running application
 * does hold a {@link RestClient} bean — {@code greek-gods-upstream-sync} adds
 * reconciliation, an out-of-band background process with its own client to the
 * third-party source (design: reached only between requests, never during one). The
 * invariant this test protects was never "the whole application holds no HTTP client
 * anywhere"; it is "the read path — {@link GreekGodController} and
 * {@link GreekGodRepository} specifically — holds no client with which a request
 * could reach the third party." Asserting on the read path's own constructors, rather
 * than on every bean in the context, is what keeps that distinction precise.
 *
 * <p>The complementary source-level evidence — that no HTTP client type and no
 * reference to the third-party host appears anywhere in {@code GreekGodController} or
 * {@code GreekGodRepository} — is recorded against design D3 and is what the
 * neighbouring scenario "Third-party service is never contacted during a request"
 * rests on.
 *
 * <p><strong>Strengthened for {@code greek-gods-upstream-sync}</strong>: the
 * reflection assertion above proves the read path holds no HTTP client, but that is a
 * static fact about the code, not a runtime observation. This class now also proves it
 * behaviourally: a real request-counting stub (a JDK {@link HttpServer}, no new
 * dependency) stands in for the third-party source — the application's
 * {@code greek-gods.sync.upstream.base-url} is pointed at it — and after a normal
 * catalogue read, the stub's request counter is asserted to be exactly zero. Both
 * assertions are kept; the reflection check still catches a client dependency being
 * added to the read path even before a test exercises it, and the counting stub now
 * additionally proves that no call reaches the wire during a request.
 */
class CatalogueIndependenceAT extends AcceptanceTestBase {

    private static final AtomicInteger THIRD_PARTY_REQUEST_COUNT = new AtomicInteger();
    private static final HttpServer THIRD_PARTY_REQUEST_COUNTING_STUB = startRequestCountingStub();

    private static HttpServer startRequestCountingStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/greek", exchange -> {
                THIRD_PARTY_REQUEST_COUNT.incrementAndGet();
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stopRequestCountingStub() {
        THIRD_PARTY_REQUEST_COUNTING_STUB.stop(0);
    }

    @DynamicPropertySource
    static void pointReconciliationAtTheRequestCountingStub(DynamicPropertyRegistry registry) {
        registry.add("greek-gods.sync.upstream.base-url", () ->
            "http://localhost:" + THIRD_PARTY_REQUEST_COUNTING_STUB.getAddress().getPort());
    }

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
    @DisplayName("The read path never calls the third-party source during a request")
    void theReadPathNeverCallsTheThirdPartySourceDuringARequest() {
        // WHEN an API consumer requests the Greek gods
        givenTheCatalogueHolds("Hestia");

        whenTheGreekGodsAreRequested();

        // THEN the request-counting stub standing in for the third-party
        // source received zero requests — proven behaviourally, not inferred
        // from the catalogue being unchanged (reconciliation is also disabled
        // for this context by the suite-wide default, so a background run
        // cannot explain a non-zero count either way).
        assertThat(THIRD_PARTY_REQUEST_COUNT.get())
            .as("serving a read must never call the third-party source")
            .isZero();
    }

    @Test
    @DisplayName("The read path holds no client with which to contact anyone")
    void theReadPathHoldsNoHttpClient() {
        assertThat(declaredConstructorParameterTypesOf(GreekGodController.class))
            .as("a RestClient/RestTemplate dependency here would give the controller a way out to the network")
            .noneMatch(CatalogueIndependenceAT::isHttpClientType);
        assertThat(declaredConstructorParameterTypesOf(GreekGodRepository.class))
            .as("a RestClient/RestTemplate dependency here would give the read repository a way out to the network")
            .noneMatch(CatalogueIndependenceAT::isHttpClientType);
    }

    private static List<Class<?>> declaredConstructorParameterTypesOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .flatMap(Arrays::stream)
            .toList();
    }

    private static boolean isHttpClientType(Class<?> type) {
        return RestClient.class.isAssignableFrom(type) || RestTemplate.class.isAssignableFrom(type);
    }
}
