package io.github.massimilianopili.mcp.search;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.mcp.search.spi.ContentIngester;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class WebIngestTools {

    private final ContentIngester ingester;

    public WebIngestTools(ContentIngester ingester) {
        this.ingester = ingester;
    }

    @ReactiveTool(
            name = "web_ingest",
            description = "Archives web content into the knowledge graph (AGE) and embedding store (pgvector). " +
                          "Creates nodes (Paper/BlogPost/Documentation/WebContent) + Author + Venue + Concept " +
                          "with WRITTEN_BY, PUBLISHED_IN, HAS_CONCEPT relationships. " +
                          "Generates embeddings. Idempotent: MERGE on archival_id (title slug). " +
                          "For papers from APIs, use web_ingest_from_extract with web_fetch extract output."
    )
    public Mono<String> webIngest(
            @ToolParam(description = "Source URL of the content") String url,
            @ToolParam(description = "Content title") String title,
            @ToolParam(description = "Type: 'paper', 'blog', 'docs', 'generic'. Default: 'generic'",
                       required = false) String contentType,
            @ToolParam(description = "Full text or abstract of the content") String body,
            @ToolParam(description = "Comma-separated authors", required = false) String authors,
            @ToolParam(description = "Publication year", required = false) Integer year,
            @ToolParam(description = "Venue/journal/source", required = false) String venue,
            @ToolParam(description = "Comma-separated concepts/tags", required = false) String concepts) {

        return Mono.fromCallable(() -> {
            List<String> authorList = splitCsv(authors);
            List<String> conceptList = splitCsv(concepts);
            String type = (contentType != null && !contentType.isBlank()) ? contentType : "generic";
            return ingester.ingest(url, title, type, body, authorList, year, venue, conceptList, Map.of());
        });
    }

    @ReactiveTool(
            name = "web_ingest_from_extract",
            description = "Archives a paper into the knowledge graph from web_fetch extract output. " +
                          "Accepts the JSON returned by web_fetch(url, extract='semantic_scholar'|'arxiv'|'openalex'). " +
                          "Automatically maps fields and creates nodes + embedding."
    )
    public Mono<String> webIngestFromExtract(
            @ToolParam(description = "JSON returned by web_fetch with extract (contains 'extracted_from')") String extractedJson) {
        return Mono.fromCallable(() -> ingester.ingestFromExtract(extractedJson));
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
