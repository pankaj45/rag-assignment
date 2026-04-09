package com.example.secfix.exception;

public class SchemaInferenceException extends RuntimeException {

    public SchemaInferenceException(String message) {
        super(message);
    }

    public SchemaInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
