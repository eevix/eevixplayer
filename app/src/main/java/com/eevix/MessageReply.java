package com.eevix;

import java.util.concurrent.Semaphore;

public class MessageReply<T> {
    private Semaphore mSemaphore= new Semaphore(0);
    private T mData = null;

    void setData(T data) {
        mData = data;
    }

    T getData() {
        return mData;
    }

    void waitReply() {
        try {
            mSemaphore.acquire();
        } catch (Exception exception) {
            // Empty
        }
    }

    void notifyReply() {
        mSemaphore.release();
    }
}
