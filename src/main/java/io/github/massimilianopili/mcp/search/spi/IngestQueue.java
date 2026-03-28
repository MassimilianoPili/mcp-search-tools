package io.github.massimilianopili.mcp.search.spi;

/**
 * SPI for queueing fetched content for knowledge graph ingest.
 */
public interface IngestQueue {

    void enqueue(String url, String extractedJson, String extractType);
}
