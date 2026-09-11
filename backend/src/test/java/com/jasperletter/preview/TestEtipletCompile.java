package com.jasperletter.preview;

import com.jasperletter.preview.dto.LetterDetailResponse;
import com.jasperletter.preview.dto.LetterResourceInfo;
import com.jasperletter.preview.service.JasperReportService;
import com.jasperletter.preview.service.LetterResourceService;
import com.jasperletter.preview.service.SanitizingFileRepositoryService;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.repo.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestEtipletCompile {

    @Test
    void testLetterResourceServiceAndPreview() {
        try {
            LetterResourceService resourceService = new LetterResourceService();
            assertNotNull(resourceService.getResourcesDir());
            assertTrue(resourceService.getResourcesDir().exists());

            // 1. Listar cartas
            List<LetterResourceInfo> letters = resourceService.getAvailableLetters();
            System.out.println("Cartas encontradas: " + letters.size());
            assertFalse(letters.isEmpty(), "Debería encontrar al menos la carta ETIPLET003");

            LetterResourceInfo info = letters.stream()
                    .filter(l -> "ETIPLET003".equals(l.getId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(info);
            System.out.println("Carta ETIPLET003 detectada con formato: " + info.getFormat());
            assertEquals("JR6", info.getFormat(), "El formato original debe ser JR6");
            assertTrue(info.isHasDataAdapter());
            assertTrue(info.isHasXmlData());

            // 2. Obtener detalle
            LetterDetailResponse detail = resourceService.getLetterDetail("ETIPLET003");
            assertNotNull(detail);
            assertNotNull(detail.getJrxml());
            assertNotNull(detail.getXmlData());
            assertNotNull(detail.getDataAdapter());
            System.out.println("JRXML longitud: " + detail.getJrxml().length());

            // 3. Generar preview con JasperReportService pasando plantilla JR6
            JasperReportService reportService = new JasperReportService(resourceService);
            byte[] pdfBytes = reportService.generatePdfPreview(detail.getJrxml(), null, "ETIPLET003", null);
            assertNotNull(pdfBytes);
            assertTrue(pdfBytes.length > 1000, "El PDF generado debe tener contenido válido (> 1000 bytes)");
            System.out.println("¡PDF generado exitosamente desde JR6! Tamaño: " + pdfBytes.length + " bytes");

            // 4. Probar con XML de datos personalizado
            String customXml = detail.getXmlData().replace("JORGE DANIEL GONZALEZ", "JUAN PEREZ MODIFICADO");
            byte[] customPdfBytes = reportService.generatePdfPreview(detail.getJrxml(), null, "ETIPLET003", customXml);
            assertNotNull(customPdfBytes);
            assertTrue(customPdfBytes.length > 1000);
            // 5. Probar con JRXML generado por ngx-jrxml-editor (que contiene isForPrompting y <element kind="...">)
            File editorJrxmlFile = new File(resourceService.getResourcesDir(), "reports/ETIPLET003/ETIPLET003_jr7.jrxml");
            if (editorJrxmlFile.exists()) {
                String editorJrxml = java.nio.file.Files.readString(editorJrxmlFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(editorJrxml.contains("isForPrompting=\"false\""), "Debe contener isForPrompting para verificar su normalización");
                byte[] editorPdfBytes = reportService.generatePdfPreview(editorJrxml, null, "ETIPLET003", null);
                assertNotNull(editorPdfBytes);
                assertTrue(editorPdfBytes.length > 1000);
                System.out.println("¡PDF generado exitosamente desde la salida del editor (ETIPLET003_jr7)! Tamaño: " + editorPdfBytes.length + " bytes");

                // 6. Probar con un nuevo textField con expresión '$F{}' (default del palette del editor)
                String withEmptyField = editorJrxml.replace("</pageHeader>",
                        "<element kind=\"textField\" x=\"200\" y=\"10\" width=\"100\" height=\"20\"><expression><![CDATA[$F{}]]></expression></element></pageHeader>");
                byte[] pdfWithEmptyField = reportService.generatePdfPreview(withEmptyField, null, "ETIPLET003", null);
                assertNotNull(pdfWithEmptyField);
                assertTrue(pdfWithEmptyField.length > 1000);
                System.out.println("¡PDF con nuevo textField ($F{}) generado exitosamente! Tamaño: " + pdfWithEmptyField.length + " bytes");

                // 7. Probar con un nuevo textField con texto literal sin comillas ("Nuevo texto")
                String withUnquotedText = editorJrxml.replace("</pageHeader>",
                        "<element kind=\"textField\" x=\"200\" y=\"10\" width=\"100\" height=\"20\"><expression><![CDATA[Nuevo texto]]></expression></element></pageHeader>");
                byte[] pdfWithUnquoted = reportService.generatePdfPreview(withUnquotedText, null, "ETIPLET003", null);
                assertNotNull(pdfWithUnquoted);
                assertTrue(pdfWithUnquoted.length > 1000);
                System.out.println("¡PDF con nuevo textField (texto sin comillas) generado exitosamente! Tamaño: " + pdfWithUnquoted.length + " bytes");
            }
        } catch (Exception e) {
            System.err.println("ERROR EN PRUEBA:");
            e.printStackTrace();
            fail("Error en prueba: " + e.getMessage(), e);
        }
    }

    @Test
    void testConvertedPageHeader() {
        try {
            LetterResourceService resourceService = new LetterResourceService();
            LetterDetailResponse detail = resourceService.getLetterDetail("ETIPLET003");
            String jr7 = com.jasperletter.preview.util.JrxmlFormatUtils.convertToJr7(detail.getJrxml());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<pageHeader[\\s\\S]*?<\\/pageHeader>").matcher(jr7);
            if (m.find()) {
                System.out.println("=== JR7 PAGEHEADER START ===");
                System.out.println(m.group(0));
                System.out.println("=== JR7 PAGEHEADER END ===");
            }

            JasperReportService reportService = new JasperReportService(resourceService);
            byte[] pdf = reportService.generatePdfPreview(detail.getJrxml(), null, "ETIPLET003", null);
            System.out.println("PDF generated, length: " + pdf.length);

            JasperPrint print = reportService.generateJasperPrint(detail.getJrxml(), null, "ETIPLET003", null);
            System.out.println("Print pages: " + print.getPages().size());
            JRPrintPage page = print.getPages().get(0);
            System.out.println("Page 1 elements count: " + page.getElements().size());
            for (JRPrintElement elem : page.getElements()) {
                if (elem instanceof JRPrintText) {
                    JRPrintText txt = (JRPrintText) elem;
                    System.out.println("PRINT TEXT [x=" + txt.getX() + ", y=" + txt.getY() + ", w=" + txt.getWidth() + ", h=" + txt.getHeight() + "]: '" + txt.getOriginalText() + "'");
                }
            }

            JasperReportsContext defaultContext = DefaultJasperReportsContext.getInstance();
            java.awt.image.BufferedImage img = (java.awt.image.BufferedImage) JasperPrintManager.getInstance(defaultContext).printToImage(print, 0, 1.5f);
            File pngFile = new File("C:/Users/jnnateraa/.gemini/antigravity/brain/a4b14f41-d2ef-4493-b114-fbd0e1dde648/scratch/page_preview.png");
            javax.imageio.ImageIO.write(img, "PNG", pngFile);
            System.out.println("Saved page PNG: " + pngFile.getAbsolutePath() + " (exists=" + pngFile.exists() + ", size=" + pngFile.length() + ")");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        }
    }
}
