package com.example.secfix.exception;

public class CompletionException extends RuntimeException {

    public CompletionException(String message) {
        super(message);
    }

    public CompletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
