package io.github.massimilianopili.mcp.search.spi;

import java.util.List;
import java.util.Map;

/**
 * SPI for ingesting web content into knowledge graph + vector store.
 * Implement backed by AGE CypherExecutor + VectorStore.
 */
public interface ContentIngester {

    String ingest(String url, String title, String contentType, String body,
                  List<String> authors, Integer year, String venue,
                  List<String> concepts, Map<String, Object> extraMeta);

    String ingestFromExtract(String extractedJson);
}
