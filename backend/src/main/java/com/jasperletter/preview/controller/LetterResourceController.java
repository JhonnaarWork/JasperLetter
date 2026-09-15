package com.jasperletter.preview.controller;

import com.jasperletter.preview.dto.CreateLetterRequest;
import com.jasperletter.preview.dto.DataAdapterOptionInfo;
import com.jasperletter.preview.dto.DataFileInfo;
import com.jasperletter.preview.dto.GeneratedJrxmlResponse;
import com.jasperletter.preview.dto.ImportJrxmlRequest;
import com.jasperletter.preview.dto.LetterDetailResponse;
import com.jasperletter.preview.dto.LetterResourceInfo;
import com.jasperletter.preview.dto.SaveLetterRequest;
import com.jasperletter.preview.dto.TestDataAdapterRequest;
import com.jasperletter.preview.dto.TestDataAdapterResponse;
import com.jasperletter.preview.exception.ValidationException;
import com.jasperletter.preview.service.LetterResourceService;
import com.jasperletter.preview.service.PdfToJrxmlService;
import com.jasperletter.preview.util.JrxmlFormatUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final PdfToJrxmlService pdfToJrxmlService;

    public LetterResourceController(LetterResourceService letterResourceService, PdfToJrxmlService pdfToJrxmlService) {
        this.letterResourceService = letterResourceService;
        this.pdfToJrxmlService = pdfToJrxmlService;
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
     * Importa una carta a partir de un JRXML ya escrito: crea resources/reports/{letterId}/ con
     * ese JRXML, y opcionalmente Data Adapter + XML de datos base (ver "Generar Datos" en el
     * frontend para poblarlo con los fields del propio JRXML).
     */
    @PostMapping("/letters/import-jrxml")
    public ResponseEntity<LetterDetailResponse> importJrxml(@Valid @RequestBody ImportJrxmlRequest request) throws IOException {
        LetterDetailResponse detail = letterResourceService.importLetterFromJrxml(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    /**
     * Importa una carta generando un JRXML de layout estático a partir de la primera página de
     * un PDF (ver PdfToJrxmlService) — punto de partida visual, no funcional.
     */
    @PostMapping(value = "/letters/import-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LetterDetailResponse> importPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("letterId") String letterId,
            @RequestParam(value = "format", defaultValue = "JR6") String format,
            @RequestParam(value = "createDataAdapter", defaultValue = "true") boolean createDataAdapter,
            @RequestParam(value = "createXmlData", defaultValue = "true") boolean createXmlData
    ) throws IOException {
        if (file.isEmpty()) {
            throw new ValidationException("El archivo PDF está vacío.");
        }
        LetterDetailResponse detail = letterResourceService.importLetterFromPdf(
                file.getBytes(), letterId, format, createDataAdapter, createXmlData);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    /**
     * Genera un JRXML de layout estático a partir de un PDF SIN crear ninguna carta — a
     * diferencia de /letters/import-pdf, este endpoint no toca el disco en absoluto, solo
     * devuelve el JRXML generado. Lo usa el frontend cuando el usuario elige "cargar sobre la
     * carta actual" en vez de "crear carta nueva": el JRXML resultante se aplica en memoria
     * sobre la carta ya abierta, quedando como cambio pendiente por guardar.
     */
    @PostMapping(value = "/generate-jrxml-from-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeneratedJrxmlResponse> generateJrxmlFromPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "letterId", defaultValue = "CARTA") String letterId,
            @RequestParam(value = "format", defaultValue = "JR6") String format
    ) throws IOException {
        if (file.isEmpty()) {
            throw new ValidationException("El archivo PDF está vacío.");
        }
        String jrxml = pdfToJrxmlService.generateJrxmlFromPdf(file.getBytes(), letterId);
        if ("JR7".equalsIgnoreCase(format)) {
            jrxml = JrxmlFormatUtils.convertToJr7(jrxml);
        }
        return ResponseEntity.ok(new GeneratedJrxmlResponse(jrxml));
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
