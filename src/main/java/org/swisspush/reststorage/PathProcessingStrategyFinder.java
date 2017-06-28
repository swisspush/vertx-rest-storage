package org.swisspush.reststorage;

import io.vertx.core.MultiMap;
import org.swisspush.reststorage.util.HttpRequestHeader;
import org.swisspush.reststorage.util.ModuleConfiguration.PathProcessingStrategy;

import static org.swisspush.reststorage.util.HttpRequestHeader.PATH_PROCESSING_STRATEGY_HEADER;
import static org.swisspush.reststorage.util.HttpRequestHeader.getString;

/**
 * Util class to get the {@link PathProcessingStrategy} based on the static configuration and http request headers.
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
public class PathProcessingStrategyFinder {
    private final PathProcessingStrategy pathProcessingStrategy;

    public PathProcessingStrategyFinder(PathProcessingStrategy pathProcessingStrategy) {
        this.pathProcessingStrategy = pathProcessingStrategy;
    }

    /**
     * Get the {@link PathProcessingStrategy} configured as default value.
     *
     * @return the default {@link PathProcessingStrategy}
     */
    public PathProcessingStrategy getDefaultPathProcessingStrategy() { return  pathProcessingStrategy; }

    /**
     * Get the {@link PathProcessingStrategy} based on the static configuration (default value) and the http
     * request headers. When the http request headers contain the {@link HttpRequestHeader#PATH_PROCESSING_STRATEGY_HEADER}
     * with a valid value, the {@link PathProcessingStrategy} relating this value will be returned. If the header is
     * missing or does not contain a valid value, the default {@link PathProcessingStrategy} will be returned.
     *
     * @param requestHeaders the http request headers of the request
     * @return {@link PathProcessingStrategy} based on the static configuration (default value) and the http request headers
     */
    public PathProcessingStrategy getPathProcessingStrategy(MultiMap requestHeaders) {
        if(requestHeaders == null){
            return pathProcessingStrategy;
        }
        if(HttpRequestHeader.containsHeader(requestHeaders, PATH_PROCESSING_STRATEGY_HEADER)){
            PathProcessingStrategy strategy = PathProcessingStrategy.fromString(getString(requestHeaders, PATH_PROCESSING_STRATEGY_HEADER));
            if(strategy != null){
                return strategy;
            }
        }
        return pathProcessingStrategy;
    }
}
