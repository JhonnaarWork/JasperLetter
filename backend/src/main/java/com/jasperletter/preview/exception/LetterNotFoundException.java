package com.jasperletter.preview.exception;

/**
 * La carta solicitada no existe en resources/reports/, o el identificador recibido no tiene
 * un formato válido (tratado deliberadamente como "no encontrada" en vez de exponer un error
 * de validación distinto, para no revelar si el problema fue el formato del id o que
 * simplemente no existe esa carta).
 */
public class LetterNotFoundException extends RuntimeException {
    public LetterNotFoundException(String message) {
        super(message);
    }
}
