package io.github.massimilianopili.mcp.search.spi;

import reactor.core.publisher.Mono;

/**
 * SPI for storing and retrieving chunked fetch content.
 * Implement backed by Redis (or any KV store with TTL).
 */
public interface ChunkStore {

    Mono<String> storeAndReturnFirst(String content, String url, String contentType);

    Mono<String> getChunk(String fetchId, int chunkIndex);
}
