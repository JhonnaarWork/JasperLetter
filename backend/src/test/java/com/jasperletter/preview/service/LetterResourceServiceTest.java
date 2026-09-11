package com.jasperletter.preview.service;

import com.jasperletter.preview.dto.TestDataAdapterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura unitaria de LetterResourceService, aislada del resources/ real del proyecto
 * mediante un directorio temporal (@TempDir + ReflectionTestUtils sobre configuredResourcesDir).
 *
 * Prioriza tests de regresión para los hallazgos de seguridad ya corregidos (S-1 XXE, S-2
 * lectura arbitraria de archivos, S-3 sanitización de letterId), de forma que un cambio futuro
 * que reintroduzca alguno de esos problemas falle aquí antes de llegar a producción.
 */
class LetterResourceServiceTest {

    @TempDir
    Path tempDir;

    private LetterResourceService service;
    private Path resourcesDir;

    @BeforeEach
    void setUp() throws IOException {
        service = new LetterResourceService();
        resourcesDir = tempDir.resolve("resources");
        Files.createDirectories(resourcesDir.resolve("data/xml"));
        Files.createDirectories(resourcesDir.resolve("reports/ETIPLET_TEST"));
        Files.createDirectories(tempDir.resolve("outside"));

        ReflectionTestUtils.setField(service, "configuredResourcesDir", resourcesDir.toString());
    }

