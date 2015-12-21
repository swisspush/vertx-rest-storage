/*
 * ------------------------------------------------------------------------------------------------
 * Copyright 2014 by Swiss Post, Information Technology Services
 * ------------------------------------------------------------------------------------------------
 * $Id$
 * ------------------------------------------------------------------------------------------------
 */

package li.chee.vertx.reststorage;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.vertx.testtools.VertxAssert.testComplete;


public class ExpandInStorageTest extends AbstractTestCase {

    final String ETAG_HEADER = "Etag";
    final String IF_NONE_MATCH_HEADER = "if-none-match";
    final String POST_STORAGE_EXP = "/server/resources?expandInStorage=true";

    @Before
    public void setPath() {
        RestAssured.basePath = "/server/resources";
        delete("/");
    }

    @Test
    public void testPOSTWithoutExpandParam() {
        given()
                .body("{ \"foo\": \"bar1\" }")
                .when()
                .post("/server/resources")
                .then()
                .assertThat().statusCode(405);

        testComplete();
    }

    @Test
    public void testPOSTWithWrongBody() {
        given()
                .body("{ \"foo\": \"bar1\" }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(400)
                .assertThat().body(equalTo("Bad Request: Expected array field 'subResources' with names of resources"));

        testComplete();
    }

    @Test
    public void testSimpleWith3Resources() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar2\" }").put("/server/resources/res2");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");

        given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("", allOf(hasKey("res1"), hasKey("res2"), hasKey("res3")))
                .body("res1.foo", equalTo("bar1"))
                .body("res2.foo", equalTo("bar2"))
                .body("res3.foo", equalTo("bar3"));

        testComplete();
    }

    @Test
    public void testSimpleWithUnknownSubResources() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar2\" }").put("/server/resources/res2");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");

        given()
                .body("{ \"subResources\": [\"res1\", \"res2XX\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("", allOf(hasKey("res1"), hasKey("res3")))
                .body("", not(hasKey("res2")))
                .body("res1.foo", equalTo("bar1"))
                .body("res3.foo", equalTo("bar3"));

        testComplete();
    }

    @Test
    public void testSimpleWithMissingSubResources() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");

        given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("", allOf(hasKey("res1"), hasKey("res3")))
                .body("", not(hasKey("res2")))
                .body("res1.foo", equalTo("bar1"))
                .body("res3.foo", equalTo("bar3"));

        testComplete();
    }

    @Test
    public void testSimpleWithEmptyResult() {

        delete("/server/resources");

        given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(404);

        testComplete();
    }

    @Test
    public void testSimpleWithResourcesAndCollections() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar2\" }").put("/server/resources/res2");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");
        with().body("{ \"foo\": \"sub1\" }").put("/server/resources/sub/sub1");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/sub/sub2");

        given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\", \"sub/\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("", allOf(hasKey("res1"), hasKey("res2"), hasKey("res3"), hasKey("sub")))
                .body("res1.foo", equalTo("bar1"))
                .body("res2.foo", equalTo("bar2"))
                .body("res3.foo", equalTo("bar3"))
                .body("sub", hasItems("sub1", "sub2"));

        delete("/server/resources");

        with().body("{ \"foo\": \"sub1\" }").put("/server/resources/sub/sub1");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/sub/sub2");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/anothersub/sub3");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/anothersub/sub4");

        given()
                .body("{ \"subResources\": [\"anothersub/\", \"sub/\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("", allOf(hasKey("anothersub"), hasKey("sub")))
                .body("sub", hasItems("sub1", "sub2"))
                .body("anothersub", hasItems("sub3", "sub4"));

        with().body("{ \"foo\": \"sub1\" }").put("/server/resources/level1/sub/sub1");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/level1/sub/sub2");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/level1/anothersub/sub3");
        with().body("{ \"foo\": \"sub2\" }").put("/server/resources/level1/anothersub/sub4");

        given()
                .body("{ \"subResources\": [\"level1/\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(200).contentType(ContentType.JSON).header(ETAG_HEADER, not(empty()))
                .body("level1", hasItems("anothersub/", "sub/"));

        testComplete();
    }

    @Test
    public void testSameEtagForSameResult() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar2\" }").put("/server/resources/res2");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");

        Response post1 = given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP);
        String etagPost1 = post1.getHeader(ETAG_HEADER);

        Response post2 = given()
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP);
        String etagPost2 = post2.getHeader(ETAG_HEADER);

        Response post3 = given()
                .body("{ \"subResources\": [\"res1\", \"res2\"] }")
                .when()
                .post(POST_STORAGE_EXP);
        String etagPost3 = post3.getHeader(ETAG_HEADER);

        Assert.assertEquals(etagPost1, etagPost2);
        Assert.assertNotEquals(etagPost2, etagPost3);

        testComplete();
    }

    @Test
    public void testIfNoneMatchHeaderProvided() {

        delete("/server/resources");

        with().body("{ \"foo\": \"bar1\" }").put("/server/resources/res1");
        with().body("{ \"foo\": \"bar2\" }").put("/server/resources/res2");
        with().body("{ \"foo\": \"bar3\" }").put("/server/resources/res3");

        String etag = "SomeEtagValue";

        Response post1 = given().header(IF_NONE_MATCH_HEADER, etag)
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP);
        String etagPost1 = post1.getHeader(ETAG_HEADER);

        Assert.assertNotNull(etagPost1);
        Assert.assertNotEquals(etagPost1, etag);

        given().header(IF_NONE_MATCH_HEADER, etagPost1)
                .body("{ \"subResources\": [\"res1\", \"res2\", \"res3\"] }")
                .when()
                .post(POST_STORAGE_EXP)
                .then()
                .assertThat().statusCode(304);

        testComplete();
    }
}
