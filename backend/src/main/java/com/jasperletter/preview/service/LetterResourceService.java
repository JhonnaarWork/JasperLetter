package com.jasperletter.preview.service;

import com.jasperletter.preview.dto.CreateLetterRequest;
import com.jasperletter.preview.dto.DataAdapterOptionInfo;
import com.jasperletter.preview.dto.DataFileInfo;
import com.jasperletter.preview.dto.LetterDetailResponse;
import com.jasperletter.preview.dto.LetterResourceInfo;
import com.jasperletter.preview.dto.TestDataAdapterResponse;
import com.jasperletter.preview.exception.LetterNotFoundException;
import com.jasperletter.preview.exception.ValidationException;
import com.jasperletter.preview.util.JrxmlFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LetterResourceService {

    private static final Logger log = LoggerFactory.getLogger(LetterResourceService.class);

    /**
     * Un letterId válido es un único segmento de ruta: letras, dígitos, "_" y "-".
     * Rechaza "..", separadores de directorio y cualquier otro carácter que permita
     * escapar de resources/reports/{letterId} al construir rutas de archivo.
     */
    private static final Pattern LETTER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    // Visibilidad de paquete (no private) para permitir tests unitarios directos sin reflexión.
    boolean isValidLetterId(String letterId) {
        return letterId != null && LETTER_ID_PATTERN.matcher(letterId.trim()).matches();
    }

    /**
     * Valida letterId antes de usarlo para construir una ruta de archivo. Se usa en las
     * operaciones que actúan sobre una carta puntual identificada por el cliente
     * (lectura y guardado), donde un id inválido debe tratarse como error, no ignorarse.
     */
    String requireValidLetterId(String letterId) {
        if (!isValidLetterId(letterId)) {
            throw new ValidationException("Identificador de carta inválido: " + letterId);
        }
        return letterId.trim();
    }

    @Value("${app.resources.dir:../resources}")
    private String configuredResourcesDir = "../resources";

    private File resolvedResourcesDir;

    public File getResourcesDir() {
        if (resolvedResourcesDir != null && resolvedResourcesDir.exists()) {
            return resolvedResourcesDir;
        }

        File chosen = null;

        // 1. Verificar ruta configurada
        if (configuredResourcesDir != null) {
            File f = new File(configuredResourcesDir);
            if (f.exists() && f.isDirectory()) {
                chosen = f;
            }
        }

        // 2. Probar relativo ../resources
        if (chosen == null) {
            File f1 = new File("../resources");
            if (f1.exists() && f1.isDirectory()) {
                chosen = f1;
            }
        }

        // 3. Probar relativo ./resources
        if (chosen == null) {
            File f2 = new File("./resources");
            if (f2.exists() && f2.isDirectory()) {
                chosen = f2;
            }
        }

        if (chosen != null) {
            try {
                resolvedResourcesDir = chosen.getCanonicalFile();
            } catch (IOException e) {
                resolvedResourcesDir = chosen.getAbsoluteFile();
            }
        } else {
            log.warn("No se pudo localizar el directorio resources/ (probado: app.resources.dir={}, ../resources, ./resources). "
                            + "Usando ../resources como último recurso; configure app.resources.dir si esto no es correcto.",
                    configuredResourcesDir);
            resolvedResourcesDir = new File("../resources");
        }

        return resolvedResourcesDir;
    }

    /**
     * Lista todas las cartas encontradas en resources/reports/
     */
    public List<LetterResourceInfo> getAvailableLetters() {
        List<LetterResourceInfo> result = new ArrayList<>();
        File reportsDir = new File(getResourcesDir(), "reports");
        if (!reportsDir.exists() || !reportsDir.isDirectory()) {
            log.warn("El directorio de reportes no existe: {}", reportsDir.getAbsolutePath());
            return result;
        }

        File[] subdirs = reportsDir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return result;
        }

        for (File dir : subdirs) {
            String letterId = dir.getName();
            File jrxmlFile = new File(dir, letterId + ".jrxml");
            if (!jrxmlFile.exists()) {
                // Probar otros .jrxml en la carpeta
                File[] anyJrxml = dir.listFiles((d, name) -> name.endsWith(".jrxml"));
                if (anyJrxml != null && anyJrxml.length > 0) {
                    jrxmlFile = anyJrxml[0];
                } else {
                    continue;
                }
            }

            File adapterFile = new File(dir, "xmlDataAdapter.xml");
            File xmlDataFile = new File(getResourcesDir(), "data/xml/" + letterId + ".xml");

            String format = "JR6";
            try {
                String content = Files.readString(jrxmlFile.toPath(), StandardCharsets.UTF_8);
                format = JrxmlFormatUtils.detectFormat(content);
            } catch (Exception ignored) {
            }

            result.add(new LetterResourceInfo(
                    letterId,
                    "Carta " + letterId,
                    "reports/" + letterId,
                    jrxmlFile.getName(),
                    format,
                    adapterFile.exists(),
                    adapterFile.exists() ? adapterFile.getName() : null,
                    xmlDataFile.exists(),
                    xmlDataFile.exists() ? "data/xml/" + letterId + ".xml" : null
            ));
        }

        return result;
    }

    /**
     * Obtiene el contenido completo de una carta (JRXML, DataAdapter y XML de datos)
     */
    public LetterDetailResponse getLetterDetail(String letterId) throws IOException {
        try {
            letterId = requireValidLetterId(letterId);
        } catch (ValidationException e) {
            // Un id con formato inválido nunca puede corresponder a una carta real: se trata
            // como "no encontrada" en vez de propagar el error de validación (evita revelar si
            // el problema fue el formato del id o que la carta simplemente no existe).
            throw new LetterNotFoundException("Carta no encontrada: " + letterId);
        }
        File letterDir = new File(getResourcesDir(), "reports/" + letterId);
        if (!letterDir.exists() || !letterDir.isDirectory()) {
            throw new LetterNotFoundException("Carta no encontrada: " + letterId);
        }

        // Buscar el archivo JRXML preferente
        File jrxmlFile = new File(letterDir, letterId + ".jrxml");
        if (!jrxmlFile.exists()) {
            File[] files = letterDir.listFiles((d, name) -> name.endsWith(".jrxml"));
            if (files != null && files.length > 0) {
                jrxmlFile = files[0];
            } else {
                throw new LetterNotFoundException("No se encontró ningún archivo .jrxml para la carta " + letterId);
            }
        }

        String jrxmlContent = Files.readString(jrxmlFile.toPath(), StandardCharsets.UTF_8);
        String format = JrxmlFormatUtils.detectFormat(jrxmlContent);

        // Data Adapter
        File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
        String adapterContent = adapterFile.exists() ? Files.readString(adapterFile.toPath(), StandardCharsets.UTF_8) : null;

        // Comprobar si el data adapter está conectado (la ruta declarada debe existir en disco)
        boolean dataAdapterConnected = false;
        File resolvedXmlFile = null;
        if (adapterContent != null && !adapterContent.trim().isEmpty()) {
            String loc = extractLocationFromXml(adapterContent);
            if (loc != null && !loc.trim().isEmpty()) {
                resolvedXmlFile = resolveDataFile(letterId, loc, null);
                if (resolvedXmlFile != null && resolvedXmlFile.exists() && resolvedXmlFile.isFile()) {
                    dataAdapterConnected = true;
                }
            }
        }

        // XML Data: si el data adapter está conectado se lee su archivo resuelto.
        // Si no está conectado o falló la resolución, se recurre al archivo fallback estándar resources/data/xml/{letterId}.xml
        String xmlDataContent = null;
        if (dataAdapterConnected && resolvedXmlFile != null) {
            try {
                xmlDataContent = Files.readString(resolvedXmlFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("No se pudo leer archivo de datos resuelto {}: {}", resolvedXmlFile.getAbsolutePath(), e.getMessage());
            }
        }
        if (xmlDataContent == null) {
            File fallback = new File(getResourcesDir(), "data/xml/" + letterId + ".xml");
            if (fallback.exists() && fallback.isFile()) {
                try {
                    xmlDataContent = Files.readString(fallback.toPath(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }
        }

        // Imágenes
        List<String> images = new ArrayList<>();
        File[] imgFiles = letterDir.listFiles((d, name) ->
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif"));
        if (imgFiles != null) {
            for (File img : imgFiles) {
                images.add(img.getName());
            }
        }

        return new LetterDetailResponse(
                letterId,
                letterId,
                format,
                jrxmlContent,
                xmlDataContent,
                adapterContent,
                images,
                dataAdapterConnected
        );
    }

    /**
     * Guarda el JRXML y opcionalmente el XML de datos en disco, respetando el formato (JR6 o JR7)
     */
    public void saveLetter(String letterId, String jrxml, String xmlData, String dataAdapter, String requestedFormat) throws IOException {
        saveLetter(letterId, jrxml, true, xmlData, true, dataAdapter, true, requestedFormat);
    }

    public void saveLetter(String letterId, String jrxml, boolean saveJrxml,
                           String xmlData, boolean saveXmlData,
                           String dataAdapter, boolean saveDataAdapter,
                           String requestedFormat) throws IOException {
        letterId = requireValidLetterId(letterId);
        File letterDir = new File(getResourcesDir(), "reports/" + letterId);
        if (!letterDir.exists()) {
            letterDir.mkdirs();
        }

        File jrxmlFile = new File(letterDir, letterId + ".jrxml");

        if (saveJrxml && jrxml != null && !jrxml.trim().isEmpty()) {
            // Determinar formato objetivo
            String targetFormat = requestedFormat;
            if (targetFormat == null || targetFormat.trim().isEmpty()) {
                if (jrxmlFile.exists()) {
                    String existingContent = Files.readString(jrxmlFile.toPath(), StandardCharsets.UTF_8);
                    targetFormat = JrxmlFormatUtils.detectFormat(existingContent);
                } else {
                    targetFormat = JrxmlFormatUtils.detectFormat(jrxml);
                }
            }

            log.info("Guardando carta {} en formato objetivo: {}", letterId, targetFormat);

            String finalJrxmlToSave = jrxml;
            String incomingFormat = JrxmlFormatUtils.detectFormat(jrxml);

            if ("JR6".equalsIgnoreCase(targetFormat)) {
                if ("JR7".equalsIgnoreCase(incomingFormat)) {
                    log.info("Convirtiendo JRXML de JR7 a JR6 antes de guardar...");
                    finalJrxmlToSave = JrxmlFormatUtils.convertToJr6(jrxml);
                }
            } else if ("JR7".equalsIgnoreCase(targetFormat)) {
                if ("JR6".equalsIgnoreCase(incomingFormat)) {
                    log.info("Convirtiendo JRXML de JR6 a JR7 antes de guardar...");
                    finalJrxmlToSave = JrxmlFormatUtils.convertToJr7(jrxml);
                }
            }

            Files.writeString(jrxmlFile.toPath(), finalJrxmlToSave, StandardCharsets.UTF_8);
            log.info("Archivo JRXML guardado exitosamente en: {}", jrxmlFile.getAbsolutePath());
        }

        // Guardar XML de datos si fue provisto y solicitado
        if (saveXmlData && xmlData != null && !xmlData.trim().isEmpty()) {
            File xmlDataFile = getDataFileForLetter(letterId);
            if (xmlDataFile == null) {
                File xmlDataDir = new File(getResourcesDir(), "data/xml");
                if (!xmlDataDir.exists()) {
                    xmlDataDir.mkdirs();
                }
                xmlDataFile = new File(xmlDataDir, letterId + ".xml");
            }
            Files.writeString(xmlDataFile.toPath(), xmlData, StandardCharsets.UTF_8);
            log.info("Archivo XML de datos guardado exitosamente en: {}", xmlDataFile.getAbsolutePath());
        }

        // Guardar Data Adapter si fue provisto y solicitado
        if (saveDataAdapter && dataAdapter != null && !dataAdapter.trim().isEmpty()) {
            File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
            Files.writeString(adapterFile.toPath(), dataAdapter, StandardCharsets.UTF_8);
            log.info("Archivo Data Adapter guardado exitosamente en: {}", adapterFile.getAbsolutePath());
        }
    }

    /**
     * Devuelve la ruta canónica normalizada de un File, evitando puntos relativos (../).
     */
    public String toCanonicalPath(File f) {
        if (f == null) return null;
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    /**
     * Determina si una ruta es explícitamente absoluta (ej: C:\... en Windows o /... en Unix).
     */
    public boolean isExplicitlyAbsolute(String path) {
        if (path == null || path.trim().isEmpty()) return false;
        String trimmed = path.trim();
        // Windows drive letter: ej. C:\ o C:/
        if (trimmed.length() >= 2 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':') {
            return true;
        }
        // UNC: \\server\share o //server/share
        if (trimmed.startsWith("\\\\") || trimmed.startsWith("//")) {
            return true;
        }
        // Linux/Unix absoluto (solo si no es Windows)
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win") && trimmed.startsWith("/")) {
            File f = new File(trimmed);
            if (f.exists() && f.isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comprueba que un archivo candidato quede realmente contenido dentro de resources/,
     * resolviendo enlaces simbólicos y secuencias ".." antes de comparar. Se usa para evitar
     * que una ruta declarada por el cliente (absoluta o con "..") pueda escapar del directorio
     * de recursos y exponer lectura arbitraria de archivos del servidor.
     */
    boolean isWithinResourcesDir(File candidate) {
        File resourcesDir = getResourcesDir();
        if (resourcesDir == null || candidate == null) {
            return false;
        }
        try {
            Path resourcesReal = resourcesDir.toPath().toRealPath();
            Path candidateReal = candidate.toPath().toRealPath();
            return candidateReal.startsWith(resourcesReal);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resuelve el archivo físico de datos XML basándose estrictamente en la ruta declarada en el Data Adapter.
     * No realiza suposiciones arbitrarias ni extracción por substring:
     * - Si la ruta es absoluta, solo se busca en esa ruta absoluta.
     * - Si la ruta es relativa, se busca respetando la ruta declarada relativa a:
     *   1. Raíz del workspace (padre de resources/)
     *   2. Directorio resources/
     *   3. Directorio de la carta (resources/reports/{letterId}/)
     * En todos los casos, el archivo resuelto debe quedar contenido dentro de resources/:
     * <location> proviene de contenido enviado por el cliente y no es de confianza, por lo que
     * cualquier candidato que caiga fuera de ese límite (vía ruta absoluta ajena o secuencias "..")
     * se descarta en vez de leerse.
     */
    public File resolveDataFile(String letterId, String location, List<String> testedPathsOut) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }

        String trimmed = location.trim();
        List<String> tested = testedPathsOut != null ? testedPathsOut : new ArrayList<>();

        // 1. Si es ruta absoluta explícita (ej: C:\... o \\server\...)
        if (isExplicitlyAbsolute(trimmed)) {
            File fAbs = new File(trimmed);
            String canonPath = toCanonicalPath(fAbs);
            if (!tested.contains(canonPath)) {
                tested.add(canonPath);
            }
            if (fAbs.exists() && fAbs.isFile() && isWithinResourcesDir(fAbs)) {
                return fAbs;
            }
            return null;
        }

        // 2. Ruta relativa: limpiar barras iniciales de rutas de repositorio (ej: "/data/xml/..." -> "data/xml/...")
        String relPath = trimmed.replace('\\', '/').replaceAll("^/+", "");
        File resourcesDir = getResourcesDir();
        File workspaceRoot = resourcesDir != null ? resourcesDir.getParentFile() : null;
        File letterDir = (letterId != null && !letterId.trim().isEmpty() && resourcesDir != null)
                ? new File(resourcesDir, "reports/" + letterId)
                : null;

        // A. Relativo a la raíz del workspace (ej: resources/data/xml/ETIPLET002.xml)
        if (workspaceRoot != null && workspaceRoot.exists()) {
            File fWs = new File(workspaceRoot, relPath);
            String canonWs = toCanonicalPath(fWs);
            if (!tested.contains(canonWs)) {
                tested.add(canonWs);
            }
            if (fWs.exists() && fWs.isFile() && isWithinResourcesDir(fWs)) {
                return fWs;
            }
        }

        // B. Relativo a resources/ (ej: data/xml/ETIPLET002.xml)
        if (resourcesDir != null && resourcesDir.exists()) {
            File fRes = new File(resourcesDir, relPath);
            String canonRes = toCanonicalPath(fRes);
            if (!tested.contains(canonRes)) {
                tested.add(canonRes);
            }
            if (fRes.exists() && fRes.isFile() && isWithinResourcesDir(fRes)) {
                return fRes;
            }
        }

        // C. Relativo al directorio de la carta (ej: ../../data/xml/ETIPLET002.xml)
        if (letterDir != null && letterDir.exists()) {
            File fLetter = new File(letterDir, relPath);
            String canonLetter = toCanonicalPath(fLetter);
            if (!tested.contains(canonLetter)) {
                tested.add(canonLetter);
            }
            if (fLetter.exists() && fLetter.isFile() && isWithinResourcesDir(fLetter)) {
                return fLetter;
            }
        }

        return null;
    }

    /**
     * Extrae el valor de la etiqueta <location> desde el XML del Data Adapter.
     */
    public String extractLocationFromXml(String dataAdapterXml) {
        if (dataAdapterXml == null || dataAdapterXml.trim().isEmpty()) {
            return null;
        }
        try {
            DocumentBuilder builder = createSecureDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(dataAdapterXml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            if (root == null) return null;
            NodeList dataFileNodes = root.getElementsByTagName("dataFile");
            if (dataFileNodes.getLength() > 0) {
                Element dataFileEl = (Element) dataFileNodes.item(0);
                String loc = getTagText(dataFileEl, "location");
                if (loc != null && !loc.trim().isEmpty()) return loc.trim();
            }
            return getTagText(root, "location");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene el archivo de datos XML para una carta, consultando el Data Adapter si existe,
     * o recurriendo al fallback estándar resources/data/xml/{letterId}.xml.
     */
    public File getDataFileForLetter(String letterId) {
        if (!isValidLetterId(letterId)) {
            return null;
        }
        letterId = letterId.trim();
        File resourcesDir = getResourcesDir();
        File letterDir = new File(resourcesDir, "reports/" + letterId);
        File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
        if (adapterFile.exists()) {
            try {
                String adapterContent = Files.readString(adapterFile.toPath(), StandardCharsets.UTF_8);
                String loc = extractLocationFromXml(adapterContent);
                if (loc != null && !loc.trim().isEmpty()) {
                    File resolved = resolveDataFile(letterId, loc, null);
                    if (resolved != null && resolved.exists() && resolved.isFile()) {
                        return resolved;
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo leer o resolver location desde Data Adapter de carta {}: {}", letterId, e.getMessage());
            }
        }
        // Fallback estándar
        File fallback = new File(resourcesDir, "data/xml/" + letterId + ".xml");
        if (fallback.exists() && fallback.isFile()) {
            return fallback;
        }
        return null;
    }

    /**
     * Comprueba si el Data Adapter de la carta existe y su ruta declarada es válida y accesible en disco.
     */
    public boolean isDataAdapterConnected(String letterId) {
        if (!isValidLetterId(letterId)) {
            return false;
        }
        letterId = letterId.trim();
        File resourcesDir = getResourcesDir();
        File letterDir = new File(resourcesDir, "reports/" + letterId);
        File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
        if (!adapterFile.exists() || !adapterFile.isFile()) {
            return false;
        }
        try {
            String adapterContent = Files.readString(adapterFile.toPath(), StandardCharsets.UTF_8);
            String loc = extractLocationFromXml(adapterContent);
            if (loc == null || loc.trim().isEmpty()) {
                return false;
            }
            File resolved = resolveDataFile(letterId, loc, null);
            return resolved != null && resolved.exists() && resolved.isFile();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Prueba exhaustiva del Data Adapter:
     * 1. Valida la sintaxis XML del adaptador.
     * 2. Comprueba que la ruta del archivo de datos exista en disco respetando estrictamente la ruta declarada.
     * 3. Verifica que el archivo de datos sea XML bien formado.
     * 4. Evalúa la expresión XPath si fue especificada.
     */
    public TestDataAdapterResponse testDataAdapter(String letterId, String dataAdapterXml, String customXmlData) {
        if (dataAdapterXml == null || dataAdapterXml.trim().isEmpty()) {
            return TestDataAdapterResponse.error(
                    "INVALID_ADAPTER_XML",
                    "El contenido XML del Data Adapter está vacío.",
                    null,
                    Collections.emptyList()
            );
        }

        // 1. Parsear el XML del Data Adapter
        Document adapterDoc;
        try {
            DocumentBuilder builder = createSecureDocumentBuilder();
            adapterDoc = builder.parse(new ByteArrayInputStream(dataAdapterXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.warn("Error al parsear XML del Data Adapter: {}", ex.getMessage());
            return TestDataAdapterResponse.error(
                    "INVALID_ADAPTER_XML",
                    "Error de sintaxis en el XML del Data Adapter: " + ex.getMessage(),
                    null,
                    Collections.emptyList()
            );
        }

        Element root = adapterDoc.getDocumentElement();
        if (root == null || (!"xmlDataAdapter".equalsIgnoreCase(root.getTagName()) && !root.getTagName().toLowerCase().contains("adapter"))) {
            return TestDataAdapterResponse.error(
                    "INVALID_ADAPTER_XML",
                    "El elemento raíz del XML no es <xmlDataAdapter>. Encontrado: <" + (root != null ? root.getTagName() : "null") + ">",
                    null,
                    Collections.emptyList()
            );
        }

        String adapterName = getTagText(root, "name");
        String location = null;
        NodeList dataFileNodes = root.getElementsByTagName("dataFile");
        if (dataFileNodes.getLength() > 0) {
            Element dataFileEl = (Element) dataFileNodes.item(0);
            location = getTagText(dataFileEl, "location");
        }
        if (location == null || location.trim().isEmpty()) {
            location = getTagText(root, "location");
        }

        String selectExpression = getTagText(root, "selectExpression");
        String locale = getTagText(root, "locale");
        String timeZone = getTagText(root, "timeZone");

        if (location == null || location.trim().isEmpty()) {
            return TestDataAdapterResponse.error(
                    "MISSING_LOCATION",
                    "No se encontró la ruta del archivo de datos XML (<dataFile><location>...</location></dataFile>).",
                    "",
                    Collections.emptyList()
            );
        }

        // 2. Resolver la ruta física del archivo XML respetando estrictamente la ruta declarada
        List<String> testedPaths = new ArrayList<>();
        File resolvedFile = resolveDataFile(letterId, location, testedPaths);

        if (resolvedFile == null || !resolvedFile.exists()) {
            return TestDataAdapterResponse.error(
                    "FILE_NOT_FOUND",
                    "No se encontró el archivo de datos XML especificado.",
                    location,
                    Collections.emptyList()
            );
        }

        // 3. Validar el contenido XML de datos
        Document dataDoc;
        long fileSize = resolvedFile.length();
        try {
            DocumentBuilder dataBuilder = createSecureDocumentBuilder();
            dataDoc = dataBuilder.parse(resolvedFile);
        } catch (Exception ex) {
            log.warn("El archivo de datos XML está corrupto o mal formado: {}", ex.getMessage());
            TestDataAdapterResponse res = TestDataAdapterResponse.error(
                    "INVALID_DATA_XML",
                    "El archivo de datos XML está corrupto o tiene errores de sintaxis: " + ex.getMessage(),
                    location,
                    testedPaths
            );
            res.setFileExists(true);
            res.setFileSizeBytes(fileSize);
            res.setResolvedPath(toCanonicalPath(resolvedFile));
            return res;
        }

        String rootElementName = dataDoc.getDocumentElement() != null ? dataDoc.getDocumentElement().getTagName() : "desconocido";

        // 4. Evaluar la expresión XPath si fue definida
        int xpathMatches = 0;
        if (selectExpression != null && !selectExpression.trim().isEmpty()) {
            try {
                XPathFactory xPathFactory = XPathFactory.newInstance();
                XPath xpath = xPathFactory.newXPath();
                XPathExpression expr = xpath.compile(selectExpression.trim());
                NodeList nodes = (NodeList) expr.evaluate(dataDoc, XPathConstants.NODESET);
                xpathMatches = nodes != null ? nodes.getLength() : 0;
            } catch (Exception ex) {
                log.debug("Aviso al evaluar XPath {}: {}", selectExpression, ex.getMessage());
            }
        }

        String xmlContent = null;
        try {
            xmlContent = Files.readString(resolvedFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        String successMsg = "Test exitoso: Conexión establecida correctamente con el archivo de datos XML.";
        return TestDataAdapterResponse.success(
                successMsg,
                adapterName != null ? adapterName : (letterId != null ? "xmlDataAdapter_" + letterId : "xmlDataAdapter"),
                location,
                toCanonicalPath(resolvedFile),
                fileSize,
                rootElementName,
                selectExpression,
                xpathMatches,
                locale != null ? locale : "es_ES",
                timeZone != null ? timeZone : "America/Montevideo",
                xmlContent
        );
    }

    /**
     * Crea un DocumentBuilder endurecido contra XXE (XML External Entity) para parsear
     * XML de origen no confiable (Data Adapter y datos XML recibidos del cliente):
     * deshabilita DOCTYPE, entidades externas y expansión de entidades.
     */
    private static DocumentBuilder createSecureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private String getTagText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0).getTextContent() != null) {
            return list.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * Crea una nueva carta desde cero en resources/reports/{letterId}/
     */
    public LetterDetailResponse createLetter(CreateLetterRequest req) throws IOException {
        if (req.getLetterId() == null || req.getLetterId().trim().isEmpty()) {
            throw new ValidationException("El ID de la carta es obligatorio.");
        }
        String cleanId = req.getLetterId().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
        if (cleanId.isEmpty()) {
            throw new ValidationException("El ID de la carta contiene caracteres inválidos.");
        }

        File letterDir = new File(getResourcesDir(), "reports/" + cleanId);
        if (letterDir.exists()) {
            throw new ValidationException("Ya existe una carta con el identificador " + cleanId);
        }
        letterDir.mkdirs();

        String format = req.getFormat() != null && "JR7".equalsIgnoreCase(req.getFormat()) ? "JR7" : "JR6";
        String jrxmlContent = JrxmlFormatUtils.createDefaultJrxml(cleanId, format);

        // Si se solicitó crear Data Adapter
        String adapterContent = null;
        if (req.isCreateDataAdapter()) {
            String xmlLocation = "resources\\data\\xml\\" + cleanId + ".xml";
            adapterContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<xmlDataAdapter class=\"net.sf.jasperreports.data.xml.XmlDataAdapterImpl\">\n" +
                    "  <name>xmlDataAdapter_" + cleanId + "</name>\n" +
                    "  <dataFile xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"repositoryDataLocation\">\n" +
                    "    <location>" + xmlLocation + "</location>\n" +
                    "  </dataFile>\n" +
                    "  <useConnection>true</useConnection>\n" +
                    "  <namespaceAware>false</namespaceAware>\n" +
                    "  <selectExpression></selectExpression>\n" +
                    "  <locale>es_ES</locale>\n" +
                    "  <timeZone>America/Montevideo</timeZone>\n" +
                    "</xmlDataAdapter>\n";
            File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
            Files.writeString(adapterFile.toPath(), adapterContent, StandardCharsets.UTF_8);

            // Inyectar referencia en el JRXML
            jrxmlContent = JrxmlFormatUtils.injectDataAdapterProperty(jrxmlContent, "xmlDataAdapter.xml");
        }

        // Guardar JRXML
        File jrxmlFile = new File(letterDir, cleanId + ".jrxml");
        Files.writeString(jrxmlFile.toPath(), jrxmlContent, StandardCharsets.UTF_8);

        // Si se solicitó crear Datos XML
        if (req.isCreateXmlData()) {
            File xmlDataDir = new File(getResourcesDir(), "data/xml");
            if (!xmlDataDir.exists()) {
                xmlDataDir.mkdirs();
            }
            File xmlFile = new File(xmlDataDir, cleanId + ".xml");
            if (!xmlFile.exists()) {
                String starterXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<content>\n" +
                        "\t<letterType>" + cleanId + "</letterType>\n" +
                        "\t<letterContents>\n" +
                        "\t\t<letterTypeData>\n" +
                        "\t\t\t<letterData>\n" +
                        "\t\t\t\t<idLetterFormat>1001</idLetterFormat>\n" +
                        "\t\t\t\t<printDate>" + java.time.LocalDate.now().toString() + "</printDate>\n" +
                        "\t\t\t</letterData>\n" +
                        "\t\t\t<argumentList>\n" +
                        "\t\t\t\t<argumentData>\n" +
                        "\t\t\t\t\t<argumentName>LETTER_TYPE</argumentName>\n" +
                        "\t\t\t\t\t<argumentValue>" + cleanId + "</argumentValue>\n" +
                        "\t\t\t\t</argumentData>\n" +
                        "\t\t\t</argumentList>\n" +
                        "\t\t</letterTypeData>\n" +
                        "\t</letterContents>\n" +
                        "\t<language>es</language>\n" +
                        "\t<extTemplate>" + cleanId + "</extTemplate>\n" +
                        "</content>\n";
                Files.writeString(xmlFile.toPath(), starterXml, StandardCharsets.UTF_8);
            }
        }

        // Copiar un logo estándar si existe en otra carta (ej: ETIPLET002/enersa_4.png)
        try {
            File sampleLogo = new File(getResourcesDir(), "reports/ETIPLET002/enersa_4.png");
            if (sampleLogo.exists()) {
                Files.copy(sampleLogo.toPath(), new File(letterDir, "enersa_4.png").toPath());
            }
        } catch (Exception ignored) {
        }

        return getLetterDetail(cleanId);
    }

    /**
     * Lista todos los Data Adapters existentes en reports/
     */
    public List<DataAdapterOptionInfo> getAvailableDataAdapters() {
        List<DataAdapterOptionInfo> list = new ArrayList<>();
        File reportsDir = new File(getResourcesDir(), "reports");
        if (!reportsDir.exists() || !reportsDir.isDirectory()) return list;

        File[] subdirs = reportsDir.listFiles(File::isDirectory);
        if (subdirs == null) return list;

        for (File dir : subdirs) {
            File adapterFile = new File(dir, "xmlDataAdapter.xml");
            if (adapterFile.exists() && adapterFile.isFile()) {
                String letterId = dir.getName();
                String location = null;
                try {
                    String content = Files.readString(adapterFile.toPath(), StandardCharsets.UTF_8);
                    location = extractLocationFromXml(content);
                } catch (Exception ignored) {
                }
                list.add(new DataAdapterOptionInfo(
                        "Data Adapter " + letterId,
                        "reports/" + letterId + "/xmlDataAdapter.xml",
                        letterId,
                        location
                ));
            }
        }
        return list;
    }

    /**
     * Lista todos los archivos XML de datos disponibles en resources/data/xml/
     */
    public List<DataFileInfo> getAvailableDataXmlFiles() {
        List<DataFileInfo> list = new ArrayList<>();
        File xmlDir = new File(getResourcesDir(), "data/xml");
        if (!xmlDir.exists() || !xmlDir.isDirectory()) return list;

        File[] files = xmlDir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
        if (files == null) return list;

        for (File f : files) {
            list.add(new DataFileInfo(
                    f.getName(),
                    "resources\\data\\xml\\" + f.getName(),
                    f.length()
            ));
        }
        return list;
    }

    /**
     * Crea un nuevo archivo de datos XML en resources/data/xml/
     */
    public DataFileInfo createDataXmlFile(String letterId, String fileName, String starterContent) throws IOException {
        String cleanName = (fileName != null && !fileName.trim().isEmpty())
                ? fileName.trim()
                : (letterId != null ? letterId + ".xml" : "data.xml");
        if (!cleanName.toLowerCase().endsWith(".xml")) {
            cleanName += ".xml";
        }

        File xmlDir = new File(getResourcesDir(), "data/xml");
        if (!xmlDir.exists()) {
            xmlDir.mkdirs();
        }

        File xmlFile = new File(xmlDir, cleanName);
        String content = (starterContent != null && !starterContent.trim().isEmpty())
                ? starterContent
                : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<content>\n\t<letterType>" + (letterId != null ? letterId : "DATA") + "</letterType>\n</content>\n";

        Files.writeString(xmlFile.toPath(), content, StandardCharsets.UTF_8);
        return new DataFileInfo(cleanName, "resources\\data\\xml\\" + cleanName, xmlFile.length());
    }
}
