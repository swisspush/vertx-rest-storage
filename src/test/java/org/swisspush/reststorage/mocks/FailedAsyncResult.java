package org.swisspush.reststorage.mocks;

import io.vertx.core.AsyncResult;


public class FailedAsyncResult<T> implements AsyncResult<T> {

    private final T value;
    private final Throwable cause;

    public FailedAsyncResult(T value) {
        this(value, new RuntimeException("Source didn't specify a cause"));
    }

    public FailedAsyncResult(T value, Throwable cause) {
        this.value = value;
        this.cause = cause;
    }

    @Override
    public T result() {
        return value;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean succeeded() {
        return false;
    }

    @Override
    public boolean failed() {
        return true;
    }
}
