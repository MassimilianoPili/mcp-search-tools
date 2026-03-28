package io.github.massimilianopili.mcp.search.spi;

/**
 * SPI for semantic search prepend (KORE pgvector lookup).
 * When available, web_search prepends cached knowledge results.
 */
public interface SemanticLookup {

    boolean isAvailable();

    String search(String query, int limit);
}
