package com.jasperletter.preview;

import com.jasperletter.preview.dto.LetterDetailResponse;
import com.jasperletter.preview.dto.LetterResourceInfo;
import com.jasperletter.preview.service.JasperReportService;
import com.jasperletter.preview.service.LetterResourceService;
import com.jasperletter.preview.util.JrxmlFormatUtils;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración de extremo a extremo contra el motor real de JasperReports y el
 * resources/ del proyecto — a diferencia de JrxmlFormatUtilsTest y LetterResourceServiceTest,
 * que son unitarios y están aislados en un directorio temporal, este ejercita la carta
 * ETIPLET003 tal como existe en disco para verificar que la cadena completa (lectura ->
 * normalización JR6/JR7 -> compilación -> llenado -> exportación a PDF) sigue funcionando
 * de punta a punta.
 */
class EtipletReportIntegrationTest {

    private static final String LETTER_ID = "ETIPLET003";

    @Test
    void compilesAndFillsEtiplet003FromResourcesInAllSupportedInputForms() throws Exception {
        LetterResourceService resourceService = new LetterResourceService();
        assertTrue(resourceService.getResourcesDir().exists());

        // 1. Listar cartas
        List<LetterResourceInfo> letters = resourceService.getAvailableLetters();
        LetterResourceInfo info = letters.stream()
                .filter(l -> LETTER_ID.equals(l.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(info, "Debería encontrar la carta " + LETTER_ID + " en resources/reports/");
        assertEquals("JR6", info.getFormat(), "El formato original en disco debe ser JR6");
        assertTrue(info.isHasDataAdapter());
        assertTrue(info.isHasXmlData());

        // 2. Obtener detalle
        LetterDetailResponse detail = resourceService.getLetterDetail(LETTER_ID);
        assertNotNull(detail.getJrxml());
        assertNotNull(detail.getXmlData());
        assertNotNull(detail.getDataAdapter());

        JasperReportService reportService = new JasperReportService(resourceService);

        // 3. Compilar y llenar desde JR6 tal cual está en disco
        byte[] pdfBytes = reportService.generatePdfPreview(detail.getJrxml(), null, LETTER_ID, null);
        assertTrue(pdfBytes.length > 1000, "El PDF generado debe tener contenido válido (> 1000 bytes)");

        // 4. Con XML de datos personalizado en tiempo real
        String customXml = detail.getXmlData().replace("JORGE DANIEL GONZALEZ", "JUAN PEREZ MODIFICADO");
        byte[] customPdfBytes = reportService.generatePdfPreview(detail.getJrxml(), null, LETTER_ID, customXml);
        assertTrue(customPdfBytes.length > 1000);

        // 5. Con JRXML en formato JR7 (salida real del editor visual: isForPrompting, <element kind="...">)
        File editorJrxmlFile = new File(resourceService.getResourcesDir(), "reports/" + LETTER_ID + "/" + LETTER_ID + "_jr7.jrxml");
        Assumptions.assumeTrue(editorJrxmlFile.exists(),
                "Fixture opcional no presente, se omite la verificación del formato del editor: " + editorJrxmlFile);

        String editorJrxml = Files.readString(editorJrxmlFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(editorJrxml.contains("isForPrompting=\"false\""),
                "El fixture debe contener isForPrompting para verificar su normalización a JR6");

        byte[] editorPdfBytes = reportService.generatePdfPreview(editorJrxml, null, LETTER_ID, null);
        assertTrue(editorPdfBytes.length > 1000);

        // 6. Con un textField nuevo cuya expresión es '$F{}' (default del palette del editor)
        String withEmptyField = editorJrxml.replace("</pageHeader>",
                "<element kind=\"textField\" x=\"200\" y=\"10\" width=\"100\" height=\"20\">"
                        + "<expression><![CDATA[$F{}]]></expression></element></pageHeader>");
        byte[] pdfWithEmptyField = reportService.generatePdfPreview(withEmptyField, null, LETTER_ID, null);
        assertTrue(pdfWithEmptyField.length > 1000);

        // 7. Con un textField nuevo cuya expresión es texto literal sin comillas
        String withUnquotedText = editorJrxml.replace("</pageHeader>",
                "<element kind=\"textField\" x=\"200\" y=\"10\" width=\"100\" height=\"20\">"
                        + "<expression><![CDATA[Nuevo texto]]></expression></element></pageHeader>");
        byte[] pdfWithUnquoted = reportService.generatePdfPreview(withUnquotedText, null, LETTER_ID, null);
        assertTrue(pdfWithUnquoted.length > 1000);
    }

    @Test
    void convertToJr7NormalizesPageHeaderAndFillProducesPrintableElements() throws Exception {
        LetterResourceService resourceService = new LetterResourceService();
        LetterDetailResponse detail = resourceService.getLetterDetail(LETTER_ID);

        String jr7 = JrxmlFormatUtils.convertToJr7(detail.getJrxml());
        Matcher m = Pattern.compile("<pageHeader[\\s\\S]*?<\\/pageHeader>").matcher(jr7);
        assertTrue(m.find(), "El JRXML normalizado a JR7 debe conservar un <pageHeader>");

        JasperReportService reportService = new JasperReportService(resourceService);
        JasperPrint print = reportService.generateJasperPrint(detail.getJrxml(), null, LETTER_ID, null);

        assertFalse(print.getPages().isEmpty(), "El reporte llenado debe producir al menos una página");
        JRPrintPage page = print.getPages().get(0);
        assertFalse(page.getElements().isEmpty(), "La primera página debe contener elementos impresos");

        boolean hasPrintedText = page.getElements().stream()
                .filter(JRPrintText.class::isInstance)
                .map(JRPrintText.class::cast)
                .anyMatch(txt -> txt.getOriginalText() != null && !txt.getOriginalText().isBlank());
        assertTrue(hasPrintedText, "Debe haber al menos un elemento de texto con contenido en la página");
    }
}
