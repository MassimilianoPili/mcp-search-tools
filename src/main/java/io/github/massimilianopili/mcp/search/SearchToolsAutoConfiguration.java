package io.github.massimilianopili.mcp.search;

import io.github.massimilianopili.mcp.search.spi.ContentIngester;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.search.enabled", havingValue = "true", matchIfMissing = false)
@Import({SearchConfig.class, WebSearchTools.class})
public class SearchToolsAutoConfiguration {

    /**
     * WebIngestTools requires a ContentIngester SPI implementation.
     * Only imported when available in the host application context.
     */
    @AutoConfiguration
    @ConditionalOnBean(ContentIngester.class)
    @Import(WebIngestTools.class)
    public static class IngestAutoConfiguration {
    }
}
