package org.swisspush.reststorage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.swisspush.reststorage.util.LockMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.util.*;

public class FileSystemStorage implements Storage {

    private String root;
    private Vertx vertx;
    private final int rootLen;

    private Logger log = LoggerFactory.getLogger(FileSystemStorage.class);

    public FileSystemStorage(Vertx vertx, String root) {
        this.vertx = vertx;
        this.root = root;
        { // Cache string length of root without trailing slashes
            final String rootAbs;
            try {
                rootAbs = new File(root).getCanonicalPath();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to canonicalize root: '"+root+"'.", e);
            }
            int rootLen;
            for( rootLen=rootAbs.length()-1 ; rootAbs.charAt(rootLen) == File.separatorChar ; --rootLen );
            this.rootLen = rootLen;
        }
    }

    @Override
    public Optional<Float> getCurrentMemoryUsage() {
        throw new UnsupportedOperationException("Method 'getCurrentMemoryUsage' is not yet implemented for the FileSystemStorage");
    }

    @Override
    public void get(String path, String etag, final int offset, final int count, final Handler<Resource> handler) {
        final String fullPath = canonicalize(path);
        fileSystem().exists(fullPath, booleanAsyncResult -> {
            if (booleanAsyncResult.result()) {
                fileSystem().props(fullPath, filePropsAsyncResult -> {
                    final FileProps props = filePropsAsyncResult.result();
                    if (props.isDirectory()) {
                        fileSystem().readDir(fullPath, event1 -> {
                            final int length = event1.result().size();
                            final CollectionResource c = new CollectionResource();
                            c.items = new ArrayList<>(length);
                            if (length == 0) {
                                handler.handle(c);
                                return;
                            }
                            final int dirLength = fullPath.length();
                            for (final String item : event1.result()) {
                                fileSystem().props(item, itemProp -> {
                                    Resource r;
                                    if (itemProp.succeeded() && itemProp.result().isDirectory()) {
                                        r = new CollectionResource();
                                    } else if (itemProp.succeeded() && itemProp.result().isRegularFile()) {
                                        r = new DocumentResource();
                                    } else {
                                        r = new Resource();
                                        r.exists = false;
                                    }
                                    r.name = item.substring(dirLength + 1);
                                    c.items.add(r);
                                    if (c.items.size() == length) {
                                        Collections.sort(c.items);
                                        int n = count;
                                        if(n == -1) {
                                            n = length;
                                        }
                                        if(offset > -1) {
                                            if(offset >= c.items.size() || (offset+n) >= c.items.size() || (offset == 0 && n == -1)) {
                                                handler.handle(c);
                                            } else {
                                                c.items = c.items.subList(offset, offset+n);
                                                handler.handle(c);
                                            }
                                        }
                                    }
                                });
                            }
                        });
                    } else if (props.isRegularFile()) {
                        fileSystem().open(fullPath, new OpenOptions(), event1 -> {
                            DocumentResource d = new DocumentResource();
                            d.length = props.size();
                            d.readStream = event1.result();
                            d.closeHandler = v -> event1.result().close();
                            handler.handle(d);
                        });
                    } else {
                        Resource r = new Resource();
                        r.exists = false;
                        handler.handle(r);
                    }
                });
            } else {
                Resource r = new Resource();
                r.exists = false;
                handler.handle(r);
            }
        });
    }

    @Override
    public void put(String path, String etag, boolean merge, long expire, final Handler<Resource> handler) {
        put(path, etag, merge, expire, "", LockMode.SILENT, 0, handler);
    }

    @Override
    public void put(String path, String etag, boolean merge, long expire, String lockOwner, LockMode lockMode, long lockExpire, Handler<Resource> handler) {
        final String fullPath = canonicalize(path);
        fileSystem().exists(fullPath, event -> {
            if (event.result()) {
                fileSystem().props(fullPath, event1 -> {
                    final FileProps props = event1.result();
                    if (props.isDirectory()) {
                        CollectionResource c = new CollectionResource();
                        handler.handle(c);
                    } else if (props.isRegularFile()) {
                        putFile(handler, fullPath);
                    } else {
                        Resource r = new Resource();
                        r.exists = false;
                        handler.handle(r);
                    }
                });
            } else {
                final String dirName = dirName(fullPath);
                fileSystem().exists(dirName, event1 -> {
                    if (event1.result()) {
                        putFile(handler, fullPath);
                    } else {
                        fileSystem().mkdirs(dirName, event2 -> putFile(handler, fullPath));
                    }
                });
            }
        });
    }

    @Override
    public void put(String path, String etag, boolean merge, long expire, String lockOwner, LockMode lockMode, long lockExpire, boolean storeCompressed, Handler<Resource> handler) {
        log.warn("PUT with storeCompressed option is not yet implemented in file system storage. Ignoring storeCompressed option value");
        put(path, etag, merge, expire, "", LockMode.SILENT, 0, handler);
    }

