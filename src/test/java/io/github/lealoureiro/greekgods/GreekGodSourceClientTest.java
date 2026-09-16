package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link GreekGodSourceClient} against a stubbed source (task 2.4): a normal
 * payload, an empty list, a {@code 500}, a {@code 504}, and — needing a real
 * socket rather than a stub — a source that never responds in time.
 *
 * <p>A context-free unit test: no Spring context, no container. The
 * {@link MockRestServiceServer} tests bind directly to a {@link RestClient.Builder},
 * exactly as {@link org.springframework.test.web.client.MockRestServiceServer#bindTo(RestClient.Builder)}
 * is designed for; the timeout test starts a real {@link HttpServer} because a
 * stub server returns instantly and cannot exercise a read timeout.
 */
class GreekGodSourceClientTest {

    private static final String BASE_URL = "http://upstream.test";

    @Test
    void returnsEveryNameFromANormalPayload() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/greek"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                ["Zeus", "Hera", "Poseidon"]
                """, MediaType.APPLICATION_JSON));

        GreekGodSourceClient client = new GreekGodSourceClient(builder.build());

        assertThat(client.fetchNames()).containsExactly("Zeus", "Hera", "Poseidon");
        server.verify();
    }

    @Test
    void returnsAnEmptyListWhenTheSourceOffersNothing() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/greek"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        GreekGodSourceClient client = new GreekGodSourceClient(builder.build());

        assertThat(client.fetchNames()).isEmpty();
        server.verify();
    }

    @Test
    void propagatesA500AsARestClientException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/greek"))
            .andRespond(withServerError());

        GreekGodSourceClient client = new GreekGodSourceClient(builder.build());

        assertThatThrownBy(client::fetchNames).isInstanceOf(RestClientException.class);
    }

    @Test
    void propagatesA504AsARestClientException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/greek"))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        GreekGodSourceClient client = new GreekGodSourceClient(builder.build());

        assertThatThrownBy(client::fetchNames).isInstanceOf(RestClientException.class);
    }

    @Test
    void aSourceThatDoesNotRespondInTimeIsAbandoned() throws IOException {
        // MockRestServiceServer responds instantly and cannot exercise a real
        // read timeout, so this one test uses a real socket (per task 2.4).
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/greek", exchange -> {
            try {
                // Comfortably longer than the 200ms read timeout configured below.
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(2));
            requestFactory.setReadTimeout(Duration.ofMillis(200));

            RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + httpServer.getAddress().getPort())
                .requestFactory(requestFactory)
                .build();
            GreekGodSourceClient client = new GreekGodSourceClient(restClient);

            assertThatThrownBy(client::fetchNames)
                .as("a hanging source must not stall a reconciliation run indefinitely")
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            httpServer.stop(0);
        }
    }
}
