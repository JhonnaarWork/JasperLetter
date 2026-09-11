package com.jasperletter.preview.exception;

/**
 * La petición del cliente es inválida (identificador con formato incorrecto, campo requerido
 * ausente, carta ya existente al crear, etc.) — se traduce a un 400 Bad Request.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
