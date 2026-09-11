package com.jasperletter.preview.service;

import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.RepositoryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SanitizingFileRepositoryService extends FileRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(SanitizingFileRepositoryService.class);
    private final File rootDir;

    public SanitizingFileRepositoryService(JasperReportsContext context, String root, boolean resolveAbsolute) {
        super(context, root, resolveAbsolute);
        this.rootDir = new File(root);
    }

    @Override
    public InputStream getInputStream(RepositoryContext context, String location) {
        InputStream is = super.getInputStream(context, location);

        // Si falló la resolución normal de super (por ejemplo por contener .. en la ruta), intentar resolver directamente contra rootDir
        if (is == null && location != null && !location.trim().isEmpty()) {
            File directFile = new File(rootDir, location);
            if (directFile.exists()) {
                try {
                    is = directFile.toURI().toURL().openStream();
                } catch (IOException e) {
                    log.debug("No se pudo abrir stream directo para {}: {}", directFile, e.getMessage());
                }
            }
        }

        if (is != null && location != null && location.endsWith(".jrtx")) {
            try {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // En JasperReports 7, JacksonReportLoader rechaza templates con xmlns y nombres antiguos de atributos
                String sanitized = content
                        .replaceAll("xmlns=\"[^\"]*\"", "")
                        .replaceAll("xmlns:xsi=\"[^\"]*\"", "")
                        .replaceAll("xsi:schemaLocation=\"[^\"]*\"", "")
                        .replaceAll("\\bisBold=", "bold=")
                        .replaceAll("\\bisItalic=", "italic=")
                        .replaceAll("\\bisUnderline=", "underline=")
                        .replaceAll("\\bisDefault=", "default=")
                        .replaceAll("\\bisStrikeThrough=", "strikeThrough=")
                        .replaceAll("\\bisPdfEmbedded=", "pdfEmbedded=");
                return new ByteArrayInputStream(sanitized.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                log.warn("Error al sanitizar jrtx en memoria: {}", ex.getMessage());
            }
        }

        return is;
    }
}
