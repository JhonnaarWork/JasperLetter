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
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los errores de estos endpoints (carta no encontrada, validación, fallos de E/S) se manejan
 * de forma centralizada en GlobalExceptionHandler: los métodos no atrapan excepciones, las
 * dejan propagar.
 */
@RestController
@RequestMapping("/api/resources")
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
    public ResponseEntity<LetterDetailResponse> getLetterDetail(@PathVariable("letterId") String letterId) throws IOException {
        LetterDetailResponse detail = letterResourceService.getLetterDetail(letterId);
        return ResponseEntity.ok(detail);
    }

    /**
     * Guarda modificaciones a la plantilla JRXML y datos XML en disco en su formato original
     */
    @PostMapping("/letters/{letterId}/save")
    public ResponseEntity<Map<String, Object>> saveLetter(@PathVariable("letterId") String letterId, @RequestBody SaveLetterRequest request) throws IOException {
        boolean saveJrxml = request.saveJrxml() != null ? request.saveJrxml() : true;
        boolean saveDataAdapter = request.saveDataAdapter() != null ? request.saveDataAdapter() : true;
        boolean saveXmlData = request.saveXmlData() != null ? request.saveXmlData() : true;

        letterResourceService.saveLetter(
                letterId,
                request.jrxml(),
                saveJrxml,
                request.xmlData(),
                saveXmlData,
                request.dataAdapter(),
                saveDataAdapter,
                request.format()
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
    }

    /**
     * Crea una nueva carta desde cero en resources/reports/{letterId}/
     */
    @PostMapping("/letters/create")
    public ResponseEntity<LetterDetailResponse> createLetter(@Valid @RequestBody CreateLetterRequest request) throws IOException {
        LetterDetailResponse detail = letterResourceService.createLetter(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
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
    public ResponseEntity<DataFileInfo> createDataXmlFile(@RequestBody Map<String, String> body) throws IOException {
        String letterId = body.get("letterId");
        String fileName = body.get("fileName");
        String content = body.get("content");
        DataFileInfo info = letterResourceService.createDataXmlFile(letterId, fileName, content);
        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

    /**
     * Prueba el Data Adapter: valida sintaxis XML, existencia física del archivo de datos e integridad
     */
    @PostMapping("/data-adapter/test")
    public ResponseEntity<TestDataAdapterResponse> testDataAdapter(@RequestBody TestDataAdapterRequest request) {
        log.info("Probando Data Adapter para carta {}...", request.letterId());
        TestDataAdapterResponse response = letterResourceService.testDataAdapter(
                request.letterId(),
                request.dataAdapterXml(),
                request.customXmlData()
        );
        return ResponseEntity.ok(response);
    }
}
