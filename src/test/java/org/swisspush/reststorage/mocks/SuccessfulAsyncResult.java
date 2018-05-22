package org.swisspush.reststorage.mocks;

import io.vertx.core.AsyncResult;


public class SuccessfulAsyncResult<T> implements AsyncResult<T> {

    private final T value;

    public SuccessfulAsyncResult(T value) {
        this.value = value;
    }

    @Override
    public T result() {
        return value;
    }

    @Override
    public Throwable cause() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean succeeded() {
        return true;
    }

    @Override
    public boolean failed() {
        return false;
    }
}
