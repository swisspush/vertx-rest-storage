package org.swisspush.reststorage;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.parsing.Parser;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.swisspush.reststorage.util.ModuleConfiguration;
import redis.clients.jedis.Jedis;

import static org.swisspush.reststorage.util.HttpRequestHeader.*;

@RunWith(VertxUnitRunner.class)
public abstract class AbstractTestCase {

    Vertx vertx;
    Jedis jedis = null;

    // restAssured Configuration
    private static final int REST_STORAGE_PORT = 8989;
    private static RequestSpecification REQUEST_SPECIFICATION = new RequestSpecBuilder()
            .addHeader(CONTENT_TYPE.getName(), "application/json")
            .setPort(8989)
            .setBasePath("/")
            .build();

    @BeforeClass
    public static void config() {
        if(!RedisEmbeddedConfiguration.useExternalRedis()) {
            RedisEmbeddedConfiguration.redisServer.start();
        }
    }

    @AfterClass
    public static void stopRedis() {
        if(!RedisEmbeddedConfiguration.useExternalRedis()) {
            RedisEmbeddedConfiguration.redisServer.stop();
        }
    }

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        jedis = JedisFactory.createJedis();

        // RestAssured Configuration
        RestAssured.port = REST_STORAGE_PORT;
        RestAssured.requestSpecification = REQUEST_SPECIFICATION;
        RestAssured.registerParser("application/json; charset=utf-8", Parser.JSON);
        RestAssured.defaultParser = Parser.JSON;

        ModuleConfiguration modConfig = ModuleConfiguration.with()
                .storageType(ModuleConfiguration.StorageType.redis)
                .storageAddress("rest-storage")
                .build();

        RestStorageMod restStorageMod = new RestStorageMod();
        vertx.deployVerticle(restStorageMod, new DeploymentOptions().setConfig(modConfig.asJsonObject()), context.asyncAssertSuccess(stringAsyncResult1 -> {
            // standard code: will called @Before every test
            RestAssured.basePath = "";
        }));
    }

    @After
    public void tearDown(TestContext context) {
        jedis.flushAll();
        jedis.close();
        vertx.close(context.asyncAssertSuccess());
    }
}