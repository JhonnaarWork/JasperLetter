package com.jasperletter.preview.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Genera un JRXML de layout estático a partir de la PRIMERA página de un PDF: extrae cada
 * fragmento de texto con su posición y tamaño de fuente y lo vuelca como un <staticText> en esas
 * mismas coordenadas. Es deliberadamente solo un punto de partida visual — un PDF no conserva
 * qué texto era un campo dinámico, ni bandas repetibles, ni condicionales; eso lo agrega después
 * quien edita la carta (ver el toggle estático/funcional en el modal de edición de texto).
 */
@Service
public class PdfToJrxmlService {

    private static final float DEFAULT_FONT_SIZE = 10f;
    /** Altura mínima de la caja de un staticText, como múltiplo de su tamaño de fuente — ver el
     *  comentario en el cálculo de TextRun.height sobre por qué hace falta este mínimo. */
    private static final float MIN_HEIGHT_PER_FONT_SIZE = 1.6f;
    private static final float BOTTOM_MARGIN = 20f;

    public String generateJrxmlFromPdf(byte[] pdfBytes, String letterId) throws IOException {
        List<TextRun> runs;
        float pageWidth;
        float pageHeight;

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("El PDF no tiene páginas.");
            }
            PDPage firstPage = document.getPage(0);
            PDRectangle mediaBox = firstPage.getMediaBox();
            pageWidth = mediaBox.getWidth();
            pageHeight = mediaBox.getHeight();

