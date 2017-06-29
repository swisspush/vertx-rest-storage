package org.swisspush.reststorage;

import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.swisspush.reststorage.util.HttpRequestHeader.PATH_PROCESSING_STRATEGY_HEADER;
import static org.swisspush.reststorage.util.ModuleConfiguration.PathProcessingStrategy.cleaned;
import static org.swisspush.reststorage.util.ModuleConfiguration.PathProcessingStrategy.unmodified;

/**
 * <p>
 * Tests for the {@link PathProcessingStrategyFinder} class
 * </p>
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class PathProcessingStrategyFinderTest {

    @Test
    public void testGetDefaultPathProcessingStrategy(TestContext context){
        PathProcessingStrategyFinder strategyFinder = new PathProcessingStrategyFinder(null);
        context.assertEquals(cleaned, strategyFinder.getDefaultPathProcessingStrategy());

        strategyFinder = new PathProcessingStrategyFinder(unmodified);
        context.assertEquals(unmodified, strategyFinder.getDefaultPathProcessingStrategy());
    }

    @Test
    public void testGetPathProcessingStrategy(TestContext context){
        PathProcessingStrategyFinder strategyFinder = new PathProcessingStrategyFinder(unmodified);

        // no headers, fallback to default
        context.assertEquals(unmodified, strategyFinder.getPathProcessingStrategy(null));

        // no path-processing-strategy header, fallback to default
        context.assertEquals(unmodified, strategyFinder.getPathProcessingStrategy(new CaseInsensitiveHeaders()));

        // invalid path-processing-strategy header, fallback to default
        context.assertEquals(unmodified, strategyFinder.getPathProcessingStrategy(
                new CaseInsensitiveHeaders().set(PATH_PROCESSING_STRATEGY_HEADER.getName(), "booom")));

        // valid path-processing-strategy header. this value should be returned
        context.assertEquals(unmodified, strategyFinder.getPathProcessingStrategy(
                new CaseInsensitiveHeaders().set(PATH_PROCESSING_STRATEGY_HEADER.getName(), "unmodified")));
        context.assertEquals(cleaned, strategyFinder.getPathProcessingStrategy(
                new CaseInsensitiveHeaders().set(PATH_PROCESSING_STRATEGY_HEADER.getName(), "cleaned")));
    }
}
