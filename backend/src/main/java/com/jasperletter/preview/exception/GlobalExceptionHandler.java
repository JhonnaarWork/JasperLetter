package com.jasperletter.preview.exception;

import com.jasperletter.preview.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo centralizado de errores de la API (hallazgo A-3): reemplaza los bloques try/catch
 * repetidos en cada método de LetterResourceController, que construían a mano un
 * Map&lt;String, Object&gt; con la misma forma una y otra vez.
 *
 * ReportPreviewController conserva su propio catch para errores de compilación/llenado de
 * JasperReports: ese caso necesita desenrollar la causa raíz de la excepción (útil para
 * diagnosticar plantillas JRXML inválidas) y responder 400, un comportamiento propio de ese
 * único endpoint que no tiene sentido generalizar aquí.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LetterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(LetterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Error no controlado en la API", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("ERROR", ex.getMessage()));
    }
}
