package org.swisspush.reststorage;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link RestStorageHandler} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class RestStorageHandlerTest {

    private Vertx vertx;
    private Storage storage;
    private RestStorageHandler restStorageHandler;
    private Logger log;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        storage = mock(Storage.class);
        log = mock(Logger.class);

        restStorageHandler = new RestStorageHandler(vertx, log, storage, "/", null,
                "lockPrefix", false, true);
    }

    @Test
    public void testOne(TestContext testContext){
        testContext.assertTrue(true);
    }
}
