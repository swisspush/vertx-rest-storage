package org.swisspush.reststorage;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.swisspush.reststorage.util.LockMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.util.*;

public class FileSystemStorage implements Storage {

    private String root;
    private Vertx vertx;

    private Logger log = LoggerFactory.getLogger(FileSystemStorage.class);

    public FileSystemStorage(Vertx vertx, String root) {
        this.vertx = vertx;
        this.root = root;
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
        final String tempFile = fullPath + "." + UUID.randomUUID().toString();
        fileSystem().open(tempFile, new OpenOptions(), event -> {
            if (event.succeeded()) {
                final DocumentResource d = new DocumentResource();
                d.writeStream = event.result();
                d.closeHandler = v -> event.result().close(event2 -> fileSystem().delete(fullPath, event3 -> fileSystem().move(tempFile, fullPath, event4 -> d.endHandler.handle(null))));
                handler.handle(d);
            } else {
                Resource r = new Resource();
                r.exists = false;
                handler.handle(r);
            }
        });
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
