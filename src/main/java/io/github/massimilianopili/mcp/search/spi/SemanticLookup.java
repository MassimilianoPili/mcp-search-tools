package io.github.massimilianopili.mcp.search.spi;

/**
 * SPI for semantic search prepend (KORE pgvector lookup).
 * When available, web_search prepends cached knowledge results.
 */
public interface SemanticLookup {

    boolean isAvailable();

    String search(String query, int limit);

    /**
     * Graph-augmented search: pgvector lookup + AGE graph traversal expansion.
     * Returns vector results enriched with graph neighbors (authors, topics, venues).
     * Default falls back to pure vector search when graph backend is unavailable.
     *
     * @param query       search query
     * @param vectorLimit max vector results (seed nodes for graph expansion)
     * @param graphDepth  traversal depth 1-2 (default 1)
     * @return JSON string with enriched results, or null if unavailable
     */
    default String searchWithGraphExpansion(String query, int vectorLimit, int graphDepth) {
        return search(query, vectorLimit);
    }
}
