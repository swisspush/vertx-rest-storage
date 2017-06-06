package org.swisspush.reststorage;

import io.vertx.core.Handler;
import org.swisspush.reststorage.util.LockMode;

import java.util.List;

public interface Storage {

    /**
     * Gets the percentage of the actual memory usage. Possible values are in range 0.0 to 100.0
     *
     * @return the percentage of the actual memory usage
     */
    float getMemoryUsage();

    void get(String path, String etag, int offset, int count, Handler<Resource> handler);

    void storageExpand(String path, String etag, List<String> subResources, Handler<Resource> handler);

    void put(String path, String etag, boolean merge, long expire, Handler<Resource> handler);

    void put(String path, String etag, boolean merge, long expire, String lockOwner, LockMode lockMode, long lockExpire, Handler<Resource> handler);

    void put(String path, String etag, boolean merge, long expire, String lockOwner, LockMode lockMode, long lockExpire, boolean storeCompressed, Handler<Resource> handler);

    void delete(String path, String lockOwner, LockMode lockMode, long lockExpire, boolean confirmCollectionDelete, boolean deleteRecursive, Handler<Resource> handler);

    void cleanup(Handler<DocumentResource> handler, String cleanupResourcesAmount);

}
