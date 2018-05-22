package org.swisspush.reststorage.mocks;

import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;


public class FailFastVertxWriteStream<T> implements WriteStream<T> {

    protected final String msg;

    public FailFastVertxWriteStream() {
        this("Method not implemented in mock. Override method to provide your behaviour.");
    }

    public FailFastVertxWriteStream(String msg) {
        this.msg = msg;
    }

    @Override
    public WriteStream<T> exceptionHandler(Handler<Throwable> handler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public WriteStream<T> write(T t) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public void end() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public WriteStream<T> setWriteQueueMaxSize(int i) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public boolean writeQueueFull() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public WriteStream<T> drainHandler(Handler<Void> handler) {
        throw new UnsupportedOperationException(msg);
    }
}
