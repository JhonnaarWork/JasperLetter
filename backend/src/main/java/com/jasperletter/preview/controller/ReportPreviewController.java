package com.jasperletter.preview.controller;

import com.jasperletter.preview.dto.PreviewRequest;
import com.jasperletter.preview.service.JasperReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*") // Permite peticiones desde el frontend Angular (localhost:4200)
public class ReportPreviewController {

    private static final Logger log = LoggerFactory.getLogger(ReportPreviewController.class);
    private final JasperReportService jasperReportService;

    public ReportPreviewController(JasperReportService jasperReportService) {
        this.jasperReportService = jasperReportService;
    }

    /**
     * Endpoint para verificar que el servicio y motor de JasperReports están arriba.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("engine", "JasperReports 7.0.3");
        response.put("description", "Servicio de renderizado y previsualización de cartas JRXML");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint principal para previsualizar una carta JRXML en formato PDF.
     */
    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewReport(@RequestBody PreviewRequest request) {
        try {
            byte[] pdfContent = jasperReportService.generatePdfPreview(
                    request.getJrxml(),
                    request.getParameters(),
                    request.getLetterId(),
                    request.getXmlData()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "preview-letter.pdf");
            headers.setContentLength(pdfContent.length);

            return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Error al compilar o previsualizar el reporte JRXML", ex);
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("status", "ERROR");
            errorBody.put("message", ex.getMessage());
            errorBody.put("cause", root.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }
    }
}
