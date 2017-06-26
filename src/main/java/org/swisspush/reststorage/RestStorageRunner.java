package org.swisspush.reststorage;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import org.swisspush.reststorage.util.ModuleConfiguration;

/**
 * Created by florian kammermann on 23.05.2016.
 *
 * Deploys the rest-storage to vert.x.
 * Used in the standalone scenario.
 */
public class RestStorageRunner {

    public static void main(String[] args) {

        JsonObject mainStorageConfig = ModuleConfiguration.with()
                .storageType(ModuleConfiguration.StorageType.redis)
                .build()
                .asJsonObject();

        DeploymentOptions storageOptions = new DeploymentOptions().setConfig(mainStorageConfig).setInstances(4);

        Vertx.vertx().deployVerticle("org.swisspush.reststorage.RestStorageMod", storageOptions, event -> {
            LoggerFactory.getLogger(RestStorageMod.class).info("rest-storage started");
        });
    }
}