package io.github.massimilianopili.mcp.search.spi;

import reactor.core.publisher.Mono;

/**
 * SPI for caching fetched URL content (extracted JSON).
 * Implement backed by Redis or any reactive cache.
 */
public interface FetchCache {

    Mono<String> get(String url);

    Mono<Boolean> put(String url, String extractedJson);
}
