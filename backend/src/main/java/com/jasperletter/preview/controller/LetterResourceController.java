package com.jasperletter.preview.controller;

import com.jasperletter.preview.dto.CreateLetterRequest;
import com.jasperletter.preview.dto.DataAdapterOptionInfo;
import com.jasperletter.preview.dto.DataFileInfo;
import com.jasperletter.preview.dto.LetterDetailResponse;
import com.jasperletter.preview.dto.LetterResourceInfo;
import com.jasperletter.preview.dto.SaveLetterRequest;
import com.jasperletter.preview.dto.TestDataAdapterRequest;
import com.jasperletter.preview.dto.TestDataAdapterResponse;
import com.jasperletter.preview.service.LetterResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
@CrossOrigin(origins = "*")
public class LetterResourceController {

    private static final Logger log = LoggerFactory.getLogger(LetterResourceController.class);
    private final LetterResourceService letterResourceService;

    public LetterResourceController(LetterResourceService letterResourceService) {
        this.letterResourceService = letterResourceService;
    }

    /**
     * Lista todas las cartas disponibles en resources/reports/
     */
    @GetMapping("/letters")
    public ResponseEntity<List<LetterResourceInfo>> getLetters() {
        List<LetterResourceInfo> letters = letterResourceService.getAvailableLetters();
        return ResponseEntity.ok(letters);
    }

    /**
     * Obtiene el contenido completo de una carta específica (JRXML, XML de datos, DataAdapter)
     */
    @GetMapping("/letters/{letterId}")
    public ResponseEntity<?> getLetterDetail(@PathVariable("letterId") String letterId) {
        try {
            LetterDetailResponse detail = letterResourceService.getLetterDetail(letterId);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "NOT_FOUND");
            err.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        } catch (Exception ex) {
            log.error("Error al obtener detalle de carta {}", letterId, ex);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Guarda modificaciones a la plantilla JRXML y datos XML en disco en su formato original
     */
    @PostMapping("/letters/{letterId}/save")
    public ResponseEntity<?> saveLetter(@PathVariable("letterId") String letterId, @RequestBody SaveLetterRequest request) {
        try {
            boolean saveJrxml = request.getSaveJrxml() != null ? request.getSaveJrxml() : true;
            boolean saveDataAdapter = request.getSaveDataAdapter() != null ? request.getSaveDataAdapter() : true;
            boolean saveXmlData = request.getSaveXmlData() != null ? request.getSaveXmlData() : true;

            letterResourceService.saveLetter(
                    letterId,
                    request.getJrxml(),
                    saveJrxml,
                    request.getXmlData(),
                    saveXmlData,
                    request.getDataAdapter(),
                    saveDataAdapter,
                    request.getFormat()
            );

            boolean isConnected = letterResourceService.isDataAdapterConnected(letterId);

            String reloadedXmlData = null;
            if (isConnected) {
                File xmlFile = letterResourceService.getDataFileForLetter(letterId);
                if (xmlFile != null && xmlFile.exists() && xmlFile.isFile()) {
                    try {
                        reloadedXmlData = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Carta " + letterId + " guardada exitosamente en resources/reports/" + letterId);
            response.put("dataAdapterConnected", isConnected);
            if (reloadedXmlData != null) {
                response.put("xmlData", reloadedXmlData);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "VALIDATION_ERROR");
            err.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        } catch (Exception ex) {
            log.error("Error al guardar carta {}", letterId, ex);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "Error al guardar carta en disco: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Crea una nueva carta desde cero en resources/reports/{letterId}/
     */
    @PostMapping("/letters/create")
    public ResponseEntity<?> createLetter(@RequestBody CreateLetterRequest request) {
        try {
            LetterDetailResponse detail = letterResourceService.createLetter(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(detail);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "VALIDATION_ERROR");
            err.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        } catch (Exception ex) {
            log.error("Error al crear carta {}", request.getLetterId(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "Error al crear carta en disco: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Lista todos los Data Adapters existentes en reports/
     */
    @GetMapping("/data-adapters")
    public ResponseEntity<List<DataAdapterOptionInfo>> getDataAdapters() {
        return ResponseEntity.ok(letterResourceService.getAvailableDataAdapters());
    }

    /**
     * Lista todos los archivos XML de datos disponibles en resources/data/xml/
     */
    @GetMapping("/data-xml-files")
    public ResponseEntity<List<DataFileInfo>> getDataXmlFiles() {
        return ResponseEntity.ok(letterResourceService.getAvailableDataXmlFiles());
    }

    /**
     * Crea un nuevo archivo de datos XML en resources/data/xml/
     */
    @PostMapping("/data-xml-files/create")
    public ResponseEntity<?> createDataXmlFile(@RequestBody Map<String, String> body) {
        try {
            String letterId = body.get("letterId");
            String fileName = body.get("fileName");
            String content = body.get("content");
            DataFileInfo info = letterResourceService.createDataXmlFile(letterId, fileName, content);
            return ResponseEntity.status(HttpStatus.CREATED).body(info);
        } catch (Exception ex) {
            log.error("Error al crear archivo de datos XML", ex);
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "Error al crear archivo XML: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Prueba el Data Adapter: valida sintaxis XML, existencia física del archivo de datos e integridad
     */
    @PostMapping("/data-adapter/test")
    public ResponseEntity<TestDataAdapterResponse> testDataAdapter(@RequestBody TestDataAdapterRequest request) {
        log.info("Probando Data Adapter para carta {}...", request.getLetterId());
        TestDataAdapterResponse response = letterResourceService.testDataAdapter(
                request.getLetterId(),
                request.getDataAdapterXml(),
                request.getCustomXmlData()
        );
        return ResponseEntity.ok(response);
    }
}