            RunCollectingStripper stripper = new RunCollectingStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(document);
            runs = stripper.runs;
        }

        return buildJrxml(letterId, pageWidth, pageHeight, runs);
    }

    private String buildJrxml(String letterId, float pageWidth, float pageHeight, List<TextRun> runs) {
        float maxBottom = 0f;
        for (TextRun run : runs) {
            maxBottom = Math.max(maxBottom, run.y + run.height);
        }
        int bandHeight = Math.max(50, Math.round(maxBottom + BOTTOM_MARGIN));

        StringBuilder elements = new StringBuilder();
        for (TextRun run : runs) {
            elements.append("\t\t\t<staticText>\n")
                    .append("\t\t\t\t<reportElement x=\"").append(Math.round(run.x))
                    .append("\" y=\"").append(Math.round(run.y))
                    .append("\" width=\"").append(Math.max(1, Math.round(run.width)))
                    .append("\" height=\"").append(Math.max(1, Math.round(run.height)))
                    .append("\" uuid=\"").append(UUID.randomUUID()).append("\"/>\n")
                    .append("\t\t\t\t<textElement>\n")
                    .append("\t\t\t\t\t<font size=\"").append(Math.round(run.fontSize)).append("\"/>\n")
                    .append("\t\t\t\t</textElement>\n")
                    .append("\t\t\t\t<text><![CDATA[").append(escapeCdata(run.text)).append("]]></text>\n")
                    .append("\t\t\t</staticText>\n");
        }

        String uuidReport = UUID.randomUUID().toString();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                // whenNoDataType="AllSectionsNoDetail": sin este atributo el valor por defecto de
                // JasperReports deja el reporte COMPLETO en blanco (título incluido) cuando la
                // fuente de datos no tiene filas — y una carta recién importada de un PDF no
                // tiene ninguna (no hay <letterTabs> en su XML de datos todavía). Con este valor,
                // el título imprime igual; solo se saltaría <detail>, que aquí ni se usa.
                "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" name=\"" + letterId + "\" pageWidth=\"" + Math.round(pageWidth) + "\" pageHeight=\"" + Math.round(pageHeight) + "\" columnWidth=\"" + Math.round(pageWidth) + "\" leftMargin=\"0\" rightMargin=\"0\" topMargin=\"0\" bottomMargin=\"0\" whenNoDataType=\"AllSectionsNoDetail\" uuid=\"" + uuidReport + "\">\n" +
                "\t<property name=\"net.sf.jasperreports.export.pdf.force.linebreak.policy\" value=\"true\"/>\n" +
                "\t<property name=\"net.sf.jasperreports.awt.ignore.missing.font\" value=\"true\"/>\n" +
                "\t<background>\n" +
                "\t\t<band splitType=\"Stretch\"/>\n" +
                "\t</background>\n" +
                // El layout se pone en <title>, no en <detail>: <detail> imprime una vez POR
                // FILA de la fuente de datos, y una carta recién importada de un PDF no tiene
                // ninguna fila todavía (no hay <letterTabs> en su XML de datos hasta que se
                // agreguen fields) — con <detail> el PDF salía prácticamente en blanco. <title>
                // siempre imprime exactamente una vez, sin depender de la fuente de datos.
                "\t<title>\n" +
                "\t\t<band height=\"" + bandHeight + "\" splitType=\"Stretch\">\n" +
                elements +
                "\t\t</band>\n" +
                "\t</title>\n" +
                "</jasperReport>\n";
    }

    private static String escapeCdata(String text) {
        return text == null ? "" : text.replace("]]>", "]]]]><![CDATA[>");
    }

    private record TextRun(String text, float x, float y, float width, float height, float fontSize) {
    }

    /**
     * Agrupa cada fragmento de texto contiguo (una llamada de writeString ya es una agrupación
     * razonable hecha por la propia librería, ligada a cambios de fuente/formato) en un TextRun
     * con posición top-left (coordenadas de JasperReports): TextPosition.getXDirAdj()/
     * getYDirAdj() ya están en esa convención (0,0 arriba a la izquierda), así que no hace
     * falta invertir contra la altura de página.
     */
    private static final class RunCollectingStripper extends PDFTextStripper {
        private final List<TextRun> runs = new ArrayList<>();

        RunCollectingStripper() throws IOException {
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (text == null || text.trim().isEmpty() || textPositions.isEmpty()) {
                return;
            }

            float minX = Float.MAX_VALUE;
            float minTop = Float.MAX_VALUE;
            float maxRight = -Float.MAX_VALUE;
            float maxBottom = -Float.MAX_VALUE;
            float maxFontSize = DEFAULT_FONT_SIZE;

            for (TextPosition tp : textPositions) {
                // getYDirAdj()/getXDirAdj() ya están en coordenadas top-left (0,0 arriba a la
                // izquierda) y getYDirAdj() ya es el TOPE de la caja del glifo, no la línea base
                // (ver TextPosition.isBelow/isAfter en la fuente: "y-coordinate is top of
                // TextPosition", bottom = top + getHeightDir()).
                float left = tp.getXDirAdj();
                float top = tp.getYDirAdj();
                float glyphHeight = Math.max(tp.getHeightDir(), 1f);
                float right = left + tp.getWidthDirAdj();
                float bottom = top + glyphHeight;

                minX = Math.min(minX, left);
                minTop = Math.min(minTop, top);
                maxRight = Math.max(maxRight, right);
                maxBottom = Math.max(maxBottom, bottom);
                maxFontSize = Math.max(maxFontSize, tp.getFontSizeInPt());
            }

            float width = Math.max(1f, maxRight - minX);
            // JasperReports no recorta un staticText cuya caja sea más baja de lo que su fuente
            // necesita para una sola línea: directamente NO dibuja nada (comprobado en vivo: una
            // caja de 7px con fuente 10pt salía completamente invisible, sin excepción ni aviso).
            // El bounding box real de los glifos (maxBottom - minTop) es demasiado ajustado para
            // esto — se usa el tamaño de fuente como base, con margen generoso para ascendentes/
            // descendentes, en vez de esa medida.
            float height = Math.max(maxBottom - minTop, maxFontSize * MIN_HEIGHT_PER_FONT_SIZE);
            runs.add(new TextRun(text, minX, minTop, width, height, maxFontSize));
        }
    }
}
