package org.swisspush.reststorage;

import io.vertx.core.Handler;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.swisspush.reststorage.util.LockMode;

import java.io.File;
import java.util.UUID;


/**
 * Used by {@link FileSystemStorage} to execute file PUTs.
 */
public class FilePutter {

    public static interface FilePutterCallbacks {
        /** Same as {@link FileSystemStorage#delete(String, String, LockMode, long, boolean, boolean, Handler)}. */
        void delete(String path, String lockOwner, LockMode lockMode, long lockExpire, boolean confirmCollectionDelete, boolean deleteRecursive, final Handler<Resource> handler);
        /** Same as {@link FileSystemStorage#canonicalize(String)}. */
        String canonicalize(String path);
    }

    private static final int MOVE_TO_FINAL_DESTINATION_ATTEMPT_LIMIT = 256;
    private static final Logger log = LoggerFactory.getLogger(FilePutter.class);
    private final CopyOptions moveOptions = new CopyOptions().setReplaceExisting(true);
    private final FileSystem fileSystem;
    private final String fullPath;
    private final FilePutterCallbacks callbacks;
    private final Handler<Resource> onCompleteHandler;
    private String tmpFilePath;
    private String tmpFilePathAbs;
    private String tmpFileParentPath;
    private volatile boolean executed = false;
    private int moveToFinalDestinationAttemptCount = 0;

    /**
     * Package-private because currently only used internally.
     */
    FilePutter(FileSystem fileSystem, String fullPath, FilePutterCallbacks callbacks, Handler<Resource> onCompleteHandler) {
        this.fileSystem = fileSystem;
        this.fullPath = fullPath;
        this.callbacks = callbacks;
        this.onCompleteHandler = onCompleteHandler;
    }

    /**
     * <p>Triggers the configured task. This method should only get called once per
     * instance!</p>
     *
     * @throws IllegalStateException
     *      Eg. in case method gets called more than one time.
     */
    public synchronized void execute() {
        if( executed ){
            throw new IllegalStateException( "This putter already got executed." );
        }
        this.executed = true;
        // Setup required context.
        this.tmpFilePath = "/.tmp/uploads/" + new File(fullPath).getName() + "-" + UUID.randomUUID().toString() + ".part";
        this.tmpFilePathAbs = callbacks.canonicalize(tmpFilePath);
        this.tmpFileParentPath = new File(tmpFilePathAbs).getParent();
        // Prepare directory for temporary file.
        fileSystem.mkdirs(tmpFileParentPath, result -> {
            if (result.succeeded()) {
                openTmpFile();
            } else {
                log.warn("Failed to create directory '" + tmpFileParentPath + "'.");
                resolveWithErroneousResource();
            }
        });
    }

    private void openTmpFile() {
        fileSystem.open(tmpFilePathAbs, new OpenOptions(), result -> {
            if (result.succeeded()) {
                resolveWithTmpFileResource(result.result());
            } else {
                log.warn("Failed to open tmp file '{}'.", tmpFilePathAbs);
                resolveWithErroneousResource();
            }
        });
    }

    private void resolveWithTmpFileResource(final AsyncFile tmpFile) {
        final DocumentResource d = new DocumentResource();
        d.writeStream = tmpFile;
        d.closeHandler = v -> {
            tmpFile.close(ev -> {
                moveTmpFileToFinalDestination(d);
            });
        };
        d.addErrorHandler(err -> {
            log.error("Put file failed:", err);
            cleanupFile(tmpFile);
        });
        // Resolve with ready-to-use resource.
        onCompleteHandler.handle(d);
    }

    private void moveTmpFileToFinalDestination(DocumentResource d) {
        moveToFinalDestinationAttemptCount += 1;
        // Move/rename our temporary file to its final destination.
        fileSystem.move(tmpFilePathAbs, fullPath, moveOptions, moveResult -> {
            if (moveResult.succeeded()) {
                log.debug("File stored successfully: {}", fullPath);
                d.endHandler.handle(null);
            } else if (moveToFinalDestinationAttemptCount < MOVE_TO_FINAL_DESTINATION_ATTEMPT_LIMIT) {
                mkdirsAndTriggerMove(d);
            } else {
                log.error("Failed to move tmp file '{}' to its final destination '{}' even after trying {} times.", tmpFilePathAbs, fullPath, moveToFinalDestinationAttemptCount);
                d.errorHandler.handle(moveResult.cause());
            }
        });
    }

    private void mkdirsAndTriggerMove(DocumentResource d) {
        // Creating (possibly missing) parent dirs and try again.
        fileSystem.mkdirs(dirName(fullPath), mkdirResult -> {
            if (mkdirResult.succeeded()) {
                // Trigger move
                moveTmpFileToFinalDestination(d);
            } else {
                log.error("Failed to create parent dirs of '{}'.", fullPath);
                d.errorHandler.handle(mkdirResult.cause());
            }
        });
    }

    private void cleanupFile(AsyncFile file) {
        file.close(closeResult -> {
            if (closeResult.succeeded()) {
                log.debug("Tmp file '{}' closed.", tmpFilePathAbs);
            } else {
                log.warn("Failed to close tmp file '{}'.", file, closeResult.cause());
            }
            callbacks.delete(tmpFilePath, null, null, 0, false, true, resource -> {
                if( resource.error ){
                    log.warn("Failed to delete file '{}': {}", tmpFilePathAbs, resource.errorMessage);
                }else{
                    log.debug("File '{}' deleted.", tmpFilePathAbs);
                }
            });
        });
    }

    private void resolveWithErroneousResource() {
        final Resource r = new Resource();
        r.error = true;
        r.exists = false;
        onCompleteHandler.handle(r);
    }

    private String dirName(String path) {
        return new File(path).getParent();
    }

}
