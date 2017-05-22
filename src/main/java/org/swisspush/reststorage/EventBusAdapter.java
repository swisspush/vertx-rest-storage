package org.swisspush.reststorage;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * Provides a direct eventbus interface.
 *
 * @author lbovet
 */
public class EventBusAdapter {

    public void init(final Vertx vertx, String address, final Handler<HttpServerRequest> requestHandler) {
        vertx.eventBus().consumer(address, new Handler<Message<Buffer>>() {
            @Override
            public void handle(Message<Buffer> message) {
                requestHandler.handle(new MappedHttpServerRequest(vertx, message));
            }
        });
    }

    private class MappedHttpServerRequest implements HttpServerRequest {
        private Vertx vertx;
        private Buffer requestPayload;
        private HttpMethod method;
        private String uri;
        private String path;
        private String query;
        private MultiMap params;
        private MultiMap requestHeaders;
        private Message<Buffer> message;
        private Handler<Buffer> dataHandler;
        private Handler<Void> endHandler;
        private HttpServerResponse response;

        private MappedHttpServerRequest(Vertx vertx, Message<Buffer> message) {
            this.vertx = vertx;
            this.message = message;
            Buffer buffer = message.body();
            int headerLength =  buffer.getInt(0);
            JsonObject header = new JsonObject(buffer.getString(4,headerLength+4));
            method = httpMethodFromHeader(header);
            uri = header.getString("uri");
            requestPayload = buffer.getBuffer(headerLength+4, buffer.length());

            JsonArray headerArray = header.getJsonArray("headers");
            if(headerArray != null) {
                requestHeaders = fromJson(headerArray);
            } else {
                requestHeaders = new CaseInsensitiveHeaders();
            }
        }

        private HttpMethod httpMethodFromHeader(JsonObject header){
            String method = header.getString("method");
            if(method != null){
                return HttpMethod.valueOf(method.toUpperCase());
            }
            return null;
        }

        @Override
        public HttpVersion version() {
            return HttpVersion.HTTP_1_0;
        }

        @Override
        public HttpMethod method() {
            return method;
        }

        @Override
        public String rawMethod() { return method.toString(); }

        @Override
        public boolean isSSL() { return false; }

        @Override
        public String scheme() { throw new UnsupportedOperationException(); }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public String path() {
            if(path==null) {
                path = UrlParser.path(uri);
            }
            return path;
        }

        @Override
        public String query() {
            if(query==null) {
                query = UrlParser.query(uri);
            }
            return query;
        }

        @Override
        public String host() { throw new UnsupportedOperationException(); }

