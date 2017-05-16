package org.swisspush.reststorage;

import com.jayway.restassured.http.ContentType;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

@RunWith(VertxUnitRunner.class)
public class FilesystemStorageTest extends FilesystemStorageTestCase {

    @Test
    public void testGetCollection(TestContext testContext) throws InterruptedException {
        Async async = testContext.async();
        String path = TEST_FILES_PATH + "/collection/sub/resources/";
        with().body("<h1>nemo.html</h1>").put(path + "nemo.html");
        with().body("<h1>index.html</h1>").put(path + "index.html");
        get(path).then().assertThat().statusCode(200)
                .contentType("application/json")
                .body("resources", hasItems("index.html", "nemo.html"));
        async.complete();
    }

    @Test
    public void testDeleteCollectionWithRecursiveParameter(TestContext testContext) throws InterruptedException {
        Async async = testContext.async();
        String path = TEST_FILES_PATH + "/collection/sub/resources/";
        with().body("<h1>nemo.html</h1>").put(path + "nemo.html");
        get(path +"nemo.html").then().assertThat().
                statusCode(200).
                assertThat().
                contentType(ContentType.HTML);

        //delete non-empty collection
        with().param("recursive", true).delete(path).then().assertThat().statusCode(200);

        get(path +"nemo.html").then().assertThat().
                statusCode(404);

        async.complete();
    }

    @Test
    public void testDeleteCollectionWithoutRecursiveParameter(TestContext testContext) throws InterruptedException {
        Async async = testContext.async();
        String path = TEST_FILES_PATH + "/collection/sub/resources/";
        with().body("<h1>nemo.html</h1>").put(path + "nemo.html");
        get(path +"nemo.html").then().assertThat().
                statusCode(200).
                assertThat().
                contentType(ContentType.HTML);

        //delete non-empty collection
        delete(path).then().assertThat().statusCode(400).body(equalTo("Bad Request: directory not empty. Use recursive=true parameter to delete"));

        get(path +"nemo.html").then().assertThat().
                statusCode(200);

        async.complete();
    }
}
