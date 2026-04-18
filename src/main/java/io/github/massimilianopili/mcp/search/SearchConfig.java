package io.github.massimilianopili.mcp.search;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class SearchConfig {

    static final int CHUNK_SIZE = 6 * 1024; // 6KB

    @Value("${mcp.websearch.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${mcp.websearch.response-timeout-seconds:3600}")
    private int responseTimeoutSeconds;

    @Value("${mcp.websearch.chain-timeout-seconds:3600}")
    private int chainTimeoutSeconds;

    @Value("${mcp.websearch.request-timeout-seconds:30}")
    private int requestTimeoutSeconds;

    @Value("${mcp.websearch.circuit-breaker.threshold:3}")
    private int circuitBreakerThreshold;

    @Value("${mcp.websearch.circuit-breaker.open-seconds:60}")
    private int circuitBreakerOpenSeconds;

    Duration getChainTimeout() {
        return Duration.ofSeconds(chainTimeoutSeconds);
    }

    Duration getRequestTimeout() {
        return Duration.ofSeconds(requestTimeoutSeconds);
    }

    int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    Duration getCircuitBreakerOpenDuration() {
        return Duration.ofSeconds(circuitBreakerOpenSeconds);
    }

    @Bean("searxngWebClient")
    public WebClient searxngWebClient(
            @Value("${mcp.websearch.url:http://searxng:8080}") String searxngUrl) {
        return WebClient.builder()
                .baseUrl(searxngUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("searchHttpClient")
    public WebClient searchHttpClient() {
        ConnectionProvider pool = ConnectionProvider.builder("web-fetch")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(10))
                .pendingAcquireTimeout(Duration.ofSeconds(45))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutSeconds * 1000)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9,it;q=0.8")
                .build();
    }
}
