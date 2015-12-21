package li.chee.vertx.reststorage;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

import java.io.*;
import java.util.*;

public class RedisStorage implements Storage {

    // set to very high value = Sat Nov 20 2286 17:46:39
    private static final String MAX_EXPIRE_IN_MILLIS = "9999999999999";

    private static final int CLEANUP_BULK_SIZE = 200;

    private String redisAddress;
    private String redisResourcesPrefix;
    private String redisCollectionsPrefix;
    private String redisDeltaResourcesPrefix;
    private String redisDeltaEtagsPrefix;
    private String expirableSet;
    private long cleanupResourcesAmount;
    private EventBus eb;
    private Vertx vertx;
    private Logger log;
    private Map<LuaScript,LuaScriptState> luaScripts = new HashMap<>();

    public RedisStorage(Vertx vertx, Logger log, String redisAddress, String redisResourcesPrefix, String redisCollectionsPrefix, String redisDeltaResourcesPrefix, String redisDeltaEtagsPrefix, String expirableSet, long cleanupResourcesAmount) {
        this.redisAddress = redisAddress;
        this.expirableSet = expirableSet;
        this.redisResourcesPrefix = redisResourcesPrefix;
        this.redisCollectionsPrefix = redisCollectionsPrefix;
        this.redisDeltaResourcesPrefix = redisDeltaResourcesPrefix;
        this.redisDeltaEtagsPrefix = redisDeltaEtagsPrefix;
        this.cleanupResourcesAmount = cleanupResourcesAmount;
        eb = vertx.eventBus();
        this.vertx = vertx;
        this.log = log;

        // load all the lua scripts
        LuaScriptState luaGetScriptState = new LuaScriptState(LuaScript.GET, false);
        luaGetScriptState.loadLuaScript(new RedisCommandDoNothing(), 0);
        luaScripts.put(LuaScript.GET, luaGetScriptState);

        LuaScriptState luaGetExpandScriptState = new LuaScriptState(LuaScript.GET_EXPAND, false);
        luaGetExpandScriptState.loadLuaScript(new RedisCommandDoNothing(), 0);
        luaScripts.put(LuaScript.GET_EXPAND, luaGetExpandScriptState);

        LuaScriptState luaPutScriptState = new LuaScriptState(LuaScript.PUT, false);
        luaPutScriptState.loadLuaScript(new RedisCommandDoNothing(), 0);
        luaScripts.put(LuaScript.PUT, luaPutScriptState);

        LuaScriptState luaDeleteScriptState = new LuaScriptState(LuaScript.DELETE, false);
        luaDeleteScriptState.loadLuaScript(new RedisCommandDoNothing(), 0);
        luaScripts.put(LuaScript.DELETE, luaDeleteScriptState);

        LuaScriptState luaCleanupScriptState = new LuaScriptState(LuaScript.CLEANUP, false);
        luaCleanupScriptState.loadLuaScript(new RedisCommandDoNothing(), 0);
        luaScripts.put(LuaScript.CLEANUP, luaCleanupScriptState);
    }

    private enum LuaScript {
        GET("get.lua"), GET_EXPAND("getExpand.lua"), PUT("put.lua"), DELETE("del.lua"), CLEANUP("cleanup.lua");

        private String file;

        private LuaScript(String file) {
            this.file = file;
        }

        public String getFile() {
            return file;
        }
    }

    /**
     * Holds the state of a lua script.
     */
    private class LuaScriptState {

        private LuaScript luaScriptType;
        /** the script itself */
        private String script;
        /** if the script logs to the redis log */
        private boolean logoutput = false;
        /** the sha, over which the script can be accessed in redis */
        private String sha;

        private LuaScriptState(LuaScript luaScriptType, boolean logoutput) {
            this.luaScriptType = luaScriptType;
            this.logoutput = logoutput;
            this.composeLuaScript(luaScriptType);
            this.loadLuaScript(new RedisCommandDoNothing(), 0);
        }

