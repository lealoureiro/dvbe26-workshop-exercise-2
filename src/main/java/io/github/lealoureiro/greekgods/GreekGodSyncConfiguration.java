package io.github.lealoureiro.greekgods;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

/**
 * Wiring for reconciliation: the clock and scheduler
 * {@link GreekGodReconciliationScheduler} runs on, and the {@link RestClient}
 * {@link GreekGodSourceClient} reads the third-party source through.
 *
 * <p>The {@link RestClient} is built by hand, with
 * {@link SimpleClientHttpRequestFactory} carrying the connect and read
 * timeouts (task 2.2), rather than via Spring Boot's auto-configured
 * {@code RestClient.Builder} — that auto-configuration lives in a module this
 * project does not currently depend on, and adding a dependency is outside
 * this change's scope. {@link SimpleClientHttpRequestFactory} ships with
 * {@code spring-web}, already transitive through
 * {@code spring-boot-starter-web}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GreekGodSyncProperties.class)
class GreekGodSyncConfiguration {

    /**
     * The time source reconciliation's scheduling runs on (task 4.5): a real
     * system clock in production, substitutable with a fixed or mutable one in
     * tests so cadence behaviour does not depend on wall-clock time.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * A dedicated, single-thread scheduler for reconciliation (task 4.5): kept
     * separate from any other {@link TaskScheduler} the application may use,
     * and registered under a fixed name so a test can replace exactly this
     * bean with a controllable double without disturbing anything else.
     */
    @Bean(GreekGodReconciliationScheduler.SCHEDULER_BEAN_NAME)
    TaskScheduler greekGodSyncTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("greek-god-sync-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * The outbound client to the third-party name source, bounded by connect
     * and read timeouts (task 2.2) so a slow or hanging source cannot stall a
     * reconciliation run indefinitely.
     */
    @Bean
    RestClient greekGodSourceRestClient(GreekGodSyncProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.upstream().connectTimeout());
        requestFactory.setReadTimeout(properties.upstream().readTimeout());

        return RestClient.builder()
            .baseUrl(properties.upstream().baseUrl())
            .requestFactory(requestFactory)
            .build();
    }
}
