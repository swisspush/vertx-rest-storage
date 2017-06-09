package org.swisspush.reststorage;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link FileSystemStorage} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class FilesystemStorageTest {

    @Test(expected=UnsupportedOperationException.class)
    public void testGetMemoryUsageNotYetImplemented(TestContext testContext){
        FileSystemStorage storage = new FileSystemStorage(mock(Vertx.class), "/root");
        storage.getCurrentMemoryUsage();
    }
}