    private void writeUnderResources(String relativePath, String content) throws IOException {
        Path file = resourcesDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // ---------- letterId validation (regresión S-3) ----------

    @Test
    void isValidLetterId_acceptsAlphanumericUnderscoreHyphen() {
        assertTrue(service.isValidLetterId("ETIPLET003"));
        assertTrue(service.isValidLetterId("carta_01-beta"));
    }

    @Test
    void isValidLetterId_rejectsPathTraversalAndSeparators() {
        assertFalse(service.isValidLetterId(".."));
        assertFalse(service.isValidLetterId("../../etc"));
        assertFalse(service.isValidLetterId("a/b"));
        assertFalse(service.isValidLetterId("a\\b"));
        assertFalse(service.isValidLetterId(null));
        assertFalse(service.isValidLetterId(""));
        assertFalse(service.isValidLetterId("   "));
    }

    @Test
    void requireValidLetterId_throwsForInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> service.requireValidLetterId("../../etc/passwd"));
    }

    @Test
    void getDataFileForLetter_returnsNull_forInvalidLetterId_insteadOfThrowing() {
        assertNull(service.getDataFileForLetter("../../etc/passwd"));
    }

    @Test
    void isDataAdapterConnected_returnsFalse_forInvalidLetterId_insteadOfThrowing() {
        assertFalse(service.isDataAdapterConnected("../../etc"));
    }

    // ---------- resolveDataFile: contención dentro de resources/ (regresión S-2) ----------

    @Test
    void resolveDataFile_resolvesLegitimateRelativeLocationInsideResources() throws IOException {
        writeUnderResources("data/xml/ETIPLET_TEST.xml", "<content/>");

        File resolved = service.resolveDataFile("ETIPLET_TEST", "data/xml/ETIPLET_TEST.xml", null);

        assertNotNull(resolved);
        assertTrue(resolved.exists());
    }

    @Test
    void resolveDataFile_resolvesLegacyPathRelativeToLetterDir() throws IOException {
        // Caso real de ETIPLET003: "../../data/xml/X.xml" navega desde reports/{letterId}/
        // de vuelta a resources/data/xml/ — debe seguir funcionando tras el fix de S-2.
        writeUnderResources("data/xml/ETIPLET_TEST.xml", "<content/>");

        File resolved = service.resolveDataFile("ETIPLET_TEST", "../../data/xml/ETIPLET_TEST.xml", null);

        assertNotNull(resolved, "La navegación \"..\" legítima dentro de resources/ debe seguir resolviendo");
    }

    @Test
    void resolveDataFile_rejectsPathTraversalEscapingResourcesDir() throws IOException {
        Files.writeString(tempDir.resolve("outside/secret.xml"), "<secret/>", StandardCharsets.UTF_8);

        File resolved = service.resolveDataFile("ETIPLET_TEST", "../../../outside/secret.xml", null);

        assertNull(resolved, "No debe resolver un archivo fuera de resources/, aunque exista en disco");
    }

    @Test
    void resolveDataFile_rejectsAbsolutePathOutsideResourcesDir() throws IOException {
        Path secret = tempDir.resolve("outside/secret.xml");
        Files.writeString(secret, "<secret/>", StandardCharsets.UTF_8);

        File resolved = service.resolveDataFile("ETIPLET_TEST", secret.toAbsolutePath().toString(), null);

        assertNull(resolved, "No debe resolver una ruta absoluta fuera de resources/, aunque exista en disco");
    }

    @Test
    void resolveDataFile_returnsNull_whenLocationIsBlank() {
        assertNull(service.resolveDataFile("ETIPLET_TEST", "", null));
        assertNull(service.resolveDataFile("ETIPLET_TEST", null, null));
    }

    // ---------- extractLocationFromXml y endurecimiento XXE (regresión S-1) ----------

    @Test
    void extractLocationFromXml_readsDeclaredLocation() {
        String adapterXml = "<xmlDataAdapter>"
                + "<dataFile><location>data/xml/ETIPLET_TEST.xml</location></dataFile>"
                + "</xmlDataAdapter>";

        assertEquals("data/xml/ETIPLET_TEST.xml", service.extractLocationFromXml(adapterXml));
    }

    @Test
    void extractLocationFromXml_rejectsXxePayload_returnsNullInsteadOfResolvingEntity() throws IOException {
        Path secret = tempDir.resolve("outside/secret.txt");
        Files.writeString(secret, "CONTENIDO SECRETO", StandardCharsets.UTF_8);
        String secretUri = secret.toUri().toString();

        String maliciousXml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE xmlDataAdapter [<!ENTITY xxe SYSTEM \"" + secretUri + "\">]>"
                + "<xmlDataAdapter><dataFile><location>&xxe;</location></dataFile></xmlDataAdapter>";

        // Con disallow-doctype-decl activo, el parseo falla y el método devuelve null:
        // nunca debe devolver el contenido del archivo referenciado por la entidad externa.
        assertNull(service.extractLocationFromXml(maliciousXml));
    }

    // ---------- testDataAdapter: regresión end-to-end de S-1 + S-2 ----------

    @Test
    void testDataAdapter_success_forLegitimateAdapter() throws IOException {
        writeUnderResources("data/xml/ETIPLET_TEST.xml", "<content><letterType>ETIPLET_TEST</letterType></content>");
        String adapterXml = "<xmlDataAdapter>"
                + "<name>xmlDataAdapter_ETIPLET_TEST</name>"
                + "<dataFile><location>data/xml/ETIPLET_TEST.xml</location></dataFile>"
                + "</xmlDataAdapter>";

        TestDataAdapterResponse result = service.testDataAdapter("ETIPLET_TEST", adapterXml, null);

        assertTrue(result.isSuccess());
        assertEquals("content", result.getRootElement());
    }

    @Test
    void testDataAdapter_pathTraversalLocation_isReportedAsFileNotFound_neverLeaksContent() throws IOException {
        Path secret = tempDir.resolve("outside/secret.xml");
        Files.writeString(secret, "<secret>NO DEBERIA VERSE</secret>", StandardCharsets.UTF_8);

        String adapterXml = "<xmlDataAdapter>"
                + "<dataFile><location>" + secret.toAbsolutePath() + "</location></dataFile>"
                + "</xmlDataAdapter>";

        TestDataAdapterResponse result = service.testDataAdapter("ETIPLET_TEST", adapterXml, null);

        assertFalse(result.isSuccess());
        assertEquals("FILE_NOT_FOUND", result.getStatus());
        assertNull(result.getXmlContent(), "El contenido de un archivo fuera de resources/ nunca debe llegar en la respuesta");
    }

    @Test
    void testDataAdapter_malformedXml_reportsInvalidAdapterXml() {
        TestDataAdapterResponse result = service.testDataAdapter("ETIPLET_TEST", "<xmlDataAdapter", null);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_ADAPTER_XML", result.getStatus());
    }
}
