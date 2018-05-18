package org.swisspush.reststorage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swisspush.reststorage.mocks.FailFastVertx;
import org.swisspush.reststorage.mocks.FailFastVertxAsyncFile;
import org.swisspush.reststorage.mocks.FailFastVertxFileSystem;
import org.swisspush.reststorage.mocks.SuccessfulAsyncResult;
import org.swisspush.reststorage.util.LockMode;

import java.io.File;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link FileSystemStorage} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class FilesystemStorageTest {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemStorageTest.class);

    @Test(expected=UnsupportedOperationException.class)
    public void testGetMemoryUsageNotYetImplemented(TestContext testContext){
        FileSystemStorage storage = new FileSystemStorage(mock(Vertx.class), "/root");
        storage.getCurrentMemoryUsage();
    }

    @Test
    public void removesFilesWhenPutGotInterrupted(TestContext testContext) throws InterruptedException {

        // Keep track of state during test
        final boolean[] handlerCalledPtr = new boolean[]{ false };
        final Resource[] resourcePtr = new Resource[]{ null };
        final boolean[] errorTriggeredPtr = new boolean[]{ false };
        final boolean[] fileGotClosed = new boolean[]{ false };
        final boolean[] fileGotDeleted = new boolean[]{ false };

        // Setup victim
        final String path;
        FileSystemStorage victim;
        {
            final String root = new File( "target/fileStorage-"+ UUID.randomUUID().toString() ).getAbsolutePath().replaceAll("\\\\","/"); // <-- Fix windows
            final String base = "/path/to/my/fancy";
            path = base + "/file";
            final FileSystem fileSystem = new FailFastVertxFileSystem(){
                @Override public FileSystem exists(String s, Handler<AsyncResult<Boolean>> handler) {
                    s = s.replaceAll("\\\\","/"); // <-- Fix windows again
                    if( (root+path).equals(s) ) {
                        // Report that file to store doesn't exists already.
                        handler.handle(new SuccessfulAsyncResult<>(false));
                    }else if( (root+base).equals(s) ){
                        // Report that directory already exists.
                        handler.handle(new SuccessfulAsyncResult<>(true));
                    }else if( s.length()>(root+base).length() && s.startsWith(root+base) ){
                        // Confirm that tmp file exists
                        handler.handle(new SuccessfulAsyncResult<>(true));
                    }else{
                        throw new UnsupportedOperationException( msg );
                    }
                    return this;
                }
                @Override public FileSystem open(String s, OpenOptions openOptions, Handler<AsyncResult<AsyncFile>> handler) {
                    logger.debug( "Open file '{}'", s );
                    final AsyncFile file = new FailFastVertxAsyncFile(){
                        @Override public void close(Handler<AsyncResult<Void>> handler) {
                            logger.debug("Closing file '{}'", s);
                            synchronized (errorTriggeredPtr){
                                if( !errorTriggeredPtr[0]) testContext.fail("Must close file AFTER there was an error.");
                            }
                            synchronized (fileGotClosed){
                                fileGotClosed[0] = true;
                            }
                            handler.handle(new SuccessfulAsyncResult<>(null));
                        }
                    };
                    handler.handle(new SuccessfulAsyncResult<>(file));
                    return this;
                }
                @Override public FileSystem deleteRecursive(String s, boolean b, Handler<AsyncResult<Void>> handler) {
                    // This check may not suffice because it succeeds no matter which file gets
                    // deleted.
                    synchronized (fileGotDeleted){
                        logger.debug( "Delete recursive '{}'.", s);
                        fileGotDeleted[0] = true;
                    }
                    handler.handle(new SuccessfulAsyncResult<>(null));
                    return this;
                }
            };
            final Vertx mockedVertx = new FailFastVertx(){
                @Override public FileSystem fileSystem() {
                    return fileSystem;
                }
            };
            victim = new FileSystemStorage(mockedVertx, root);
        }

        // Challenge victim
        {
            final String etag = null; // Not used by implementation.
            final boolean merge = false; // Not used by implementation.
            final long expire = 0; // Not used by implementation.
            final String lockOwner = null; // Not used by implementation.
            final LockMode lockMode = null; // Not used by implementation.
            final long lockExpire = 0; // Not used by implementation.
            final boolean storeCompressed = false; // Not used by implementation.

            final Handler<Resource> handler = resource -> {
                resourcePtr[0] = resource;
                synchronized (handlerCalledPtr){
                    logger.debug( "put() handler got called.");
                    handlerCalledPtr[0] = true;
                }
            };

            victim.put(path, etag, merge, expire, lockOwner, lockMode, lockExpire, storeCompressed, handler);
        }

        // If below assert fails. You may need to insert a Thread.sleep here to give
        // victim a chance to fulfill our query.

        synchronized (handlerCalledPtr){
            testContext.assertTrue(handlerCalledPtr[0],"Victim failed to call our handler.");
        }

        testContext.assertNotNull(resourcePtr[0], "Victim failed to deliver resource to us via callback.");

        logger.debug( "Trigger an error on the resource" );
        {
            final Handler<Throwable> errorHandler = resourcePtr[0].errorHandler;
            synchronized (errorTriggeredPtr){
                errorTriggeredPtr[0] = true;
            }
            if(errorHandler != null){
                errorHandler.handle(new Exception("My fancy mock exception :)"));
            }
        }

        synchronized (fileGotDeleted){
            testContext.assertTrue(fileGotDeleted[0],"Victim failed to delete file.");
        }
    }

}
