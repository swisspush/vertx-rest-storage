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

@RunWith(VertxUnitRunner.class)
public abstract class FilesystemStorageTestCase extends ConfigurableTestCase {

    protected static final String TEST_FILES_PATH = "filesystemStorageTestFiles";

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();

        // RestAssured Configuration
        RestAssured.port = REST_STORAGE_PORT;
        RestAssured.requestSpecification = REQUEST_SPECIFICATION;
        RestAssured.registerParser("application/json; charset=utf-8", Parser.JSON);
        RestAssured.defaultParser = Parser.JSON;

        ModuleConfiguration modConfig = ModuleConfiguration.with()
                .storageType(ModuleConfiguration.StorageType.filesystem)
                .confirmCollectionDelete(true)
                .storageAddress("rest-storage")
                .build();

        RestStorageMod restStorageMod = new RestStorageMod();
        vertx.deployVerticle(restStorageMod, new DeploymentOptions().setConfig(modConfig.asJsonObject()), context.asyncAssertSuccess(stringAsyncResult1 -> {
            // standard code: will called @Before every test
            RestAssured.basePath = "";
        }));
    }

    @After
    public void deleteTestFiles(TestContext context){
        vertx.fileSystem().deleteRecursiveBlocking(TEST_FILES_PATH, true);
    }
}