package com.evalscope.fasthttp.exception;

public class FastHttpException extends Exception {
    public FastHttpException(String message) {
        super(message);
    }

    public FastHttpException(String message, Throwable cause) {
        super(message, cause);
    }

    public FastHttpException(Throwable cause) {
        super(cause);
    }

    public FastHttpException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}