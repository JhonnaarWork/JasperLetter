package com.jasperletter.preview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura unitaria directa de DataFileResolver, extraído de LetterResourceService
 * (hallazgo A-5) por ser la lógica más sensible en materia de seguridad del proyecto (S-1 XXE,
 * S-2 lectura arbitraria de archivos). A diferencia de LetterResourceServiceTest, que verifica
 * el mismo comportamiento a través de la API pública del servicio (la integración), estos tests
 * ejercitan el algoritmo de resolución en sí mismo, sin dependencias de configuración de Spring.
 */
class DataFileResolverTest {

    @TempDir
    Path tempDir;

    private DataFileResolver resolver;
    private Path resourcesDir;

    @BeforeEach
    void setUp() throws IOException {
        resolver = new DataFileResolver();
        resourcesDir = tempDir.resolve("resources");
        Files.createDirectories(resourcesDir.resolve("data/xml"));
        Files.createDirectories(resourcesDir.resolve("reports/ETIPLET_TEST"));
        Files.createDirectories(tempDir.resolve("outside"));
    }

    // ---------- resolveDataFile: contención dentro de resourcesDir (regresión S-2) ----------

    @Test
    void resolvesRelativeLocationInsideResources() throws IOException {
        Files.writeString(resourcesDir.resolve("data/xml/ETIPLET_TEST.xml"), "<content/>", StandardCharsets.UTF_8);

        File resolved = resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", "data/xml/ETIPLET_TEST.xml", null);

        assertNotNull(resolved);
        assertTrue(resolved.exists());
    }

    @Test
    void resolvesLegacyDotDotPathRelativeToLetterDir() throws IOException {
        // Caso real de ETIPLET003: "../../data/xml/X.xml" navega desde reports/{letterId}/
        // de vuelta a resources/data/xml/ — debe seguir resolviendo tras el fix de S-2.
        Files.writeString(resourcesDir.resolve("data/xml/ETIPLET_TEST.xml"), "<content/>", StandardCharsets.UTF_8);

        File resolved = resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", "../../data/xml/ETIPLET_TEST.xml", null);

        assertNotNull(resolved, "La navegación \"..\" legítima dentro de resources/ debe seguir resolviendo");
    }

    @Test
    void rejectsRelativePathTraversalEscapingResourcesDir() throws IOException {
        Files.writeString(tempDir.resolve("outside/secret.xml"), "<secret/>", StandardCharsets.UTF_8);

        File resolved = resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", "../../../outside/secret.xml", null);

        assertNull(resolved, "No debe resolver un archivo fuera de resourcesDir, aunque exista en disco");
    }

    @Test
    void rejectsAbsolutePathOutsideResourcesDir() throws IOException {
        Path secret = tempDir.resolve("outside/secret.xml");
        Files.writeString(secret, "<secret/>", StandardCharsets.UTF_8);

        File resolved = resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", secret.toAbsolutePath().toString(), null);

        assertNull(resolved, "No debe resolver una ruta absoluta fuera de resourcesDir, aunque exista en disco");
    }

    @Test
    void returnsNull_whenLocationIsBlank() {
        assertNull(resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", "", null));
        assertNull(resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", null, null));
    }

    @Test
    void returnsNull_whenLocationDoesNotExistAnywhere() {
        File resolved = resolver.resolveDataFile(resourcesDir.toFile(), "ETIPLET_TEST", "data/xml/no-existe.xml", null);
        assertNull(resolved);
    }

    // ---------- extractLocationFromXml y endurecimiento XXE (regresión S-1) ----------

    @Test
    void extractLocationFromXml_readsDeclaredLocation() {
        String adapterXml = "<xmlDataAdapter>"
                + "<dataFile><location>data/xml/ETIPLET_TEST.xml</location></dataFile>"
                + "</xmlDataAdapter>";

        assertEquals("data/xml/ETIPLET_TEST.xml", resolver.extractLocationFromXml(adapterXml));
    }

    @Test
    void extractLocationFromXml_fallsBackToRootLevelLocation_whenNoDataFileElement() {
        String adapterXml = "<xmlDataAdapter><location>data/xml/ETIPLET_TEST.xml</location></xmlDataAdapter>";

        assertEquals("data/xml/ETIPLET_TEST.xml", resolver.extractLocationFromXml(adapterXml));
    }

    @Test
    void extractLocationFromXml_returnsNull_forBlankOrMalformedInput() {
        assertNull(resolver.extractLocationFromXml(null));
        assertNull(resolver.extractLocationFromXml(""));
        assertNull(resolver.extractLocationFromXml("<xmlDataAdapter"));
    }

    @Test
    void extractLocationFromXml_rejectsXxePayload_returnsNullInsteadOfResolvingEntity() throws IOException {
        Path secret = tempDir.resolve("outside/secret.txt");
        Files.writeString(secret, "CONTENIDO SECRETO", StandardCharsets.UTF_8);
        String secretUri = secret.toUri().toString();

        String maliciousXml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE xmlDataAdapter [<!ENTITY xxe SYSTEM \"" + secretUri + "\">]>"
                + "<xmlDataAdapter><dataFile><location>&xxe;</location></dataFile></xmlDataAdapter>";

        // Con disallow-doctype-decl activo, el parseo falla y el método devuelve null: nunca debe
        // devolver el contenido del archivo referenciado por la entidad externa.
        assertNull(resolver.extractLocationFromXml(maliciousXml));
    }

    // ---------- isExplicitlyAbsolute ----------

    @Test
    void isExplicitlyAbsolute_detectsWindowsDriveLetterAndUncPaths() {
        assertTrue(resolver.isExplicitlyAbsolute("C:\\Windows\\System32\\drivers\\etc\\hosts"));
        assertTrue(resolver.isExplicitlyAbsolute("\\\\server\\share\\file.xml"));
    }

    @Test
    void isExplicitlyAbsolute_returnsFalseForRelativePaths() {
        assertFalse(resolver.isExplicitlyAbsolute("data/xml/ETIPLET_TEST.xml"));
        assertFalse(resolver.isExplicitlyAbsolute("../../data/xml/ETIPLET_TEST.xml"));
        assertFalse(resolver.isExplicitlyAbsolute(null));
        assertFalse(resolver.isExplicitlyAbsolute("   "));
    }

    // ---------- toCanonicalPath ----------

    @Test
    void toCanonicalPath_returnsNull_forNullFile() {
        assertNull(resolver.toCanonicalPath(null));
    }

    @Test
    void toCanonicalPath_collapsesDotDotSegments() {
        File f = new File(resourcesDir.toFile(), "reports/ETIPLET_TEST/../../data/xml");
        String canonical = resolver.toCanonicalPath(f);
        assertFalse(canonical.contains(".."), "La ruta canónica no debe contener \"..\"");
    }
}
