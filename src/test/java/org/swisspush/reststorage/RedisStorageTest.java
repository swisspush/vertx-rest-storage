package org.swisspush.reststorage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.RedisClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.swisspush.reststorage.util.ModuleConfiguration;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link RedisStorage} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class RedisStorageTest {

    private RedisClient redisClient;
    private RedisStorage storage;

    @Before
    public void setUp(TestContext context) {
        redisClient = Mockito.mock(RedisClient.class);
        storage = new RedisStorage(mock(Vertx.class), new ModuleConfiguration(), redisClient);
    }


    @Test
    public void testCalculateCurrentMemoryUsageRedisClientFail(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new FailAsyncResult() {
                @Override
                public Throwable cause() {
                    return new RuntimeException("Booom");
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageMissingMemorySection(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("data", new JsonObject().put("some_property", "some_value"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageMissingTotalSystemMemory(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject().put("some_property", "some_value"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageTotalSystemMemoryZero(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject().put("total_system_memory", "0"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageTotalSystemMemoryWrongType(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject().put("total_system_memory", 12345));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageMissingUsedMemory(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory",
                            new JsonObject()
                                    .put("total_system_memory", "1000")
                                    .put("some_other_property", "a_value"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsageUsedMemoryWrongType(TestContext testContext) {
        Async async = testContext.async();

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject()
                            .put("total_system_memory", "12345")
                    .put("used_memory", 123));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertFalse(optionalAsyncResult.result().isPresent());
            async.complete();
        });
    }

    @Test
    public void testCalculateCurrentMemoryUsage(TestContext testContext) {
        Async async = testContext.async(4);

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject()
                            .put("total_system_memory", "100")
                            .put("used_memory", "75"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertTrue(optionalAsyncResult.result().isPresent());
            testContext.assertEquals(75.0f, optionalAsyncResult.result().get());
            async.countDown();
        });

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject()
                            .put("total_system_memory", "100")
                            .put("used_memory", "0"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertTrue(optionalAsyncResult.result().isPresent());
            testContext.assertEquals(0.0f, optionalAsyncResult.result().get());
            async.countDown();
        });

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject()
                            .put("total_system_memory", "100")
                            .put("used_memory", "150"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertTrue(optionalAsyncResult.result().isPresent());
            testContext.assertEquals(100.0f, optionalAsyncResult.result().get());
            async.countDown();
        });

        when(redisClient.infoSection(eq("memory"), any(Handler.class))).thenAnswer(invocation -> {
            ((Handler<AsyncResult<JsonObject>>) invocation.getArguments()[1]).handle(new SuccessAsyncResult() {
                @Override
                public JsonObject result() {
                    return new JsonObject().put("memory", new JsonObject()
                            .put("total_system_memory", "100")
                            .put("used_memory", "-20"));
                }
            });
            return null;
        });

        storage.calculateCurrentMemoryUsage().setHandler(optionalAsyncResult -> {
            testContext.assertTrue(optionalAsyncResult.succeeded());
            testContext.assertTrue(optionalAsyncResult.result().isPresent());
            testContext.assertEquals(0.0f, optionalAsyncResult.result().get());
            async.countDown();
        });

        async.awaitSuccess();
    }

    private class SuccessAsyncResult implements AsyncResult<JsonObject> {

        @Override
        public JsonObject result() {
            return null;
        }

        @Override
        public Throwable cause() {
            return null;
        }

        @Override
        public boolean succeeded() {
            return true;
        }

        @Override
        public boolean failed() {
            return false;
        }
    }

    private class FailAsyncResult implements AsyncResult<JsonObject> {

        @Override
        public JsonObject result() {
            return null;
        }

        @Override
        public Throwable cause() {
            return null;
        }

        @Override
        public boolean succeeded() {
            return false;
        }

        @Override
        public boolean failed() {
            return true;
        }
    }
}
