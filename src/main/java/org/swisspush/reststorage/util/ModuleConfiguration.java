package org.swisspush.reststorage.util;

import io.vertx.core.json.JsonObject;

/**
 * Utility class to configure the RestStorageModule.
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
public class ModuleConfiguration {

    private String root;
    private StorageType storageType;
    private int port;
    private String prefix;
    private String storageAddress;
    private JsonObject editorConfig;
    private String redisHost;
    private int redisPort;
    private String expirablePrefix;
    private String resourcesPrefix;
    private String collectionsPrefix;
    private String deltaResourcesPrefix;
    private String deltaEtagsPrefix;
    private long resourceCleanupAmount;
    private String lockPrefix;
    private boolean confirmCollectionDelete;
    private boolean rejectStorageWriteOnLowMemory;
    private long freeMemoryCheckIntervalMs;
    private PathProcessingStrategy pathProcessingStrategy;

    public static final String PROP_ROOT = "root";
    public static final String PROP_STORAGE_TYPE = "storageType";
    public static final String PROP_PORT = "port";
    public static final String PROP_PREFIX = "prefix";
    public static final String PROP_STORAGE_ADDRESS = "storageAddress";
    public static final String PROP_EDITOR_CONFIG = "editorConfig";
    public static final String PROP_REDIS_HOST = "redisHost";
    public static final String PROP_REDIS_PORT = "redisPort";
    public static final String PROP_EXP_PREFIX = "expirablePrefix";
    public static final String PROP_RES_PREFIX = "resourcesPrefix";
    public static final String PROP_COL_PREFIX = "collectionsPrefix";
    public static final String PROP_DELTA_RES_PREFIX = "deltaResourcesPrefix";
    public static final String PROP_DELTA_ETAGS_PREFIX = "deltaEtagsPrefix";
    public static final String PROP_RES_CLEANUP_AMMOUNT = "resourceCleanupAmount";
    public static final String PROP_LOCK_PREFIX = "lockPrefix";
    public static final String PROP_CONFIRM_COLLECTIONDELETE = "confirmCollectionDelete";
    public static final String PROP_REJECT_ON_LOW_MEMORY_ENABLED = "rejectStorageWriteOnLowMemory";
    public static final String PROP_FREE_MEMORY_CHECK_INTERVAL = "freeMemoryCheckIntervalMs";
    public static final String PROP_PATH_PROCESSING_STRATEGY = "pathProcessingStrategy";

    public enum StorageType {
        filesystem, redis
    }

    public enum PathProcessingStrategy {
        unmodified, cleaned;

        /**
         * Returns the enum PathProcessingStrategy which matches the specified String value.
         *
         * @param strategyStr The strategy as String
         * @return The matching PathProcessingStrategy or null if none matches.
         */
        public static PathProcessingStrategy fromString(String strategyStr) {
            for (PathProcessingStrategy strategy : values()) {
                if (strategy.name().equalsIgnoreCase(strategyStr)) {
                    return strategy;
                }
            }
            return null;
        }
    }

    /**
     * Constructor with default values. Use the {@link org.swisspush.reststorage.util.ModuleConfiguration.ModuleConfigurationBuilder} class
     * for simplyfied custom configuration.
     */
    public ModuleConfiguration(){
        this(new ModuleConfigurationBuilder());
    }

    public ModuleConfiguration(String root, StorageType storageType, int port, String prefix, String storageAddress,
                               JsonObject editorConfig, String redisHost, int redisPort, String expirablePrefix,
                               String resourcesPrefix, String collectionsPrefix, String deltaResourcesPrefix,
                               String deltaEtagsPrefix, long resourceCleanupAmount, String lockPrefix,
                               boolean confirmCollectionDelete, boolean rejectStorageWriteOnLowMemory,
                               long freeMemoryCheckIntervalMs, PathProcessingStrategy pathProcessingStrategy) {
        this.root = root;
        this.storageType = storageType;
        this.port = port;
        this.prefix = prefix;
        this.storageAddress = storageAddress;
        this.editorConfig = editorConfig;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.expirablePrefix = expirablePrefix;
        this.resourcesPrefix = resourcesPrefix;
        this.collectionsPrefix = collectionsPrefix;
        this.deltaResourcesPrefix = deltaResourcesPrefix;
        this.deltaEtagsPrefix = deltaEtagsPrefix;
        this.resourceCleanupAmount = resourceCleanupAmount;
        this.lockPrefix = lockPrefix;
        this.confirmCollectionDelete = confirmCollectionDelete;
        this.rejectStorageWriteOnLowMemory = rejectStorageWriteOnLowMemory;
        this.freeMemoryCheckIntervalMs = freeMemoryCheckIntervalMs;
        this.pathProcessingStrategy = pathProcessingStrategy;
    }

    public static ModuleConfigurationBuilder with(){
        return new ModuleConfigurationBuilder();
    }

    private ModuleConfiguration(ModuleConfigurationBuilder builder){
        this(builder.root, builder.storageType, builder.port, builder.prefix, builder.storageAddress, builder.editorConfig,
                builder.redisHost, builder.redisPort, builder.expirablePrefix, builder.resourcesPrefix, builder.collectionsPrefix,
                builder.deltaResourcesPrefix, builder.deltaEtagsPrefix, builder.resourceCleanupAmount, builder.lockPrefix,
                builder.confirmCollectionDelete, builder.rejectStorageWriteOnLowMemory, builder.freeMemoryCheckIntervalMs, builder.pathProcessingStrategy);
    }

    public JsonObject asJsonObject(){
        JsonObject obj = new JsonObject();
        obj.put(PROP_ROOT, getRoot());
        obj.put(PROP_STORAGE_TYPE, getStorageType().name());
        obj.put(PROP_PORT, getPort());
        obj.put(PROP_PREFIX, getPrefix());
        obj.put(PROP_STORAGE_ADDRESS, getStorageAddress());
        obj.put(PROP_EDITOR_CONFIG, getEditorConfig());
        obj.put(PROP_REDIS_HOST, getRedisHost());
        obj.put(PROP_REDIS_PORT, getRedisPort());
        obj.put(PROP_EXP_PREFIX, getExpirablePrefix());
        obj.put(PROP_RES_PREFIX, getResourcesPrefix());
        obj.put(PROP_COL_PREFIX, getCollectionsPrefix());
        obj.put(PROP_DELTA_RES_PREFIX, getDeltaResourcesPrefix());
        obj.put(PROP_DELTA_ETAGS_PREFIX, getDeltaEtagsPrefix());
        obj.put(PROP_RES_CLEANUP_AMMOUNT, getResourceCleanupAmount());
        obj.put(PROP_LOCK_PREFIX, getLockPrefix());
        obj.put(PROP_CONFIRM_COLLECTIONDELETE, isConfirmCollectionDelete());
        obj.put(PROP_REJECT_ON_LOW_MEMORY_ENABLED, isRejectStorageWriteOnLowMemory());
        obj.put(PROP_FREE_MEMORY_CHECK_INTERVAL, getFreeMemoryCheckIntervalMs());
        obj.put(PROP_PATH_PROCESSING_STRATEGY, getPathProcessingStrategy().name());
        return obj;
    }

    public static ModuleConfiguration fromJsonObject(JsonObject json){
        ModuleConfigurationBuilder builder = ModuleConfiguration.with();
        if(json.containsKey(PROP_ROOT)){
            builder.root(json.getString(PROP_ROOT));
        }
        if(json.containsKey(PROP_STORAGE_TYPE)){
            builder.storageTypeFromString(json.getString(PROP_STORAGE_TYPE));
        }
        if(json.containsKey(PROP_PORT)){
            builder.port(json.getInteger(PROP_PORT));
        }
        if(json.containsKey(PROP_PREFIX)){
            builder.prefix(json.getString(PROP_PREFIX));
        }
        if(json.containsKey(PROP_STORAGE_ADDRESS)){
            builder.storageAddress(json.getString(PROP_STORAGE_ADDRESS));
        }
        if(json.containsKey(PROP_EDITOR_CONFIG)){
            builder.editorConfig(json.getJsonObject(PROP_EDITOR_CONFIG));
        }
        if(json.containsKey(PROP_REDIS_HOST)){
            builder.redisHost(json.getString(PROP_REDIS_HOST));
        }
        if(json.containsKey(PROP_REDIS_PORT)){
            builder.redisPort(json.getInteger(PROP_REDIS_PORT));
        }
        if(json.containsKey(PROP_EXP_PREFIX)){
            builder.expirablePrefix(json.getString(PROP_EXP_PREFIX));
        }
        if(json.containsKey(PROP_RES_PREFIX)){
            builder.resourcesPrefix(json.getString(PROP_RES_PREFIX));
        }
        if(json.containsKey(PROP_COL_PREFIX)){
            builder.collectionsPrefix(json.getString(PROP_COL_PREFIX));
        }
        if(json.containsKey(PROP_DELTA_RES_PREFIX)){
            builder.deltaResourcesPrefix(json.getString(PROP_DELTA_RES_PREFIX));
        }
        if(json.containsKey(PROP_DELTA_ETAGS_PREFIX)){
            builder.deltaEtagsPrefix(json.getString(PROP_DELTA_ETAGS_PREFIX));
        }
        if(json.containsKey(PROP_RES_CLEANUP_AMMOUNT)){
            builder.resourceCleanupAmount(json.getLong(PROP_RES_CLEANUP_AMMOUNT));
        }
        if(json.containsKey(PROP_LOCK_PREFIX)) {
            builder.lockPrefix(json.getString(PROP_LOCK_PREFIX));
        }
        if(json.containsKey(PROP_CONFIRM_COLLECTIONDELETE)){
            builder.confirmCollectionDelete(json.getBoolean(PROP_CONFIRM_COLLECTIONDELETE));
        }
        if(json.containsKey(PROP_REJECT_ON_LOW_MEMORY_ENABLED)){
            builder.rejectStorageWriteOnLowMemory(json.getBoolean(PROP_REJECT_ON_LOW_MEMORY_ENABLED));
        }
        if(json.containsKey(PROP_FREE_MEMORY_CHECK_INTERVAL)){
            builder.freeMemoryCheckIntervalMs(json.getLong(PROP_FREE_MEMORY_CHECK_INTERVAL));
        }
        if(json.containsKey(PROP_PATH_PROCESSING_STRATEGY)){
            builder.pathProcessingStrategyFromString(json.getString(PROP_PATH_PROCESSING_STRATEGY));
        }
        return builder.build();
    }

    public String getRoot() {
        return root;
    }

    public StorageType getStorageType() {return storageType; }

    public int getPort() {
        return port;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getStorageAddress() {
        return storageAddress;
    }

    public JsonObject getEditorConfig() {
        return editorConfig;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public String getExpirablePrefix() {
        return expirablePrefix;
    }

    public String getResourcesPrefix() {
        return resourcesPrefix;
    }

    public String getCollectionsPrefix() {
        return collectionsPrefix;
    }

    public String getDeltaResourcesPrefix() {
        return deltaResourcesPrefix;
    }

    public String getDeltaEtagsPrefix() {
        return deltaEtagsPrefix;
    }

    public long getResourceCleanupAmount() {
        return resourceCleanupAmount;
    }

    public String getLockPrefix() { return lockPrefix; }

    public boolean isConfirmCollectionDelete() { return confirmCollectionDelete; }

    public boolean isRejectStorageWriteOnLowMemory() { return rejectStorageWriteOnLowMemory; }

    public long getFreeMemoryCheckIntervalMs() { return freeMemoryCheckIntervalMs; }

    public PathProcessingStrategy getPathProcessingStrategy() { return pathProcessingStrategy; }

    @Override
    public String toString() {
        return asJsonObject().toString();
    }

    /**
     * ModuleConfigurationBuilder class for simplyfied configuration.
     *
     * <pre>Usage:</pre>
     * <pre>
     * ModuleConfiguration config = with()
     *      .redisHost("anotherhost")
     *      .redisPort(1234)
     *      .editorConfig(new JsonObject().put("myKey", "myValue"))
     *      .build();
     * </pre>
     */
    public static class ModuleConfigurationBuilder {
        private String root;
        private StorageType storageType;
        private int port;
        private String prefix;
        private String storageAddress;
        private JsonObject editorConfig;
        private String redisHost;
        private int redisPort;
        private String expirablePrefix;
        private String resourcesPrefix;
        private String collectionsPrefix;
        private String deltaResourcesPrefix;
        private String deltaEtagsPrefix;
        private long resourceCleanupAmount;
        private String lockPrefix;
        private boolean confirmCollectionDelete;
        private boolean rejectStorageWriteOnLowMemory;
        private long freeMemoryCheckIntervalMs;
        private PathProcessingStrategy pathProcessingStrategy;

        private static final long DEFAULT_FREE_MEMORY_CHECK_INTERVAL = 60000; // 60s
        private static final PathProcessingStrategy DEFAULT_PATH_PROCESSING_STRATEGY = PathProcessingStrategy.cleaned;

        public ModuleConfigurationBuilder(){
            this.root = ".";
            this.storageType = StorageType.filesystem;
            this.port = 8989;
            this.prefix = "";
            this.storageAddress = "resource-storage";
            this.editorConfig = null;
            this.redisHost = "localhost";
            this.redisPort = 6379;
            this.expirablePrefix = "rest-storage:expirable";
            this.resourcesPrefix = "rest-storage:resources";
            this.collectionsPrefix = "rest-storage:collections";
            this.deltaResourcesPrefix = "delta:resources";
            this.deltaEtagsPrefix = "delta:etags";
            this.resourceCleanupAmount = 100000L;
            this.lockPrefix = "rest-storage:locks";
            this.confirmCollectionDelete = false;
            this.rejectStorageWriteOnLowMemory = false;
            this.freeMemoryCheckIntervalMs = DEFAULT_FREE_MEMORY_CHECK_INTERVAL;
            this.pathProcessingStrategy = DEFAULT_PATH_PROCESSING_STRATEGY;
        }

        public ModuleConfigurationBuilder root(String root){
            this.root = root;
            return this;
        }

        public ModuleConfigurationBuilder storageType(StorageType storageType){
            this.storageType = storageType;
            return this;
        }

        public ModuleConfigurationBuilder storageTypeFromString(String storageType){
            for(StorageType type : StorageType.values()){
                if (type.name().equalsIgnoreCase(storageType)){
                    this.storageType = type;
                }
            }
            if(this.storageType == null) {
                this.storageType = StorageType.filesystem;
            }
            return this;
        }

        public ModuleConfigurationBuilder port(int port){
            this.port = port;
            return this;
        }

        public ModuleConfigurationBuilder prefix(String prefix){
            this.prefix = prefix;
            return this;
        }

        public ModuleConfigurationBuilder storageAddress(String storageAddress){
            this.storageAddress = storageAddress;
            return this;
        }

        public ModuleConfigurationBuilder editorConfig(JsonObject editorConfig){
            this.editorConfig = editorConfig;
            return this;
        }

        public ModuleConfigurationBuilder redisHost(String redisHost){
            this.redisHost = redisHost;
            return this;
        }

        public ModuleConfigurationBuilder redisPort(int redisPort){
            this.redisPort = redisPort;
            return this;
        }

        public ModuleConfigurationBuilder expirablePrefix(String expirablePrefix){
            this.expirablePrefix = expirablePrefix;
            return this;
        }

        public ModuleConfigurationBuilder resourcesPrefix(String resourcesPrefix){
            this.resourcesPrefix = resourcesPrefix;
            return this;
        }

        public ModuleConfigurationBuilder collectionsPrefix(String collectionsPrefix){
            this.collectionsPrefix = collectionsPrefix;
            return this;
        }

        public ModuleConfigurationBuilder deltaResourcesPrefix(String deltaResourcesPrefix){
            this.deltaResourcesPrefix = deltaResourcesPrefix;
            return this;
        }

        public ModuleConfigurationBuilder deltaEtagsPrefix(String deltaEtagsPrefix){
            this.deltaEtagsPrefix = deltaEtagsPrefix;
            return this;
        }

        public ModuleConfigurationBuilder resourceCleanupAmount(long resourceCleanupAmount){
            this.resourceCleanupAmount = resourceCleanupAmount;
            return this;
        }

        public ModuleConfigurationBuilder lockPrefix(String lockPrefix) {
            this.lockPrefix = lockPrefix;
            return this;
        }

        public ModuleConfigurationBuilder confirmCollectionDelete(boolean confirmCollectionDelete){
            this.confirmCollectionDelete = confirmCollectionDelete;
            return this;
        }

        public ModuleConfigurationBuilder rejectStorageWriteOnLowMemory(boolean rejectStorageWriteOnLowMemory){
            this.rejectStorageWriteOnLowMemory = rejectStorageWriteOnLowMemory;
            return this;
        }

        public ModuleConfigurationBuilder freeMemoryCheckIntervalMs(long freeMemoryCheckIntervalMs) {
            this.freeMemoryCheckIntervalMs = freeMemoryCheckIntervalMs;
            return this;
        }

        public ModuleConfigurationBuilder pathProcessingStrategy(PathProcessingStrategy pathProcessingStrategy){
            this.pathProcessingStrategy = pathProcessingStrategy;
            return this;
        }

        public ModuleConfigurationBuilder pathProcessingStrategyFromString(String pathProcessingStrategy){
            for(PathProcessingStrategy strategy : PathProcessingStrategy.values()){
                if (strategy.name().equalsIgnoreCase(pathProcessingStrategy)){
                    this.pathProcessingStrategy = strategy;
                }
            }
            if(this.pathProcessingStrategy == null) {
                this.pathProcessingStrategy = DEFAULT_PATH_PROCESSING_STRATEGY;
            }
            return this;
        }

        public ModuleConfiguration build(){
            return new ModuleConfiguration(this);
        }
    }
}