        /**
         * Reads the script from the classpath and removes logging output if logoutput is false.
         * The script is stored in the class member script.
         * @param luaScriptType
         */
        private void composeLuaScript(LuaScript luaScriptType) {
            log.info("read the lua script for script type: " + luaScriptType + " with logoutput: " + logoutput);

            // It is not possible to evalsha or eval inside lua scripts,
            // so we wrap the cleanupscript around the deletescript manually to avoid code duplication.
            // we have to comment the return, so that the cleanup script doesn't terminate
            if(LuaScript.CLEANUP.equals(luaScriptType)) {
                Map<String, String> values = new HashMap<String, String>();
                values.put("delscript", readLuaScriptFromClasspath(LuaScript.DELETE).replaceAll("return", "--return"));
                StrSubstitutor sub = new StrSubstitutor(values, "--%(", ")");
                this.script = sub.replace(readLuaScriptFromClasspath(LuaScript.CLEANUP));
            } else {
                this.script = readLuaScriptFromClasspath(luaScriptType);
            }
            this.sha = DigestUtils.sha1Hex(this.script);
        }

        private String readLuaScriptFromClasspath(LuaScript luaScriptType) {
            BufferedReader in = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(luaScriptType.getFile())));
            StringBuilder sb;
            try {
                sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (!logoutput && line.contains("redis.log(redis.LOG_NOTICE,")) {
                        continue;
                    }
                    sb.append(line + "\n");
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            return sb.toString();
        }

        /**
         * Rereads the lua script, eg. if the loglevel changed.
         */
        public void recomposeLuaScript() {
            this.composeLuaScript(luaScriptType);
        }