        @Override
        public HttpServerResponse response() {
            if(response == null) {
                response = new HttpServerResponse() {

                    private int statusCode;
                    private String statusMessage;
                    private MultiMap responseHeaders = new CaseInsensitiveHeaders();
                    private Buffer responsePayload = Buffer.buffer();

                    @Override
                    public int getStatusCode() {
                        return statusCode;
                    }

                    @Override
                    public HttpServerResponse setStatusCode(int i) {
                        statusCode = i;
                        return this;
                    }

                    @Override
                    public String getStatusMessage() {
                        return statusMessage;
                    }

                    @Override
                    public HttpServerResponse setStatusMessage(String s) {
                        statusMessage = s;
                        return this;
                    }

                    @Override
                    public HttpServerResponse setChunked(boolean b) {
                        return this;
                    }

                    @Override
                    public boolean isChunked() {
                        return false;
                    }

                    @Override
                    public MultiMap headers() {
                        return responseHeaders;
                    }

                    @Override
                    public HttpServerResponse putHeader(String s, String s2) {
                        responseHeaders.set(s, s2);
                        return this;
                    }

                    @Override
                    public HttpServerResponse putHeader(CharSequence charSequence, CharSequence charSequence2) {
                        responseHeaders.set(charSequence, charSequence2);
                        return this;
                    }

                    @Override
                    public HttpServerResponse putHeader(String s, Iterable<String> strings) {
                        for (String value : strings) {
                            responseHeaders.add(s, value);
                        }
                        return this;
                    }

                    @Override
                    public HttpServerResponse putHeader(CharSequence charSequence, Iterable<CharSequence> charSequences) {
                        for (CharSequence value : charSequences) {
                            responseHeaders.add(charSequence, value);
                        }
                        return this;
                    }

                    @Override
                    public MultiMap trailers() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse putTrailer(String s, String s2) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse putTrailer(CharSequence charSequence, CharSequence charSequence2) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse putTrailer(String s, Iterable<String> strings) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse putTrailer(CharSequence charSequence, Iterable<CharSequence> charSequences) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse closeHandler(Handler<Void> voidHandler) {
                        return this;
                    }

                    @Override
                    public HttpServerResponse write(Buffer buffer) {
                        responsePayload.appendBuffer(buffer);
                        return this;
                    }

                    @Override
                    public HttpServerResponse write(String s, String s2) {
                        responsePayload.appendBuffer(Buffer.buffer(s, s2));
                        return this;
                    }

                    @Override
                    public HttpServerResponse write(String s) {
                        responsePayload.appendBuffer(Buffer.buffer(s));
                        return this;
                    }

                    @Override
                    public HttpServerResponse writeContinue() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void end(String s) {
                        write(Buffer.buffer(s));
                        end();
                    }

                    @Override
                    public void end(String s, String s2) {
                        write(s, s2);
                        end();
                    }

                    @Override
                    public void end(Buffer buffer) {
                        write(buffer);
                        end();
                    }

                    @Override
                    public void end() {
                        JsonObject header = new JsonObject();
                        if (statusCode == 0) {
                            statusCode = 200;
                            statusMessage = "OK";
                        }
                        header.put("statusCode", statusCode);
                        header.put("statusMessage", statusMessage);
                        header.put("headers", toJson(responseHeaders));
                        Buffer bufferHeader = Buffer.buffer(header.encode());
                        Buffer response = Buffer.buffer(4+bufferHeader.length()+responsePayload.length());
                        response.setInt(0, bufferHeader.length()).appendBuffer(bufferHeader).appendBuffer(responsePayload);
                        message.reply(response);
                    }

                    @Override
                    public HttpServerResponse sendFile(String s) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse sendFile(String filename, long offset) { throw new UnsupportedOperationException(); }

                    @Override
                    public HttpServerResponse sendFile(String filename, long offset, long length) { throw new UnsupportedOperationException(); }

                    @Override
                    public HttpServerResponse sendFile(String s, Handler<AsyncResult<Void>> asyncResultHandler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse sendFile(String filename, long offset, Handler<AsyncResult<Void>> resultHandler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void close() {}

                    @Override
                    public boolean ended() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean closed() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean headWritten() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse headersEndHandler(Handler<Void> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse bodyEndHandler(Handler<Void> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public long bytesWritten() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int streamId() { throw new UnsupportedOperationException(); }

                    @Override
                    public HttpServerResponse push(HttpMethod method, String host, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse push(HttpMethod method, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse push(HttpMethod method, String path, Handler<AsyncResult<HttpServerResponse>> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse push(HttpMethod method, String host, String path, MultiMap headers, Handler<AsyncResult<HttpServerResponse>> handler) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void reset(long code) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse writeCustomFrame(int type, int flags, Buffer payload) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public HttpServerResponse setWriteQueueMaxSize(int i) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean writeQueueFull() {
                        return false;
                    }

                    @Override
                    public HttpServerResponse drainHandler(Handler<Void> voidHandler) {
                        return this;
                    }

                    @Override
                    public HttpServerResponse exceptionHandler(Handler<Throwable> throwableHandler) {
                        return this;
                    }
                };
            }
            return response;
        }

        @Override
        public MultiMap headers() {
            return requestHeaders;
        }

        @Override
        public String getHeader(String headerName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getHeader(CharSequence headerName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiMap params() {
            if (params == null) {
                QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri());
                Map<String, List<String>> prms = queryStringDecoder.parameters();
                params = new CaseInsensitiveHeaders();
                if (!prms.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
                        params.add(entry.getKey(), entry.getValue());
                    }
                }
            }
            return params;
        }

        @Override
        public String getParam(String paramName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketAddress remoteAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketAddress localAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
            return new X509Certificate[0];
        }

        @Override
        public String absoluteURI() {
            return null;
        }

        @Override
        public HttpServerRequest bodyHandler(final Handler<Buffer> bodyHandler) {
            final Buffer body = Buffer.buffer();
            handler(body::appendBuffer);
            endHandler(new VoidHandler() {
                public void handle() {
                    bodyHandler.handle(body);
                }
            });
            return this;
        }

        @Override
        public NetSocket netSocket() {
            return null;
        }

        @Override
        public HttpServerRequest setExpectMultipart(boolean expect) {
            return this;
        }

        @Override
        public boolean isExpectMultipart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> httpServerFileUploadHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiMap formAttributes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getFormAttribute(String attributeName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerWebSocket upgrade() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEnded() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
            return null;
        }

        @Override
        public HttpConnection connection() {
            return null;
        }

        @Override
        public HttpServerRequest endHandler(Handler<Void> voidHandler) {
            endHandler = voidHandler;
            if(requestPayload == null) {
                endHandler.handle(null);
            }
            return this;
        }

        @Override
        public HttpServerRequest pause() {
            return this;
        }

        @Override
        public HttpServerRequest resume() {
            return this;
        }

        @Override
        public HttpServerRequest exceptionHandler(Handler<Throwable> throwableHandler) {
            return this;
        }

        @Override
        public HttpServerRequest handler(Handler<Buffer> bufferHandler) {
            if(requestPayload != null) {
                dataHandler = bufferHandler;
                vertx.runOnContext(aVoid -> {
                    dataHandler.handle(requestPayload);
                    endHandler.handle(null);
                });
            }
            return this;
        }
    }

    public static JsonArray toJson(MultiMap multiMap) {
        JsonArray result = new JsonArray();
        for(Map.Entry<String, String> entry: multiMap.entries()) {
            result.add(new JsonArray().add(entry.getKey()).add(entry.getValue()));
        }
        return result;
    }

    public static MultiMap fromJson(JsonArray json) {
        MultiMap result = new CaseInsensitiveHeaders();
        for (Object next : json) {
            if (next instanceof JsonArray) {
                JsonArray pair = (JsonArray) next;
                if (pair.size() == 2) {
                    result.add(pair.getString(0), pair.getString(1));
                }
            }
        }
        return result;
    }
}
