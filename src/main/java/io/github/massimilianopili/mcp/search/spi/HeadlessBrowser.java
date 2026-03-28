package io.github.massimilianopili.mcp.search.spi;

/**
 * SPI for headless browser fallback (e.g., Playwright).
 * Used when standard HTTP fetch fails with 403/429.
 */
public interface HeadlessBrowser {

    boolean isAvailable();

    String fetchPageContent(String url, long timeoutMs);
}