    private void putFile(final Handler<Resource> handler, final String fullPath) {
        final String tmpFilePathAbs = fullPath + "." + UUID.randomUUID().toString();
        final String tmpFilePath = tmpFilePathAbs.substring(root.length());
        final FileSystem fileSystem = fileSystem();
        new Runnable(){
            @Override public void run() {
                fileSystem.open(tmpFilePathAbs, new OpenOptions(), this::onTmpFileOpen );
            }
            private void onTmpFileOpen( AsyncResult<AsyncFile> tmpFileOpenEvent ) {
                if (tmpFileOpenEvent.succeeded()) {
                    final AsyncFile tmpFile = tmpFileOpenEvent.result();
                    final DocumentResource d = new DocumentResource();
                    d.writeStream = tmpFile;
                    d.closeHandler = v -> {
                        tmpFile.close( ev -> {
                            onResourceClose(d);
                        });
                    };
                    d.addErrorHandler( err -> onResourceError(err,tmpFile) );
                    handler.handle(d);
                } else {
                    Resource r = new Resource();
                    r.exists = false;
                    handler.handle(r);
                }
            }
            private void onResourceClose( DocumentResource d ) {
                // Delete obsolete file which was there before.
                fileSystem.delete(fullPath, event3 -> {
                    // Move/rename our temporary file to its final destination.
                    fileSystem.move(tmpFilePathAbs, fullPath, event4 -> {
                        log.debug( "File stored successfully: {}", fullPath );
                        d.endHandler.handle(null);
                    });
                });
            }
            private void onResourceError( Throwable exc , AsyncFile tmpFile ) {
                log.error( "Put file failed:" , exc );
                tmpFile.close( voidCloseEvent -> {
                    log.debug( "Tmp file '{}' closed." , tmpFilePathAbs);
                    delete(tmpFilePath, null, null, 0, false, true, voidDeleteEvent -> {
                        log.debug("Tmp file '{}' deleted.", tmpFilePathAbs);
                    });
                });
            }
        }.run();
    }

    @Override
    public void delete(String path, String lockOwner, LockMode lockMode, long lockExpire, boolean confirmCollectionDelete,
                       boolean deleteRecursive, final Handler<Resource> handler ) {
        final String fullPath = canonicalize(path);

        boolean deleteRecursiveInFileSystem = true;
        if(confirmCollectionDelete && !deleteRecursive){
            deleteRecursiveInFileSystem = false;
        }
        boolean finalDeleteRecursiveInFileSystem = deleteRecursiveInFileSystem;

        fileSystem().exists(fullPath, event -> {
            if (event.result()) {
                fileSystem().deleteRecursive(fullPath, finalDeleteRecursiveInFileSystem, event1 -> {
                    Resource resource = new Resource();
                    if (event1.failed()) {
                        if(event1.cause().getCause() != null && event1.cause().getCause() instanceof DirectoryNotEmptyException){
                            resource.error = true;
                            resource.errorMessage = "directory not empty. Use recursive=true parameter to delete";
                        } else {
                            resource.exists = false;
                        }
                    }else{
                        deleteEmptyParentDirs(new File(path).getParent());
                    }
                    handler.handle(resource);
                });
            } else {
                Resource r = new Resource();
                r.exists = false;
                handler.handle(r);
            }
        });
    }

    /**
     * Deletes all empty parent directories starting at specified directory.
     *
     * @param path
     *      Most deep (virtual) directory to start bubbling up deletion of empty
     *      directories.
     */
    private void deleteEmptyParentDirs(String path) {
        final FileSystem fileSystem = fileSystem();
        final String pathAbs = canonicalize(path);

        // Analyze if we reached root.
        int pathLen;
        // Evaluate length of current path excluding trailing slashes by searching
        // last non-slash (backslash of course on windows).
        for( pathLen=pathAbs.length()-1 ; pathAbs.charAt(pathLen) == File.separatorChar ; --pathLen );
        if( rootLen == pathLen ){
            // We do NOT want to delete our virtual root even it is empty :)
            log.debug( "Stop deletion here to keep virtual root '{}'.", root );
            return;
        }

        log.debug( "Delete directory if empty '{}'.", pathAbs);
        fileSystem.delete( pathAbs , result -> {
            if( result.succeeded() ){
                // Bubbling up to parent.
                final String parentPath = new File(path).getParent();
                // HINT 1: We go recursive here!
                // HINT 2: When debugging stack traces keep in mind this recursion occurs
                //         asynchronous and therefore is not really a recursion :)
                deleteEmptyParentDirs( parentPath );
            }else{
                final Throwable cause = result.cause();
                if(cause instanceof FileSystemException && cause.getCause() instanceof DirectoryNotEmptyException){
                    // Failed to delete directory because it's not empty. Therefore we must not
                    // delete it at all and we're done now.
                    log.debug( "Directory '"+pathAbs+"' not empty. Stop bubbling deleting dirs." );
                }else if(cause instanceof FileSystemException && cause.getCause() instanceof NoSuchFileException){
                    // Somehow a caller requested to delete a directory which seems not to exist.
                    // This should never be the case theoretically. (except maybe some race
                    // conditions?)
                    log.warn( "Ignored to delete non-existing dir '{}'.", pathAbs );
                }else{
                    // This case should not happen. At least up to now i've no idea of a valid
                    // scenario for this one.
                    log.error("Unexpected error while deleting empty directories." , cause);
                }
            }
        });
    }

    private String canonicalize(String path) {
        try {
            return new File(root + path).getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String dirName(String path) {
        return new File(path).getParent();
    }

    private FileSystem fileSystem() {
        return vertx.fileSystem();
    }

    @Override
    public void cleanup(Handler<DocumentResource> handler, String cleanupResourcesAmount) {
        // nothing to do here
    }

    @Override
    public void storageExpand(String path, String etag, List<String> subResources, Handler<Resource> handler) {
        throw new UnsupportedOperationException("Method 'storageExpand' is not yet implemented for the FileSystemStorage");
    }
}