        /**
         * Load the get script into redis and store the sha in the class member sha.
         * @param redisCommand the redis command that should be executed, after the script is loaded.
         * @param executionCounter a counter to control recursion depth
         */
        public void loadLuaScript(final RedisCommand redisCommand, int executionCounter) {

            final int executionCounterIncr = ++executionCounter;

            JsonObject scriptExistsCommand = new JsonObject();
            scriptExistsCommand.putString("command", "script exists");
            JsonArray argsExistsCommand = new JsonArray();
            argsExistsCommand.add(this.sha);
            scriptExistsCommand.putArray("args", argsExistsCommand);
            // check first if the lua script already exists in the store
            eb.send(redisAddress, scriptExistsCommand, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    Long exists = event.body().getArray("value").get(0);
                    // if script already
                    if(Long.valueOf(1).equals(exists)) {
                        log.debug("RedisStorage script already exists in redis cache: " + luaScriptType);
                        redisCommand.exec(executionCounterIncr);
                    } else {
                        log.info("load lua script for script type: " + luaScriptType + " logutput: " + logoutput);
                        JsonObject loadCommand = new JsonObject();
                        loadCommand.putString("command", "script load");
                        JsonArray args = new JsonArray();
                        args.add(script);
                        loadCommand.putArray("args", args);
                        eb.send(redisAddress, loadCommand, new Handler<Message<JsonObject>>() {
                            public void handle(Message<JsonObject> event) {
                                String newSha = event.body().getString("value");
                                log.info("got sha from redis for lua script: " + luaScriptType + ": " + newSha);
                                if(!newSha.equals(sha)) {
                                    log.warn("the sha calculated by myself: " + sha + " doesn't match with the sha from redis: " + newSha + ". We use the sha from redis");
                                }
                                sha = newSha;
                                log.info("execute redis command for script type: " + luaScriptType + " with new sha: " + sha);
                                redisCommand.exec(executionCounterIncr);
                            }
                        });
                    }
                }
            });
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public boolean getLogoutput() {
            return logoutput;
        }

        public void setLogoutput(boolean logoutput) {
            this.logoutput = logoutput;
        }

        public String getSha() {
            return sha;
        }

        public void setSha(String sha) {
            this.sha = sha;
        }
    }

    /**
     * The interface for a redis command.
     */
    private interface RedisCommand {
        void exec(int executionCounter);
    }

    /**
     * A dummy that can be passed if no RedisCommand should be executed.
     */
    private class RedisCommandDoNothing implements RedisCommand {

        @Override public void exec(int executionCounter) {
            // do nothing here
        }
    }

    /**
     * If the loglevel is trace and the logoutput in luaScriptState is false, then reload the script with logoutput and execute the RedisCommand.
     * If the loglevel is not trace and the logoutput in luaScriptState is true, then reload the script without logoutput and execute the RedisCommand.
     * If the loglevel is matching the luaScriptState, just execute the RedisCommand.
     *
     * @param luaScript the type of lua script
     * @param redisCommand the redis command to execute
     */
    private void reloadScriptIfLoglevelChangedAndExecuteRedisCommand(LuaScript luaScript, RedisCommand redisCommand, int executionCounter) {
        boolean logoutput = log.isTraceEnabled() ? true : false;
        LuaScriptState luaScriptState = luaScripts.get(luaScript);
        // if the loglevel didn't change, execute the command and return
        if(logoutput == luaScriptState.getLogoutput()) {
            redisCommand.exec(executionCounter);
            return;
            // if the loglevel changed, set the new loglevel into the luaScriptState, recompose the script and provide the redisCommand as parameter to execute
        } else if(logoutput && ! luaScriptState.getLogoutput()) {
            luaScriptState.setLogoutput(true);
            luaScriptState.recomposeLuaScript();

        } else if(! logoutput && luaScriptState.getLogoutput()) {
            luaScriptState.setLogoutput(false);
            luaScriptState.recomposeLuaScript();
        }
        luaScriptState.loadLuaScript(redisCommand, executionCounter);
    }

    public class ByteArrayReadStream implements ReadStream<ByteArrayReadStream> {

        ByteArrayInputStream content;
        int size;
        boolean paused;
        int position;
        Handler<Void> endHandler;
        Handler<Buffer> dataHandler;

        public ByteArrayReadStream(byte[] byteArray) {
            size = byteArray.length;
            content = new ByteArrayInputStream(byteArray);
        }

        private void doRead() {
            vertx.runOnContext(new Handler<Void>() {
                public void handle(Void v) {
                    if (!paused) {
                        if (position < size) {
                            int toRead = 8192;
                            if (position + toRead > size) {
                                toRead = size - position;
                            }
                            byte[] bytes = new byte[toRead];
                            content.read(bytes, 0, toRead);
                            dataHandler.handle(new Buffer(bytes));
                            position += toRead;
                            doRead();
                        } else {
                            endHandler.handle(null);
                        }
                    }
                }
            });
        }

        public ByteArrayReadStream resume() {
            paused = false;
            doRead();
            return this;
        }

        @Override
        public ByteArrayReadStream pause() {
            paused = true;
            return this;
        }

        @Override
        public ByteArrayReadStream exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public ByteArrayReadStream endHandler(Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;

        }

        @Override
        public ByteArrayReadStream dataHandler(Handler<Buffer> handler) {
            this.dataHandler = handler;
            doRead();
            return this;
        }
    }

    @Override
    public void get(String path, String etag, int offset, int limit, final Handler<Resource> handler) {
        final String key = encodePath(path);
        final JsonObject command = new JsonObject();
        command.putString("command", "evalsha");
        JsonArray args = new JsonArray();
        args.add(luaScripts.get(LuaScript.GET).getSha());
        args.add(1);
        args.add(key);
        args.add(redisResourcesPrefix);
        args.add(redisCollectionsPrefix);
        args.add(expirableSet);
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(MAX_EXPIRE_IN_MILLIS);
        args.add(Integer.valueOf(offset));
        args.add(Integer.valueOf(limit));
        args.add(etag);
        command.putArray("args", args);
        reloadScriptIfLoglevelChangedAndExecuteRedisCommand(LuaScript.GET, new Get(command, handler), 0);
    }

    /**
     * The Get Command Execution.
     * If the get script cannot be found under the sha in luaScriptState, reload the script.
     * To avoid infinite recursion, we limit the recursion.
     */
    private class Get implements RedisCommand {

        private JsonObject command;
        private Handler<Resource> handler;

        public Get(JsonObject command, final Handler<Resource> handler) {
            this.command = command;
            this.handler = handler;
        }

        public void exec(final int executionCounter) {
            eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    Object value = event.body().getField("value");
                    if (log.isTraceEnabled()) {
                        log.trace("RedisStorage get result: " + value);
                    }
                    if("error".equals(event.body().getString("status"))) {
                        String message = event.body().getString("message");
                        if(message != null && message.startsWith("NOSCRIPT")) {
                            log.warn("get script couldn't be found, reload it");
                            log.warn("amount the script got loaded: " + String.valueOf(executionCounter));
                            if(executionCounter > 10) {
                                log.error("amount the script got loaded is higher than 10, we abort");
                            } else {
                                luaScripts.get(LuaScript.GET).loadLuaScript(new Get(command, handler), executionCounter);
                            }
                            return;
                        }
                    }
                    if("notModified".equals(value)){
                        notModified(handler);
                    } else if ("notFound".equals(value)) {
                        notFound(handler);
                    } else if (value instanceof JsonArray) {
                        JsonArray values = (JsonArray) value;
                        handleJsonArrayValues(values, handler);
                    }
                }
            });
        }
    }

    @Override
    public void getExpand(String path, String etag, List<String> subResources, Handler<Resource> handler) {
        final String key = encodePath(path);
        final JsonObject command = new JsonObject();
        command.putString("command", "evalsha");
        JsonArray args = new JsonArray();
        args.add(luaScripts.get(LuaScript.GET_EXPAND).getSha());
        args.add(1);
        args.add(key);
        args.add(redisResourcesPrefix);
        args.add(redisCollectionsPrefix);
        args.add(expirableSet);
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(MAX_EXPIRE_IN_MILLIS);
        args.add(StringUtils.join(subResources, ";"));
        args.add(subResources.size());
        command.putArray("args", args);

        reloadScriptIfLoglevelChangedAndExecuteRedisCommand(LuaScript.GET_EXPAND, new GetExpand(command, handler, etag), 0);
    }

    /**
     * The GetExpand Command Execution.
     * If the get script cannot be found under the sha in luaScriptState, reload the script.
     * To avoid infinite recursion, we limit the recursion.
     */
    private class GetExpand implements RedisCommand {

        private JsonObject command;
        private Handler<Resource> handler;
        private String etag;

        public GetExpand(JsonObject command, final Handler<Resource> handler, String etag) {
            this.command = command;
            this.handler = handler;
            this.etag = etag;
        }

        public void exec(final int executionCounter) {
            eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    Object value = event.body().getField("value");
                    if (log.isTraceEnabled()) {
                        log.trace("RedisStorage get result: " + value);
                    }
                    if("error".equals(event.body().getString("status"))) {
                        String message = event.body().getString("message");
                        if(message != null && message.startsWith("NOSCRIPT")) {
                            log.warn("getExpand script couldn't be found, reload it");
                            log.warn("amount the script got loaded: " + String.valueOf(executionCounter));
                            if(executionCounter > 10) {
                                log.error("amount the script got loaded is higher than 10, we abort");
                            } else {
                                luaScripts.get(LuaScript.GET_EXPAND).loadLuaScript(new GetExpand(command, handler, etag), executionCounter);
                            }
                            return;
                        }
                    }

                    if("notFound".equalsIgnoreCase((String) value)){
                        notFound(handler);
                        return;
                    }

                    JsonObject expandResult = new JsonObject();

                    JsonArray resultArr = new JsonArray((String) value);

                    for (Object resultEntry : resultArr) {
                        JsonArray entries = (JsonArray) resultEntry;
                        String subResourceName = entries.get(0);
                        String subResourceValue = entries.get(1);
                        if(subResourceValue.startsWith("[") && subResourceValue.endsWith("]")){
                            expandResult.putArray(subResourceName, new JsonArray(subResourceValue));
                        } else {
                            expandResult.putObject(subResourceName, new JsonObject(subResourceValue));
                        }
                    }

                    byte[] finalExpandedContent = decodeBinary(expandResult.encode());
                    String calcDigest = DigestUtils.sha1Hex(finalExpandedContent);

                    if(calcDigest.equals(etag)){
                        notModified(handler);
                    } else {
                        DocumentResource r = new DocumentResource();
                        r.readStream = new ByteArrayReadStream(finalExpandedContent);
                        r.length = finalExpandedContent.length;
                        r.etag = calcDigest;
                        r.closeHandler = new Handler<Void>() {
                            public void handle(Void event) {
                                // nothing to close
                            }
                        };
                        handler.handle(r);
                    }
                }
            });
        }
    }

    private void handleJsonArrayValues(JsonArray values, Handler<Resource> handler){
        String type = values.get(0);
        if("TYPE_RESOURCE".equals(type)){
            String valueStr = values.get(1);
            DocumentResource r = new DocumentResource();
            byte[] content = decodeBinary(valueStr);
            r.readStream = new ByteArrayReadStream(content);
            r.length = content.length;
            r.etag = values.get(2);
            r.closeHandler = new Handler<Void>() {
                public void handle(Void event) {
                    // nothing to close
                }
            };
            handler.handle(r);
        } else if("TYPE_COLLECTION".equals(type)) {
            CollectionResource r = new CollectionResource();
            Set<Resource> items = new HashSet<>();
            Iterator<Object> iterator = values.iterator();
            while (iterator.hasNext()) {
                String member = (String) iterator.next();
                if(!"TYPE_COLLECTION".equals(member)) {
                    if (member.endsWith(":")) {
                        member = member.replaceAll(":$", "");
                        CollectionResource c = new CollectionResource();
                        c.name = member;
                        items.add(c);
                    } else {
                        DocumentResource d = new DocumentResource();
                        d.name = member;
                        items.add(d);
                    }
                }
            }
            r.items = new ArrayList<Resource>(items);
            Collections.sort(r.items);
            handler.handle(r);
        } else {
            notFound(handler);
        }
    }

    class ByteArrayWriteStream implements WriteStream<ByteArrayWriteStream> {

        private ByteArrayOutputStream bos = new ByteArrayOutputStream();

        public byte[] getBytes() {
            return bos.toByteArray();
        }

        @Override
        public ByteArrayWriteStream write(Buffer data) {
            try {
                bos.write(data.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        @Override
        public ByteArrayWriteStream setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public ByteArrayWriteStream drainHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public ByteArrayWriteStream exceptionHandler(Handler<Throwable> handler) {
            return this;
        }
    }

    private String initEtagValue(String providedEtag){
        if(!isEmpty(providedEtag)){
            return providedEtag;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public void put(String path, final String etag, final boolean merge, final long expire, final Handler<Resource> handler) {
        final String key = encodePath(path);
        final DocumentResource d = new DocumentResource();
        final ByteArrayWriteStream stream = new ByteArrayWriteStream();

        final String etagValue = initEtagValue(etag);
        d.writeStream = stream;
        d.closeHandler = new Handler<Void>() {
            public void handle(Void event) {
                String expireInMillis = MAX_EXPIRE_IN_MILLIS;
                if (expire > -1) {
                    expireInMillis = String.valueOf(System.currentTimeMillis() + (expire * 1000));
                }
                JsonObject command = new JsonObject();
                command.putString("command", "evalsha");
                JsonArray args = new JsonArray();
                args.add(luaScripts.get(LuaScript.PUT).getSha());
                args.add(1);
                args.add(key);
                args.add(redisResourcesPrefix);
                args.add(redisCollectionsPrefix);
                args.add(expirableSet);
                args.add(merge == true ? "true" : "false");
                args.add(expireInMillis);
                args.add(MAX_EXPIRE_IN_MILLIS);
                args.add(encodeBinary(stream.getBytes()));
                args.add(etagValue);
                command.putArray("args", args);
                reloadScriptIfLoglevelChangedAndExecuteRedisCommand(LuaScript.PUT, new Put(d, command, handler), 0);
            }
        };
        handler.handle(d);
    }

    /**
     * The Put Command Execution.
     * If the get script cannot be found under the sha in luaScriptState, reload the script.
     * To avoid infinite recursion, we limit the recursion.
     */
    private class Put implements RedisCommand {

        private DocumentResource d;
        private JsonObject command;
        private Handler<Resource> handler;

        public Put(DocumentResource d, JsonObject command, Handler<Resource> handler) {
            this.d = d;
            this.command = command;
            this.handler = handler;
        }

        public void exec(final int executionCounter) {
            eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    if (log.isTraceEnabled()) {
                        log.trace("RedisStorage put result: " + event.body().getString("status") + " " + event.body().getString("value"));
                    }
                    if("error".equals(event.body().getString("status"))) {
                        String message = event.body().getString("message");
                        if(message != null && message.startsWith("NOSCRIPT")) {
                            log.warn("put script couldn't be found, reload it");
                            log.warn("amount the script got loaded: " + String.valueOf(executionCounter));
                            if(executionCounter > 10) {
                                log.error("amount the script got loaded is higher than 10, we abort");
                            } else {
                                luaScripts.get(LuaScript.PUT).loadLuaScript(new Put(d, command, handler), executionCounter);
                            }
                            return;
                        }
                    }
                    if ("error".equals(event.body().getString("status")) && d.errorHandler != null) {
                        d.errorHandler.handle(event.body().getString("message"));
                    } else if (event.body().getString("value") != null && event.body().getString("value").startsWith("existingCollection")) {
                        CollectionResource c = new CollectionResource();
                        handler.handle(c);
                    } else if (event.body().getString("value") != null && event.body().getString("value").startsWith("existingResource")) {
                        DocumentResource d = new DocumentResource();
                        d.exists = false;
                        handler.handle(d);
                    } else if("notModified".equals(event.body().getString("value"))){
                        notModified(handler);
                    } else {
                        d.endHandler.handle(null);
                    }
                }
            });
        }
    }


    @Override
    public void delete(String path, final Handler<Resource> handler) {
        final String key = encodePath(path);
        JsonObject command = new JsonObject();
        command.putString("command", "evalsha");
        JsonArray args = new JsonArray();
        args.add(luaScripts.get(LuaScript.DELETE).getSha());
        args.add(1);
        args.add(key);
        args.add(redisResourcesPrefix);
        args.add(redisCollectionsPrefix);
        args.add(redisDeltaResourcesPrefix);
        args.add(redisDeltaEtagsPrefix);
        args.add(expirableSet);
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(MAX_EXPIRE_IN_MILLIS);
        command.putArray("args", args);
        reloadScriptIfLoglevelChangedAndExecuteRedisCommand(LuaScript.DELETE, new Delete(command, handler), 0);
    }

    /**
     * The Delete Command Execution.
     * If the get script cannot be found under the sha in luaScriptState, reload the script.
     * To avoid infinite recursion, we limit the recursion.
     */
    private class Delete implements RedisCommand {

        private JsonObject command;
        private Handler<Resource> handler;

        public Delete(JsonObject command, final Handler<Resource> handler) {
            this.command = command;
            this.handler = handler;
        }

        public void exec(final int executionCounter) {
            eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    String result = event.body().getString("value");
                    if (log.isTraceEnabled()) {
                        log.trace("RedisStorage delete result: " + result);
                    }
                    if("error".equals(event.body().getString("status"))) {
                        String message = event.body().getString("message");
                        if(message != null && message.startsWith("NOSCRIPT")) {
                            log.warn("delete script couldn't be found, reload it");
                            log.warn("amount the script got loaded: " + String.valueOf(executionCounter));
                            if(executionCounter > 10) {
                                log.error("amount the script got loaded is higher than 10, we abort");
                            } else {
                                luaScripts.get(LuaScript.DELETE).loadLuaScript(new Delete(command, handler), executionCounter);
                            }
                            return;
                        }
                    }
                    if ("notFound".equals(result)) {
                        notFound(handler);
                        return;
                    }
                    Resource r = new Resource();
                    handler.handle(r);
                }
            });
        }
    }

    /**
     * Cleans up the outdated resources recursive.
     * If the script which is refered over the luaScriptState.sha, the execution is aborted and the script is reloaded.
     *
     * @param handler the handler to execute
     * @param cleanedLastRun how many resources were cleaned in the last run
     * @param maxdel max resources to clean
     * @param bulkSize how many resources should be cleaned in one run
     */
    public void cleanupRecursive(final Handler<DocumentResource> handler, final long cleanedLastRun, final long maxdel, final int bulkSize) {
        JsonObject command = new JsonObject();
        command.putString("command", "evalsha");
        JsonArray args = new JsonArray();
        args.add(luaScripts.get(LuaScript.CLEANUP).getSha());
        args.add(0);
        args.add(redisResourcesPrefix);
        args.add(redisCollectionsPrefix);
        args.add(redisDeltaResourcesPrefix);
        args.add(redisDeltaEtagsPrefix);
        args.add(expirableSet);
        args.add("0");
        args.add(MAX_EXPIRE_IN_MILLIS);
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(String.valueOf(bulkSize));
        command.putArray("args", args);
        eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> event) {
                if (log.isTraceEnabled()) {
                    log.trace("RedisStorage cleanup resources result: " + event.body().getString("status"));
                }
                if("error".equals(event.body().getString("status"))) {
                    String message = event.body().getString("message");
                    if(message != null && message.startsWith("NOSCRIPT")) {
                        log.warn("the cleanup script is not loaded. Load it and exit. The Cleanup will success the next time");
                        luaScripts.get(LuaScript.CLEANUP).loadLuaScript(new RedisCommandDoNothing(), 0);
                        return;
                    }
                }
                Long cleanedThisRun = event.body().getLong("value");
                if (log.isTraceEnabled()) {
                    log.trace("RedisStorage cleanup resources cleanded this run: " + cleanedThisRun);
                }
                final long cleaned = cleanedLastRun + cleanedThisRun;
                if (cleanedThisRun != 0 && cleaned < maxdel) {
                    if (log.isTraceEnabled()) {
                        log.trace("RedisStorage cleanup resources call recursive next bulk");
                    }
                    cleanupRecursive(handler, cleaned, maxdel, bulkSize);
                } else {
                    JsonObject command = new JsonObject();
                    command.putString("command", "zcount");
                    JsonArray args = new JsonArray();
                    args.add(expirableSet);
                    args.add(0);
                    args.add(String.valueOf(System.currentTimeMillis()));
                    command.putArray("args", args);
                    eb.send(redisAddress, command, new Handler<Message<JsonObject>>() {
                        public void handle(Message<JsonObject> event) {
                            Number result = event.body().getNumber("value");
                            if (log.isTraceEnabled()) {
                                log.trace("RedisStorage cleanup resources zcount on expirable set: " + result);
                            }
                            int resToCleanLeft = 0;
                            if (result != null && result.intValue() >= 0) {
                                resToCleanLeft = result.intValue();
                            }
                            JsonObject retObj = new JsonObject();
                            retObj.putString("cleanedResources", String.valueOf(cleaned));
                            retObj.putString("expiredResourcesLeft", String.valueOf(resToCleanLeft));
                            DocumentResource r = new DocumentResource();
                            byte[] content = decodeBinary(retObj.toString());
                            r.readStream = new ByteArrayReadStream(content);
                            r.length = content.length;
                            r.closeHandler = new Handler<Void>() {
                                public void handle(Void event) {
                                    // nothing to close
                                }
                            };
                            handler.handle(r);
                        }
                    });
                }
            }
        });
    }

    private String encodePath(String path) {
        if (path.equals("/")) {
            path = "";
        }
        return path.replaceAll(":", "§").replaceAll("/", ":");
    }

    private String encodeBinary(byte[] bytes) {
        try {
            return new String(bytes, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decodeBinary(String s) {
        try {
            return s.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void notFound(Handler<Resource> handler) {
        Resource r = new Resource();
        r.exists = false;
        handler.handle(r);
    }

    private void notModified(Handler<Resource> handler){
        Resource r = new Resource();
        r.modified = false;
        handler.handle(r);
    }

    @Override
    public void cleanup(Handler<DocumentResource> handler, String cleanupResourcesAmountStr) {
        long cleanupResourcesAmountUsed = cleanupResourcesAmount;
        if (log.isTraceEnabled()) {
            log.trace("RedisStorage cleanup resources,  cleanupResourcesAmount: " + cleanupResourcesAmountUsed);
        }
        try {
            cleanupResourcesAmountUsed = Long.parseLong(cleanupResourcesAmountStr);
        } catch (Exception e) {
            // do nothing
        }
        cleanupRecursive(handler, 0, cleanupResourcesAmountUsed, CLEANUP_BULK_SIZE);
    }

    private boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }
}