package com.jasperletter.preview.service;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve, de forma segura, la ubicación física de un archivo de datos XML a partir de la
 * ruta ("location") declarada en un Data Adapter — contenido que siempre proviene del cliente
 * y nunca debe tratarse como confiable.
 *
 * Extraído de LetterResourceService (hallazgo A-5: ese servicio concentraba demasiadas
 * responsabilidades) porque esta es, además, la lógica más sensible desde el punto de vista de
 * seguridad de todo el proyecto: aquí se corrigieron XXE (S-1) y lectura arbitraria de archivos
 * (S-2). Aislarla en su propia clase, sin dependencias de configuración de Spring, la hace más
 * fácil de encontrar, revisar y testear de forma directa.
 */
@Component
public class DataFileResolver {

    /**
     * Resuelve el archivo físico de datos XML basándose estrictamente en la ruta declarada en el
     * Data Adapter, sin realizar suposiciones arbitrarias ni extracción por substring:
     * - Si la ruta es absoluta, solo se busca en esa ruta absoluta.
     * - Si la ruta es relativa, se busca respetando la ruta declarada relativa a:
     *   1. Raíz del workspace (padre de resourcesDir)
     *   2. resourcesDir
     *   3. Directorio de la carta (resourcesDir/reports/{letterId}/)
     * En todos los casos, el archivo resuelto debe quedar contenido dentro de resourcesDir:
     * "location" proviene de contenido enviado por el cliente y no es de confianza, por lo que
     * cualquier candidato que caiga fuera de ese límite (vía ruta absoluta ajena o secuencias
     * "..") se descarta en vez de leerse.
     */
    public File resolveDataFile(File resourcesDir, String letterId, String location, List<String> testedPathsOut) {
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
            if (fAbs.exists() && fAbs.isFile() && isWithinResourcesDir(resourcesDir, fAbs)) {
                return fAbs;
            }
            return null;
        }

        // 2. Ruta relativa: limpiar barras iniciales de rutas de repositorio (ej: "/data/xml/..." -> "data/xml/...")
        String relPath = trimmed.replace('\\', '/').replaceAll("^/+", "");
        File workspaceRoot = resourcesDir != null ? resourcesDir.getParentFile() : null;
        File letterDir = (letterId != null && !letterId.trim().isEmpty() && resourcesDir != null)
                ? new File(resourcesDir, RepositoryLayout.letterDirPath(letterId))
                : null;

        // A. Relativo a la raíz del workspace (ej: resources/data/xml/ETIPLET002.xml)
        if (workspaceRoot != null && workspaceRoot.exists()) {
            File fWs = new File(workspaceRoot, relPath);
            String canonWs = toCanonicalPath(fWs);
            if (!tested.contains(canonWs)) {
                tested.add(canonWs);
            }
            if (fWs.exists() && fWs.isFile() && isWithinResourcesDir(resourcesDir, fWs)) {
                return fWs;
            }
        }

        // B. Relativo a resourcesDir (ej: data/xml/ETIPLET002.xml)
        if (resourcesDir != null && resourcesDir.exists()) {
            File fRes = new File(resourcesDir, relPath);
            String canonRes = toCanonicalPath(fRes);
            if (!tested.contains(canonRes)) {
                tested.add(canonRes);
            }
            if (fRes.exists() && fRes.isFile() && isWithinResourcesDir(resourcesDir, fRes)) {
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
            if (fLetter.exists() && fLetter.isFile() && isWithinResourcesDir(resourcesDir, fLetter)) {
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
     * Comprueba que un archivo candidato quede realmente contenido dentro de resourcesDir,
     * resolviendo enlaces simbólicos y secuencias ".." antes de comparar. Se usa para evitar
     * que una ruta declarada por el cliente (absoluta o con "..") pueda escapar del directorio
     * de recursos y exponer lectura arbitraria de archivos del servidor.
     */
    private boolean isWithinResourcesDir(File resourcesDir, File candidate) {
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
     * Crea un DocumentBuilder endurecido contra XXE (XML External Entity) para parsear XML de
     * origen no confiable (Data Adapter y datos XML recibidos del cliente): deshabilita DOCTYPE,
     * entidades externas y expansión de entidades. Público porque LetterResourceService también
     * lo necesita al validar el contenido del Data Adapter y del archivo de datos en
     * testDataAdapter(): una sola fuente de verdad para "cómo parseamos XML no confiable aquí".
     */
    public DocumentBuilder createSecureDocumentBuilder() throws ParserConfigurationException {
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

    /**
     * Lee el texto de la primera ocurrencia de una etiqueta hija. Público porque
     * LetterResourceService también lo usa en testDataAdapter() sobre su propio Document parseado.
     */
    public String getTagText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0).getTextContent() != null) {
            return list.item(0).getTextContent().trim();
        }
        return null;
    }
}
