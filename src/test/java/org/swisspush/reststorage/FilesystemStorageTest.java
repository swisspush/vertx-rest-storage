package org.swisspush.reststorage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.swisspush.reststorage.mocks.*;
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
        final boolean[] tmpFileOpened = new boolean[]{ false };
        final Resource[] resourcePtr = new Resource[]{ null };
        final boolean[] errorTriggeredPtr = new boolean[]{ false };
        final boolean[] fileGotClosed = new boolean[]{ false };
        final boolean[] fileGotDeleted = new boolean[]{ false };

        // Setup victim
        final String path;
        FileSystemStorage victim;
        {
            final String root = createPseudoFileStorageRoot();
            final String base = "/path/to/my/fancy";
            path = base + "/file";
            final FileSystem fileSystem = new FailFastVertxFileSystem(){
                @Override public FileSystem exists(String s, Handler<AsyncResult<Boolean>> handler) {
                    s = s.replaceAll("\\\\","/"); // <-- Fix ugly operating systems.
                    if( (root+path).equals(s) ) {
                        // Report that file to store doesn't exists already.
                        handler.handle(new SuccessfulAsyncResult<>(false));
                    }else if( (root+base).equals(s) ){
                        // Report that directory already exists.
                        handler.handle(new SuccessfulAsyncResult<>(true));
                    }else if( s.matches(".*/\\.tmp/uploads/file.*\\.part") ){
                        handler.handle(new SuccessfulAsyncResult<>(tmpFileOpened[0]));
                    }else{
                        throw new UnsupportedOperationException( msg );
                    }
                    return this;
                }
                @Override public FileSystem open(String s, OpenOptions openOptions, Handler<AsyncResult<AsyncFile>> handler) {
                    logger.debug( "Open file '{}'", s );
                    tmpFileOpened[0] = true;
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
                @Override public FileSystem mkdirs(String s, Handler<AsyncResult<Void>> handler) {
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

    @Test
    public void cleanupEmptyParentDirsOnResourceDeletion(final TestContext testContext) {

        // Keep track of test state
        final boolean[] deleteHandlerGotCalled = new boolean[]{ false };
        final boolean[] leafFileDeleted = new boolean[]{ false };
        final boolean[] dir_to_deleted = new boolean[]{ false };
        final boolean[] dir_file_deleted = new boolean[]{ false };
        final boolean[] dir_pseudo_deleted = new boolean[]{ false };
        final boolean[] dir_my_deleted = new boolean[]{ false };

        // Setup victim
        final FileSystemStorage victim;
        {
            // Mock vertx filesystem
            final FileSystem fileSystem = new FailFastVertxFileSystem(){
                @Override public FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler) {
                    path = path.replaceAll("\\\\", "/"); // Fix windows
                    if( path.endsWith("/my/pseudo/file/to/delete") ){
                        handler.handle(new SuccessfulAsyncResult<>(true));
                    }else{
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }
                @Override public FileSystem deleteRecursive(String path, boolean b, Handler<AsyncResult<Void>> handler) {
                    path = path.replaceAll("\\\\","/"); // Fix windows.
                    if( path.endsWith("/my/pseudo/file/to/delete") ){
                        leafFileDeleted[0] = true;
                    }else{
                        testContext.fail( "Got unexpected path '"+path+"'." );
                    }
                    handler.handle(new SuccessfulAsyncResult<>(null));
                    return this;
                }
                @Override public FileSystem delete(String path, Handler<AsyncResult<Void>> handler) {
                    path = path.replaceAll("\\\\","/"); // Fix windows.
                    if( path.endsWith("/my/pseudo/file/to") ) {
                        dir_to_deleted[0] = true;
                    }else if( path.endsWith("/my/pseudo/file") ){
                        dir_file_deleted[0] = true;
                    }else if( path.endsWith("/my/pseudo") ){
                        dir_pseudo_deleted[0] = true;
                    }else if( path.endsWith("/my") ){
                        dir_my_deleted[0] = true;
                    }else{
                        testContext.fail( "Got unexpected path '"+path+"'." );
                    }
                    handler.handle(new SuccessfulAsyncResult<>(null));
                    return this;
                }
            };
            // Mock vertx
            final Vertx mockedVertx = new FailFastVertx(){
                @Override public FileSystem fileSystem() {
                    return fileSystem;
                }
            };
            // Use pseudo root (could be anything because our test will never access real filesystem).
            final String root = createPseudoFileStorageRoot();
            // Wire up victim instance.
            victim = new FileSystemStorage(mockedVertx, root);
        }

        // Challenge victim
        victim.delete( "/my/pseudo/file/to/delete", null, null, 0, false, false, resource -> {
            deleteHandlerGotCalled[0] = true;
        });

        // Assert
        testContext.assertTrue( deleteHandlerGotCalled[0] , "Victim failed to call handler after deletion.");
        testContext.assertTrue( leafFileDeleted[0] , "Victim failed to delete resource itself." );
        testContext.assertTrue( dir_to_deleted[0] , "Victim failed to delete one of the directories.");
        testContext.assertTrue( dir_file_deleted[0] , "Victim failed to delete one of the directories.");
        testContext.assertTrue( dir_pseudo_deleted[0] , "Victim failed to delete one of the directories.");
        testContext.assertTrue( dir_my_deleted[0] , "Victim failed to delete one of the directories.");
    }

    @Test
    public void surviveConcurrentDirectoryDeleteWhilePut(final TestContext testContext) {

        // State tracking
        // States if the 'dirName' directory exists. Or in other words got created
        // through mkdir for example.
        final boolean[] dirExists = {false};
        // States if the directory for temporary files exists (aka '/.tmp/uploads')
        final boolean[] tmpUploadDirExists = {false};
        // Is true as soon the callback passed to storage.put() got called.
        final boolean[] resourceHandlerGotCalled = {false};
        // Counts how many times victim calls fileSystem to create the directory
        // '/foo/bar'.
        final int[] mkdirTmpDirAttemptCount = {0};
        // States if the final file (the one the client likes to upload) really got
        // created.
        final boolean[] finalFileExists = {false};

        // Prepare request
        // Parent directory of file we want to PUT.
        final String dirName = "/foo/bar";
        // File name of file we want to PUT.
        final String baseName = "my-file";
        // Virtual storage path of file we want to PUT.
        final String path = dirName + "/" + baseName;
        // This tmp path is specified in victims implementation. We use it here to be
        // able to give correct answer about this directories existence.
        final String tmpUploadDirPath = "/.tmp/uploads";

        final FileSystemStorage victim;
        { // Setup victim
            final String root = createPseudoFileStorageRoot();
            final FileSystem fileSystem = new FailFastVertxFileSystem() {
                @Override
                public FileSystem exists(String fullPath, Handler<AsyncResult<Boolean>> handler) {
                    fullPath = fullPath.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    if (fullPath.endsWith(root + path)) {
                        // Do state that the file we want to put doesn't exist already.
                        handler.handle(new SuccessfulAsyncResult<>(false));
                    } else if (fullPath.endsWith(root + dirName)) {
                        // State if directory of final file exists dynamically. This first is false and
                        // we'll update that later to true. This way we can force victim to retry
                        // multiple times.
                        handler.handle(new SuccessfulAsyncResult<>(dirExists[0]));
                    } else {
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }

                @Override
                public FileSystem mkdirs(String fullPath, Handler<AsyncResult<Void>> handler) {
                    fullPath = fullPath.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    if (fullPath.endsWith(root + dirName)) {
                        dirExists[0] = true; // "create" directory of final file.
                        mkdirTmpDirAttemptCount[0] += 1;
                        if (mkdirTmpDirAttemptCount[0] < 100) {
                            // Set back to false again to emulate some foreign task deleted our shortly
                            // created directory directly after we created it, forcing victim to retry this
                            // step.
                            dirExists[0] = false;
                        }
                        // State success even someone deleted our directory.
                        handler.handle(new SuccessfulAsyncResult<>(null));
                    } else if (fullPath.endsWith(root + tmpUploadDirPath)) {
                        // Create tmp directory used to store pending uploads.
                        tmpUploadDirExists[0] = true;
                        handler.handle(new SuccessfulAsyncResult<>(null));
                    } else {
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }

                @Override
                public FileSystem open(String fullPath, OpenOptions openOptions, Handler<AsyncResult<AsyncFile>> handler) {
                    fullPath = fullPath.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    if (fullPath.contains(root + tmpUploadDirPath)) {
                        // Provide the tmp file the caller wants to open.
                        final AsyncFile file = new FailFastVertxAsyncFile() {
                            @Override
                            public void close(Handler<AsyncResult<Void>> handler) {
                                handler.handle(new SuccessfulAsyncResult<>(null));
                            }
                        };
                        handler.handle(new SuccessfulAsyncResult<>(file));
                    } else {
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }

                @Override
                public FileSystem delete(String fullPath, Handler<AsyncResult<Void>> handler) {
                    fullPath = fullPath.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    if (fullPath.endsWith(root + path)) {
                        // Confirm we deleted already existing file.
                        handler.handle(new SuccessfulAsyncResult<>(null));
                    } else {
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }

                @Override
                public FileSystem move(String src, String dst, Handler<AsyncResult<Void>> handler) {
                    return move(src, dst, null, handler);
                }

                @Override
                public FileSystem move(String src, String dst, CopyOptions copyOptions, Handler<AsyncResult<Void>> handler) {
                    src = src.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    dst = dst.replaceAll("\\\\", "/"); // <-- Fix ugly operating systems.
                    if (src.startsWith(root + tmpUploadDirPath) && dst.equals(root + path)) {
                        // Move tmp file to final destination.
                        if (dirExists[0]) {
                            // This move only succeeds in case the parent directory of dst exists. Because
                            // this is the case now, we report final file now exists too.
                            finalFileExists[0] = true;
                            handler.handle(new SuccessfulAsyncResult<>(null));
                        } else {
                            // Fail because we cannot move to a directory which doesn't exist. Victim has to
                            // retry creation of this parent directory before we'll succeed here.
                            handler.handle(new FailedAsyncResult<>(null));
                        }
                    } else {
                        throw new UnsupportedOperationException(msg);
                    }
                    return this;
                }
            };
            final Vertx mockedVertx = new FailFastVertx() {
                @Override
                public FileSystem fileSystem() {
                    return fileSystem;
                }
            };
            victim = new FileSystemStorage(mockedVertx, root);
        }

        // Trigger work
        victim.put(path, null, false, 0, null, null, 0, resource -> {
            testContext.assertFalse(resource.error);
            testContext.assertFalse(resource.invalid);
            testContext.assertFalse(resource.rejected);
            // Because this callback got called, the directory of the temporary file has to
            // exist. If not how would we now write to it?
            testContext.assertTrue(tmpUploadDirExists[0]);
            // We should receive a DocumentResource.
            final DocumentResource documentResource = (DocumentResource) resource;
            // Provide noop handler because victim tries to call this without a null check.
            documentResource.endHandler = aVoid -> { /* Ignore */ };
            resourceHandlerGotCalled[0] = true;
            // Trigger close handler to signalize we're done. Victim now has to finalize
            // upload.
            documentResource.closeHandler.handle(null);
        });

        // Assert
        // Make sure our callback (containing asserts too) got called.
        testContext.assertTrue(resourceHandlerGotCalled[0]);
        // Finally the file we originally putted has to exist now.
        testContext.assertTrue(finalFileExists[0], "Victim failed to create final file");
    }

    /**
     * @return
     *      A path denoting a directory inside 'target/' directory for usage as a
     *      temporary fileStorage. This returns only a path. It does not create or
     *      check if such a directory exists or someone has access to it.
     */
    private static String createPseudoFileStorageRoot() {
        return new File( "target/fileStorage-"+ UUID.randomUUID().toString() ).getAbsolutePath().replaceAll("\\\\","/"); // <-- Fix windows
    }
}
