package io.github.massimilianopili.mcp.search;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.mcp.research.extract.ApiExtractors;
import io.github.massimilianopili.mcp.search.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;

    private static final Map<String, String> ACADEMIC_API_QUERY_PARAMS = Map.of(
            "api.semanticscholar.org", "query",
            "api.crossref.org", "query",
            "export.arxiv.org", "search_query",
            "api.openalex.org", "search"
    );

    private final WebClient searxngClient;
    private final WebClient httpClient;

    @Autowired(required = false)
    private ChunkStore chunkStore;

    @Autowired(required = false)
    private HeadlessBrowser headlessBrowser;

    @Autowired(required = false)
    private FetchCache fetchCache;

    @Autowired(required = false)
    private IngestQueue ingestQueue;

    @Autowired(required = false)
    private SemanticLookup semanticLookup;

    public WebSearchTools(
            @Qualifier("searxngWebClient") WebClient searxngClient,
            @Qualifier("searchHttpClient") WebClient httpClient) {
        this.searxngClient = searxngClient;
        this.httpClient = httpClient;
    }

    @ReactiveTool(
            name = "web_search",
            description = "Performs a web search via self-hosted SearXNG (meta-engine: Google, Bing, DuckDuckGo, Brave, Wikipedia). " +
                          "Category 'science': aggregates Semantic Scholar, CrossRef, arXiv, OpenAlex, PubMed, Google Scholar, Springer. " +
                          "Returns structured JSON results with title, URL, snippet and metadata. " +
                          "Available categories: general, science, it, news. " +
                          "More resilient than built-in WebSearch: failures are isolated per individual call."
    )
    public Mono<String> webSearch(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Maximum number of results (default 10, max 30)") int maxResults,
            @ToolParam(description = "Comma-separated categories, e.g.: 'general,science'. Default: 'general'") String categories,
            @ToolParam(description = "Result language, e.g.: 'it', 'en', 'auto'. Default: 'auto'") String language) {

        int limit = maxResults > 0 ? Math.min(maxResults, 30) : 10;
        String cats = (categories != null && !categories.isBlank()) ? categories : "general";
        String lang = (language != null && !language.isBlank()) ? language : "auto";

        String korePrefix = "";
        if (semanticLookup != null && semanticLookup.isAvailable()) {
            try {
                String koreResults = semanticLookup.search(query, 3);
                if (koreResults != null) {
                    korePrefix = "--- From KORE (cached knowledge) ---\n" + koreResults + "\n--- Web results ---\n";
                }
            } catch (Exception e) {
                log.debug("Semantic lookup skipped for '{}': {}", query, e.getMessage());
            }
        }
        final String prefix = korePrefix;

        return searxngClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("categories", cats)
                        .queryParam("language", lang)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(FETCH_TIMEOUT)
                .map(result -> prefix.isEmpty() ? result : prefix + result)
                .onErrorResume(e -> Mono.just(
                        (prefix.isEmpty() ? "" : prefix) +
                        "{\"error\": \"Web search for '" + query + "': " + e.getMessage() + "\"}"));
    }

    @ReactiveTool(
            name = "web_fetch",
            description = "Downloads the content of a URL and returns the body as text. " +
                          "4-level resilience: (1) browser-like headers, (2) retry x3 with backoff on 429/5xx, " +
                          "(3) SearXNG science search fallback for rate-limited academic APIs, " +
                          "(4) headless browser fallback for sites with Cloudflare/bot protection. " +
                          "For large responses (>6KB), content is split into chunks (TTL 10min). " +
                          "Use web_fetch_chunk(fetch_id, index) for subsequent chunks. " +
                          "Parameter 'extract': 'semantic_scholar', 'arxiv', 'openalex' for smart extraction. " +
                          "Limit: 2MB."
    )
    public Mono<String> webFetch(
            @ToolParam(description = "Full URL to download") String url,
            @ToolParam(description = "Smart extraction: 'semantic_scholar', 'arxiv', 'openalex', or null/empty for raw",
                       required = false) String extract) {

        if (extract != null && !extract.isBlank() && fetchCache != null) {
            try {
                String cached = fetchCache.get(url).block();
                if (cached != null) {
                    log.info("web_fetch cache hit for '{}'", url);
                    return Mono.just(cached);
                }
            } catch (Exception e) {
                log.debug("Cache lookup failed for '{}': {}", url, e.getMessage());
            }
        }

        return httpClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(FETCH_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .jitter(0.3)
                        .filter(WebSearchTools::isRetryable)
                        .doBeforeRetry(s -> log.warn("web_fetch retry #{} for '{}': {}",
                                s.totalRetries() + 1, url, s.failure().getMessage())))
                .flatMap(body -> processResponse(body, url, extract))
                .onErrorResume(e -> {
                    log.error("web_fetch failed for '{}': {}", url, e.getMessage());
                    String scienceQuery = extractAcademicQuery(url);
                    if (scienceQuery != null && is429(e)) {
                        log.info("web_fetch fallback SearXNG science for '{}'", scienceQuery);
                        return webSearch(scienceQuery, 10, "science", "en")
                                .flatMap(body -> processResponse(body, url, null));
                    }
                    if (shouldFallbackToBrowser(e)) {
                        return fetchWithBrowser(url, extract);
                    }
                    return Mono.just("{\"error\": \"Fetch failed for '" + url + "': " + e.getMessage() + "\"}");
                });
    }

    @ReactiveTool(
            name = "web_fetch_chunk",
            description = "Retrieves a specific chunk from a previous fetch (web_fetch with response >6KB). " +
                          "Use the fetch_id and chunk_index returned by web_fetch. Each chunk is ~6KB. " +
                          "Chunks expire after 10 minutes."
    )
    public Mono<String> webFetchChunk(
            @ToolParam(description = "Fetch ID (UUID returned by web_fetch)") String fetchId,
            @ToolParam(description = "Chunk index (0-based)") int chunkIndex) {

        if (chunkStore == null) {
            return Mono.just("{\"error\": \"Chunking not available: no ChunkStore configured\"}");
        }
        return chunkStore.getChunk(fetchId, chunkIndex);
    }

    // --- Fallback helpers ---

    private static boolean isRetryable(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return t instanceof java.util.concurrent.TimeoutException
                || t instanceof java.net.ConnectException;
    }

    private static boolean is429(Throwable t) {
        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
        return cause instanceof WebClientResponseException wcre
                && wcre.getStatusCode().value() == 429;
    }

    private static boolean shouldFallbackToBrowser(Throwable t) {
        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
        if (cause instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || status == 403;
        }
        return false;
    }

    private static String extractAcademicQuery(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String queryParamName = ACADEMIC_API_QUERY_PARAMS.entrySet().stream()
                    .filter(e -> host != null && host.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            if (queryParamName == null || uri.getQuery() == null) return null;
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals(queryParamName)) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Mono<String> fetchWithBrowser(String url, String extract) {
        if (headlessBrowser == null || !headlessBrowser.isAvailable()) {
            return Mono.just("{\"error\": \"Fetch failed for '" + url + "': all fallbacks exhausted\"}");
        }
        return Mono.fromCallable(() -> {
                    log.info("web_fetch fallback browser for '{}'", url);
                    return headlessBrowser.fetchPageContent(url, FETCH_TIMEOUT.toMillis());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(body -> processResponse(body, url, extract))
                .onErrorResume(e -> {
                    log.error("web_fetch browser failed for '{}': {}", url, e.getMessage());
                    return Mono.just("{\"error\": \"Fetch failed for '" + url + "' (all fallbacks exhausted): " + e.getMessage() + "\"}");
                });
    }

    // --- Response processing ---

    private Mono<String> processResponse(String body, String url, String extract) {
        if (extract != null && !extract.isBlank()) {
            String extracted = switch (extract.toLowerCase().trim()) {
                case "semantic_scholar" -> ApiExtractors.extractSemanticScholar(body);
                case "arxiv" -> ApiExtractors.extractArxiv(body);
                case "openalex" -> ApiExtractors.extractOpenAlex(body);
                default -> null;
            };
            if (extracted != null) {
                if (fetchCache != null) fetchCache.put(url, extracted).subscribe();
                if (ingestQueue != null) ingestQueue.enqueue(url, extracted, extract.toLowerCase().trim());
                return Mono.just(extracted);
            }
        }

        if (body.length() <= SearchConfig.CHUNK_SIZE) {
            return Mono.just(body);
        }

        if (chunkStore != null) {
            String contentType = guessContentType(body);
            return chunkStore.storeAndReturnFirst(body, url, contentType);
        }

        return Mono.just(body.substring(0, SearchConfig.CHUNK_SIZE)
                + "\n\n[TRUNCATED: " + body.length() + " bytes total. No ChunkStore available]");
    }

    private String guessContentType(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "application/json";
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) return "text/xml";
        return "text/plain";
    }
}
