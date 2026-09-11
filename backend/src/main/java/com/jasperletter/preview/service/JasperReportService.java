package com.jasperletter.preview.service;

import com.jasperletter.preview.util.JrxmlFormatUtils;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.repo.FileRepositoryPersistenceServiceFactory;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.PersistenceServiceFactory;
import net.sf.jasperreports.repo.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JasperReportService {

    private static final Logger log = LoggerFactory.getLogger(JasperReportService.class);

    private final LetterResourceService letterResourceService;

    public JasperReportService(LetterResourceService letterResourceService) {
        this.letterResourceService = letterResourceService;
    }

    /**
     * Compila y rellena la plantilla JRXML en memoria y la exporta a un arreglo de bytes PDF.
     * Soporta tanto cartas independientes con parámetros como cartas vinculadas a resources/ con DataAdapter y XML.
     *
     * @param jrxmlContent Contenido XML del reporte (.jrxml en JR6 o JR7)
     * @param parameters   Mapa con los parámetros de prueba para la carta
     * @param letterId     ID de la carta en resources/ (opcional)
     * @param customXmlData Datos XML editados por el usuario en tiempo real (opcional)
     * @return Arreglo de bytes del PDF generado
     * Compila y rellena la plantilla JRXML en memoria y retorna un JasperPrint.
     */
    public JasperPrint generateJasperPrint(String jrxmlContent, Map<String, Object> parameters, String letterId, String customXmlData) throws Exception {
        if (jrxmlContent == null || jrxmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("El contenido JRXML no puede estar vacío");
        }
        File resourcesDir = letterResourceService.getResourcesDir();
        log.info("Generando JasperPrint de reporte (letterId: {}, resourcesDir: {})...", letterId, resourcesDir.getAbsolutePath());

        // 1. Configurar contexto de JasperReports heredando DefaultJasperReportsContext para tener ReportLoader, funciones, etc.
        JasperReportsContext defaultContext = DefaultJasperReportsContext.getInstance();
        SimpleJasperReportsContext context = new SimpleJasperReportsContext(defaultContext);
        if (resourcesDir.exists()) {
            SanitizingFileRepositoryService fileRepo = new SanitizingFileRepositoryService(context, resourcesDir.getAbsolutePath(), true);
            FileRepositoryPersistenceServiceFactory factory = FileRepositoryPersistenceServiceFactory.getInstance();

            List<RepositoryService> repoServices = new ArrayList<>();
            repoServices.add(fileRepo);
            List<RepositoryService> defaultRepos = defaultContext.getExtensions(RepositoryService.class);
            if (defaultRepos != null) {
                repoServices.addAll(defaultRepos);
            }
            context.setExtensions(RepositoryService.class, repoServices);

            List<PersistenceServiceFactory> factories = new ArrayList<>();
            factories.add(factory);
            List<PersistenceServiceFactory> defaultFactories = defaultContext.getExtensions(PersistenceServiceFactory.class);
            if (defaultFactories != null) {
                factories.addAll(defaultFactories);
            }
            context.setExtensions(PersistenceServiceFactory.class, factories);
        }

        // 2. Normalizar el JRXML para compilación en JasperReports 7 (soporta JR6, JR7 y formato híbrido de ngx-jrxml-editor)
        log.info("Normalizando plantilla JRXML para motor de renderizado JasperReports 7...");
        String jr7Xml = JrxmlFormatUtils.convertToJr7(jrxmlContent);

        // 3. Compilar el reporte con el contexto
        byte[] jrxmlBytes = jr7Xml.getBytes(StandardCharsets.UTF_8);
        JasperReport jasperReport;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(jrxmlBytes)) {
            jasperReport = JasperCompileManager.getInstance(context).compile(inputStream);
        } catch (Exception ex) {
            log.error("Error al compilar JRXML en JasperReports: {}", ex.getMessage(), ex);
            throw ex;
        }

        // 4. Preparar parámetros del reporte
        Map<String, Object> reportParams = new HashMap<>();
        if (parameters != null) {
            reportParams.putAll(parameters);
        }

        if (letterId != null && !letterId.trim().isEmpty()) {
            File letterDir = new File(resourcesDir, "reports/" + letterId);
            if (letterDir.exists()) {
                reportParams.put("SUBREPORT_DIR", "reports/" + letterId + "/");
                File logoFile = new File(letterDir, "enersa_4.png");
                if (logoFile.exists()) {
                    reportParams.put("URL_LOGO", "reports/" + letterId + "/enersa_4.png");
                }
                File adapterFile = new File(letterDir, "xmlDataAdapter.xml");
                if (adapterFile.exists()) {
                    reportParams.put("net.sf.jasperreports.data.adapter", adapterFile.getAbsolutePath().replace("\\", "/"));
                }
            }
        }

        // 5. Llenar el reporte
        JasperPrint jasperPrint;
        String xmlDataToUse = customXmlData;
        if ((xmlDataToUse == null || xmlDataToUse.trim().isEmpty()) && letterId != null) {
            File xmlDataFile = letterResourceService.getDataFileForLetter(letterId);
            if (xmlDataFile != null && xmlDataFile.exists()) {
                log.info("Cargando datos XML asociados a la carta desde: {}", xmlDataFile.getAbsolutePath());
                xmlDataToUse = java.nio.file.Files.readString(xmlDataFile.toPath(), StandardCharsets.UTF_8);
            }
        }

        if (xmlDataToUse != null && !xmlDataToUse.trim().isEmpty()) {
            log.info("Llenando reporte con fuente de datos JRXmlDataSource...");
            String queryStr = "/content/letterContents/letterTypeData/letterTabs";
            if (jasperReport.getQuery() != null && jasperReport.getQuery().getText() != null) {
                queryStr = jasperReport.getQuery().getText().trim();
            }
            JRXmlDataSource xmlDataSource = new JRXmlDataSource(
                    new ByteArrayInputStream(xmlDataToUse.getBytes(StandardCharsets.UTF_8)),
                    queryStr
            );
            jasperPrint = JasperFillManager.getInstance(context).fill(jasperReport, reportParams, xmlDataSource);
        } else {
            // Reporte simple sin origen de datos
            log.info("Llenando reporte simple con JREmptyDataSource...");
            jasperPrint = JasperFillManager.getInstance(context).fill(jasperReport, reportParams, new JREmptyDataSource(1));
        }

        return jasperPrint;
    }

    /**
     * Compila y rellena la plantilla JRXML en memoria y la exporta a un arreglo de bytes PDF.
     *
     * @param jrxmlContent Contenido XML del reporte (.jrxml en JR6 o JR7)
     * @param parameters   Mapa con los parámetros de prueba para la carta
     * @param letterId     ID de la carta en resources/ (opcional)
     * @param customXmlData Datos XML editados por el usuario en tiempo real (opcional)
     * @return Arreglo de bytes del PDF generado
     */
    public byte[] generatePdfPreview(String jrxmlContent, Map<String, Object> parameters, 
                                     String letterId, String customXmlData) throws Exception {
        JasperPrint jasperPrint = generateJasperPrint(jrxmlContent, parameters, letterId, customXmlData);
        
        JasperReportsContext defaultContext = DefaultJasperReportsContext.getInstance();
        SimpleJasperReportsContext context = new SimpleJasperReportsContext(defaultContext);

        // 6. Exportar a PDF
        log.info("Exportando reporte a PDF...");
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        JasperExportManager.getInstance(context).exportToPdfStream(jasperPrint, pdfOutputStream);

        byte[] pdfBytes = pdfOutputStream.toByteArray();
        log.info("PDF generado exitosamente ({} bytes).", pdfBytes.length);
        return pdfBytes;
    }
}
