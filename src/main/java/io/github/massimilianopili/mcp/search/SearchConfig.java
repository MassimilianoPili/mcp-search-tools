package io.github.massimilianopili.mcp.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SearchConfig {

    static final int CHUNK_SIZE = 6 * 1024; // 6KB

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
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9,it;q=0.8")
                .build();
    }
}
