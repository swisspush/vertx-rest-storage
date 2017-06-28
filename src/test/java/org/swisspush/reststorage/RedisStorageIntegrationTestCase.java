package org.swisspush.reststorage;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.swisspush.reststorage.util.ModuleConfiguration;
import org.swisspush.reststorage.util.ModuleConfiguration.PathProcessingStrategy;
import org.swisspush.reststorage.util.ModuleConfiguration.StorageType;
import redis.clients.jedis.Jedis;

@RunWith(VertxUnitRunner.class)
public abstract class RedisStorageIntegrationTestCase extends ConfigurableTestCase {

    Jedis jedis = null;

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
                .storageType(StorageType.redis)
                .confirmCollectionDelete(true)
                .pathProcessingStrategy(PathProcessingStrategy.cleaned)
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