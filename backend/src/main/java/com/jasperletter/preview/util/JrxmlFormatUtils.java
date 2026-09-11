package com.jasperletter.preview.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JrxmlFormatUtils {

    private static final String[] SINGLE_BANDS = {
            "background", "title", "pageHeader", "columnHeader",
            "columnFooter", "pageFooter", "lastPageFooter", "summary", "noData"
    };

    private JrxmlFormatUtils() {
    }

    /**
     * Detecta si un contenido JRXML está en formato JR6 (tradicional) o JR7 (moderno).
     */
    public static String detectFormat(String jrxml) {
        if (jrxml == null) {
            return "JR6";
        }
        if (jrxml.contains("<element ") || jrxml.contains("<element\n") || jrxml.contains("<element\r")) {
            return "JR7";
        }
        return "JR6";
    }

    /**
     * Convierte una plantilla JRXML en sintaxis JR7 a sintaxis tradicional JR6.
     */
    public static String convertToJr6(String jr7Xml) {
        if (jr7Xml == null || jr7Xml.trim().isEmpty()) {
            return jr7Xml;
        }

        String xml = jr7Xml;

        // 1. Convertir <description> en <field> a <fieldDescription>
        xml = Pattern.compile("<field\\b([^>]*)>([\\s\\S]*?)<\\/field>").matcher(xml).replaceAll(mr -> {
            return mr.group().replaceAll("<description\\b([^>]*)>([\\s\\S]*?)<\\/description>",
                    "<fieldDescription$1>$2</fieldDescription>");
        });

        // 2. Convertir <query a <queryString
        xml = xml.replaceAll("<query\\b([^>]*)>([\\s\\S]*?)<\\/query>", "<queryString$1>$2</queryString>");

        // 3. Convertir forPrompting a isForPrompting
        xml = xml.replaceAll("\\bforPrompting=", "isForPrompting=");

        // 4. Envolver secciones de banda simple en <band> si no lo tienen
        for (String section : SINGLE_BANDS) {
            // Auto-cerradas: <background height="0" splitType="Stretch"/>
            String selfClosingPattern = "<" + section + "\\b([^>]*?)\\/>";
            xml = Pattern.compile(selfClosingPattern).matcher(xml).replaceAll(mr ->
                    "<" + section + ">\n\t\t<band" + mr.group(1) + "/>\n\t</" + section + ">"
            );

            // Con contenido: <pageHeader height="71" splitType="Stretch">...</pageHeader>
            String sectionPattern = "<" + section + "\\b([^>]*?)>([\\s\\S]*?)<\\/" + section + ">";
            Pattern p = Pattern.compile(sectionPattern);
            Matcher m = p.matcher(xml);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String attrs = m.group(1);
                String body = m.group(2);
                if (body.trim().startsWith("<band")) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                } else {
                    String replacement = "<" + section + ">\n\t\t<band" + attrs + ">" + body + "\n\t\t</band>\n\t</" + section + ">";
                    m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            }
            m.appendTail(sb);
            xml = sb.toString();
        }

        // 5. Convertir elementos compactos <element ...> a <tipo><reportElement .../>...</tipo>
        Pattern elemPattern = Pattern.compile("<element\\b([^>]*?)>([\\s\\S]*?)<\\/element>");
        Matcher elemMatcher = elemPattern.matcher(xml);
        StringBuffer elemSb = new StringBuffer();
        while (elemMatcher.find()) {
            String allAttrs = elemMatcher.group(1);
            String inner = elemMatcher.group(2);

            Matcher kindMatcher = Pattern.compile("kind=\"([^\"]+)\"").matcher(allAttrs);
            if (!kindMatcher.find()) {
                elemMatcher.appendReplacement(elemSb, Matcher.quoteReplacement(elemMatcher.group(0)));
                continue;
            }
            String kind = kindMatcher.group(1);
            String cleanAttrs = allAttrs.replace(kindMatcher.group(0), "").replaceAll("\\s+", " ").trim();

            if ("textField".equals(kind)) {
                inner = inner.replaceAll("<expression\\b([^>]*)>([\\s\\S]*?)<\\/expression>",
                        "<textFieldExpression$1>$2</textFieldExpression>");
            } else if ("image".equals(kind)) {
                inner = inner.replaceAll("<expression\\b([^>]*)>([\\s\\S]*?)<\\/expression>",
                        "<imageExpression$1>$2</imageExpression>");
            } else if ("staticText".equals(kind)) {
                Matcher tm = Pattern.compile("<text\\b([^>]*)>([\\s\\S]*?)<\\/text>").matcher(inner);
                if (tm.find()) {
                    String clean = sanitizeStaticTextContent(tm.group(2));
                    inner = tm.replaceFirst("<text" + tm.group(1) + "><![CDATA[" + Matcher.quoteReplacement(clean) + "]]></text>");
                }
            }

            String rep = "<" + kind + ">\n\t\t\t<reportElement " + cleanAttrs + "/>" + inner + "\n\t\t</" + kind + ">";
            elemMatcher.appendReplacement(elemSb, Matcher.quoteReplacement(rep));
        }
        elemMatcher.appendTail(elemSb);
        xml = elemSb.toString();

        // 6. Elementos auto-cerrados: <element .../>
        Pattern selfElemPattern = Pattern.compile("<element\\b([^>]*?)\\/>");
        Matcher selfMatcher = selfElemPattern.matcher(xml);
        StringBuffer selfSb = new StringBuffer();
        while (selfMatcher.find()) {
            String allAttrs = selfMatcher.group(1);
            Matcher kindMatcher = Pattern.compile("kind=\"([^\"]+)\"").matcher(allAttrs);
            if (!kindMatcher.find()) {
                selfMatcher.appendReplacement(selfSb, Matcher.quoteReplacement(selfMatcher.group(0)));
                continue;
            }
            String kind = kindMatcher.group(1);
            String cleanAttrs = allAttrs.replace(kindMatcher.group(0), "").replaceAll("\\s+", " ").trim();
            String rep = "<" + kind + ">\n\t\t\t<reportElement " + cleanAttrs + "/>\n\t\t</" + kind + ">";
            selfMatcher.appendReplacement(selfSb, Matcher.quoteReplacement(rep));
        }
        selfMatcher.appendTail(selfSb);
        xml = selfSb.toString();

        if (!xml.contains("xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"")) {
            xml = xml.replaceFirst("<jasperReport\\b", "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"");
        }

        return xml;
    }

    /**
     * Convierte y normaliza cualquier plantilla JRXML (JR6 puro, JR7 moderno o híbrido emitido por ngx-jrxml-editor)
     * a sintaxis moderna JR7 para su compilación en JasperReports 7.
     */
    public static String convertToJr7(String rawXml) {
        if (rawXml == null || rawXml.trim().isEmpty()) {
            return rawXml;
        }

        String xml = rawXml;

        // 1. Quitar namespaces que bloquean al JacksonReportLoader
        xml = xml.replaceAll("xmlns=\"[^\"]*\"", "")
                 .replaceAll("xmlns:xsi=\"[^\"]*\"", "")
                 .replaceAll("xsi:schemaLocation=\"[^\"]*\"", "");

        // 2. Normalizar atributos para JRDesignParameter, JRDesignStyle y elementos en JR7
        xml = xml.replaceAll("\\bisForPrompting=", "forPrompting=")
                 .replaceAll("\\bisBold=", "bold=")
                 .replaceAll("\\bisItalic=", "italic=")
                 .replaceAll("\\bisUnderline=", "underline=")
                 .replaceAll("\\bisStrikeThrough=", "strikeThrough=")
                 .replaceAll("\\bisDefault=", "default=")
                 .replaceAll("\\bisPdfEmbedded=", "pdfEmbedded=")
                 .replaceAll("\\bisBlankWhenNull=", "blankWhenNull=");

        // 2.1 Normalizar UUIDs para evitar errores de deserialización en JR7 Jackson
        Pattern uuidPattern = Pattern.compile("\\buuid=\"([^\"]*)\"");
        Matcher uuidMatcher = uuidPattern.matcher(xml);
        StringBuffer uuidSb = new StringBuffer();
        Pattern validUuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        while (uuidMatcher.find()) {
            String val = uuidMatcher.group(1);
            if (!validUuidPattern.matcher(val).matches()) {
                uuidMatcher.appendReplacement(uuidSb, "uuid=\"" + java.util.UUID.randomUUID().toString() + "\"");
            }
        }
        uuidMatcher.appendTail(uuidSb);
        xml = uuidSb.toString();

        // 3. Normalizar etiquetas de consulta, campos y expresiones
        xml = xml.replaceAll("<queryString\\b", "<query")
                 .replaceAll("<\\/queryString>", "</query>")
                 .replaceAll("<fieldDescription\\b", "<description")
                 .replaceAll("<\\/fieldDescription>", "</description>")
                 .replaceAll("<textFieldExpression\\b", "<expression")
                 .replaceAll("<\\/textFieldExpression>", "</expression>")
                 .replaceAll("<imageExpression\\b", "<expression")
                 .replaceAll("<\\/imageExpression>", "</expression>")
                 .replaceAll("/\\*\\s*@path=[^*]*\\*/\\s*\\(FLOOR\\([^)]*\\)\\s*==[^)]*\\)\\s*\\?\\s*([^:]+?)\\s*:\\s*\\1", "$1")
                 .replaceAll("<template><!\\[CDATA\\[.*incms_styles\\.jrtx.*\\]\\]><\\/template>", "<template><![CDATA[\"reports/incms_styles.jrtx\"]]></template>");

        // 4. Limpiar propiedades de estudio que apunten a rutas relativas locales de Studio/Eclipse
        xml = xml.replaceAll("(?i)<property\\s+name=\"com\\.jaspersoft\\.studio\\.data\\.defaultdataadapter\"[^>]*/>", "")
                 .replaceAll("(?i)<property\\s+name=\"net\\.sf\\.jasperreports\\.data\\.adapter\"[^>]*/>", "");

        // 5. Desanidar <band> en secciones simples si todavía lo tienen
        for (String section : SINGLE_BANDS) {
            Pattern p = Pattern.compile("<" + section + "[^>]*>\\s*<band([^>]*)>([\\s\\S]*?)<\\/band>\\s*<\\/" + section + ">");
            Matcher m = p.matcher(xml);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String bandAttrs = m.group(1);
                String bandContent = m.group(2).trim();
                String rep;
                if (bandContent.isEmpty()) {
                    rep = "<" + section + bandAttrs + "/>";
                } else {
                    rep = "<" + section + bandAttrs + ">\n" + bandContent + "\n</" + section + ">";
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            xml = sb.toString();

            // Caso auto-cerrado con band vacío: <section><band .../></section>
            Pattern pEmpty = Pattern.compile("<" + section + "[^>]*>\\s*<band([^>]*?)\\/>\\s*<\\/" + section + ">");
            xml = pEmpty.matcher(xml).replaceAll("<" + section + "$1/>");
        }

        // 6. Convertir elementos JR6 tradicionales a <element kind="..."> si existen
        if (xml.contains("<staticText") || xml.contains("<textField") || xml.contains("<image")
                || xml.contains("<line") || xml.contains("<rectangle") || xml.contains("<ellipse")) {
            xml = convertElementJr6ToJr7(xml, "staticText", "text", "text");
            xml = convertElementJr6ToJr7(xml, "textField", "textFieldExpression", "expression");
            xml = convertElementJr6ToJr7(xml, "image", "imageExpression", "expression");
            xml = convertElementJr6ToJr7(xml, "line", null, null);
            xml = convertElementJr6ToJr7(xml, "rectangle", null, null);
            xml = convertElementJr6ToJr7(xml, "ellipse", null, null);
        }

        // 7. Sanitizar expresiones en textField (sanitizar '$F{}', '$P{}' y envolver texto literal sin comillas)
        Pattern exprPattern = Pattern.compile("<expression\\b([^>]*)>([\\s\\S]*?)<\\/expression>");
        Matcher exprMatcher = exprPattern.matcher(xml);
        StringBuffer exprSb = new StringBuffer();
        while (exprMatcher.find()) {
            String exprAttrs = exprMatcher.group(1);
            String rawInner = exprMatcher.group(2);
            String cdataContent = null;
            Matcher cdataMatcher = Pattern.compile("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>").matcher(rawInner);
            if (cdataMatcher.find()) {
                cdataContent = cdataMatcher.group(1);
            } else {
                cdataContent = rawInner;
            }

            String sanitized = sanitizeExpression(cdataContent);
            String replacement = "<expression" + exprAttrs + "><![CDATA[" + sanitized + "]]></expression>";
            exprMatcher.appendReplacement(exprSb, Matcher.quoteReplacement(replacement));
        }
        exprMatcher.appendTail(exprSb);
        xml = exprSb.toString();

        // 8. Sanitizar contenido de <text> en staticText (quitar saltos de línea de indentación antes/después del texto)
        Pattern textPattern = Pattern.compile("<text\\b([^>]*)>([\\s\\S]*?)<\\/text>");
        Matcher textMatcher = textPattern.matcher(xml);
        StringBuffer textSb = new StringBuffer();
        while (textMatcher.find()) {
            String textAttrs = textMatcher.group(1);
            String rawTextContent = textMatcher.group(2);
            String cleanedText = sanitizeStaticTextContent(rawTextContent);
            String replacement = "<text" + textAttrs + "><![CDATA[" + cleanedText + "]]></text>";
            textMatcher.appendReplacement(textSb, Matcher.quoteReplacement(replacement));
        }
        textMatcher.appendTail(textSb);
        xml = textSb.toString();

        return xml;
    }

    /**
     * Sanitiza el contenido textual de una etiqueta <text> para eliminar saltos de línea
     * y espacios provenientes de la indentación del archivo XML que empujan el texto hacia
     * abajo o hacen que se corte dentro de cajas de altura reducida.
     */
    public static String sanitizeStaticTextContent(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        Matcher cdMatcher = Pattern.compile("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>").matcher(raw);
        if (cdMatcher.find()) {
            text = cdMatcher.group(1);
        }
        // Quitar saltos de línea y espacios de formateo XML accidentales al inicio y al final
        text = text.replaceAll("^[\\r\\n]+[\\t ]*", "").replaceAll("[\\t ]*[\\r\\n]+$", "");
        return text;
    }

    /**
     * Sanitiza una expresión de campo o parámetro para evitar errores de compilación en JasperReports.
     * Si el usuario ingresó texto simple sin comillas, lo envuelve en comillas para que Java lo evalúe como String.
     * Si la expresión está vacía o es '$F{}'/'$P{}', devuelve '""'.
     */
    public static String sanitizeExpression(String expr) {
        if (expr == null) {
            return "\"\"";
        }
        String trimmed = expr.trim();
        if (trimmed.isEmpty() || "$F{}".equals(trimmed) || "$P{}".equals(trimmed) || "$V{}".equals(trimmed)) {
            return "\"\"";
        }
        // Si ya empieza con comilla doble
        if (trimmed.startsWith("\"")) {
            return trimmed;
        }
        // Si contiene identificadores de JasperReports ($F{X}, $P{X}, $V{X}, $R{X})
        if (Pattern.compile("\\$[FPVR]\\{[a-zA-Z0-9_]+\\}").matcher(trimmed).find()) {
            return trimmed;
        }
        // Si contiene operadores o llamadas Java
        if (trimmed.contains("+") || trimmed.contains("?") || trimmed.startsWith("(") || trimmed.startsWith("new ")
                || trimmed.contains(".replace") || trimmed.contains(".substring") || trimmed.contains(".toString")
                || trimmed.startsWith("String.") || trimmed.startsWith("Boolean.") || trimmed.startsWith("Integer.")) {
            return trimmed;
        }
        // Números, booleanos o null
        if ("true".equals(trimmed) || "false".equals(trimmed) || "null".equals(trimmed) || trimmed.matches("^-?\\d+(\\.\\d+)?$")) {
            return trimmed;
        }

        // Es texto literal ingresado por el usuario sin comillas
        String escaped = trimmed.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String convertElementJr6ToJr7(String xml, String oldTag, String oldExprTag, String newExprTag) {
        Pattern pattern = Pattern.compile("<" + oldTag + "\\b([^>]*)>([\\s\\S]*?)<\\/" + oldTag + ">");
        Matcher matcher = pattern.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String outerAttrs = matcher.group(1);
            String content = matcher.group(2);

            // Extraer reportElement
            Pattern repElemPattern = Pattern.compile("<reportElement\\b([^>]*?)\\/>");
            Matcher repMatcher = repElemPattern.matcher(content);
            String repAttrs = "";
            if (repMatcher.find()) {
                repAttrs = repMatcher.group(1);
            }
            // Extraer textElement y font si existen
            StringBuilder extraAttrs = new StringBuilder();
            Pattern txtElemPattern = Pattern.compile("<textElement\\b([^>]*)>([\\s\\S]*?)<\\/textElement>|<textElement\\b([^>]*?)\\/>");
            Matcher txtMatcher = txtElemPattern.matcher(content);
            if (txtMatcher.find()) {
                String txtAttrs = txtMatcher.group(1) != null ? txtMatcher.group(1) : (txtMatcher.group(3) != null ? txtMatcher.group(3) : "");
                String innerTxt = txtMatcher.group(2) != null ? txtMatcher.group(2) : "";

                // textAlignment -> hTextAlign
                Matcher hAlign = Pattern.compile("textAlignment=\"([^\"]+)\"").matcher(txtAttrs);
                if (hAlign.find()) {
                    extraAttrs.append(" hTextAlign=\"").append(hAlign.group(1)).append("\"");
                }
                // verticalAlignment -> vTextAlign
                Matcher vAlign = Pattern.compile("verticalAlignment=\"([^\"]+)\"").matcher(txtAttrs);
                if (vAlign.find()) {
                    extraAttrs.append(" vTextAlign=\"").append(vAlign.group(1)).append("\"");
                }

                // font tag
                Matcher fontMatcher = Pattern.compile("<font\\b([^>]*?)\\/>|<font\\b([^>]*)>").matcher(innerTxt);
                if (fontMatcher.find()) {
                    String fontAttrs = fontMatcher.group(1) != null ? fontMatcher.group(1) : fontMatcher.group(2);
                    Matcher fName = Pattern.compile("fontName=\"([^\"]+)\"").matcher(fontAttrs);
                    if (fName.find()) extraAttrs.append(" fontName=\"").append(fName.group(1)).append("\"");
                    Matcher fSize = Pattern.compile("size=\"([^\"]+)\"").matcher(fontAttrs);
                    if (fSize.find()) extraAttrs.append(" fontSize=\"").append(fSize.group(1)).append("\"");
                    Matcher fBold = Pattern.compile("isBold=\"true\"").matcher(fontAttrs);
                    if (fBold.find()) extraAttrs.append(" bold=\"true\"");
                    Matcher fItalic = Pattern.compile("isItalic=\"true\"").matcher(fontAttrs);
                    if (fItalic.find()) extraAttrs.append(" italic=\"true\"");
                    Matcher fUnderline = Pattern.compile("isUnderline=\"true\"").matcher(fontAttrs);
                    if (fUnderline.find()) extraAttrs.append(" underline=\"true\"");
                }
            }
            String remainingContent = txtElemPattern.matcher(repMatcher.replaceAll("")).replaceAll("").trim();

            if (oldExprTag != null && newExprTag != null && !oldExprTag.equals(newExprTag)) {
                remainingContent = remainingContent
                        .replaceAll("<" + oldExprTag + "\\b([^>]*)>", "<" + newExprTag + "$1>")
                        .replaceAll("<\\/" + oldExprTag + ">", "</" + newExprTag + ">");
            }

            String allAttrs = (outerAttrs + " " + repAttrs + extraAttrs.toString()).trim().replaceAll("\\s+", " ");
            String rep;
            if (remainingContent.isEmpty()) {
                rep = "<element kind=\"" + oldTag + "\" " + allAttrs + "/>";
            } else {
                rep = "<element kind=\"" + oldTag + "\" " + allAttrs + ">\n" + remainingContent + "\n</element>";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Crea una plantilla JRXML base para una nueva carta, con formato JR6 o JR7.
     */
    public static String createDefaultJrxml(String letterId, String format) {
        String cleanId = letterId != null ? letterId.trim() : "NUEVA_CARTA";
        String uuid = java.util.UUID.randomUUID().toString();
        String uuidTitle = java.util.UUID.randomUUID().toString();
        String uuidLogo = java.util.UUID.randomUUID().toString();

        String jr6Template = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" name=\"" + cleanId + "\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" uuid=\"" + uuid + "\">\n" +
                "\t<property name=\"net.sf.jasperreports.export.pdf.force.linebreak.policy\" value=\"true\"/>\n" +
                "\t<property name=\"net.sf.jasperreports.awt.ignore.missing.font\" value=\"true\"/>\n" +
                "\t<parameter name=\"CURRENCIES\" class=\"java.util.ArrayList\" isForPrompting=\"false\"/>\n" +
                "\t<parameter name=\"SUBREPORT_DIR\" class=\"java.lang.String\" isForPrompting=\"false\">\n" +
                "\t\t<defaultValueExpression><![CDATA[\"reports/" + cleanId + "/\"]]></defaultValueExpression>\n" +
                "\t</parameter>\n" +
                "\t<parameter name=\"URL_LOGO\" class=\"java.lang.String\" isForPrompting=\"false\">\n" +
                "\t\t<defaultValueExpression><![CDATA[\"reports/" + cleanId + "/enersa_4.png\"]]></defaultValueExpression>\n" +
                "\t</parameter>\n" +
                "\t<background>\n" +
                "\t\t<band splitType=\"Stretch\"/>\n" +
                "\t</background>\n" +
                "\t<pageHeader>\n" +
                "\t\t<band height=\"50\" splitType=\"Stretch\">\n" +
                "\t\t\t<image>\n" +
                "\t\t\t\t<reportElement x=\"0\" y=\"0\" width=\"140\" height=\"45\" uuid=\"" + uuidLogo + "\"/>\n" +
                "\t\t\t\t<imageExpression><![CDATA[$P{URL_LOGO}]]></imageExpression>\n" +
                "\t\t\t</image>\n" +
                "\t\t</band>\n" +
                "\t</pageHeader>\n" +
                "\t<detail>\n" +
                "\t\t<band height=\"150\" splitType=\"Stretch\">\n" +
                "\t\t\t<staticText>\n" +
                "\t\t\t\t<reportElement x=\"0\" y=\"20\" width=\"555\" height=\"30\" uuid=\"" + uuidTitle + "\"/>\n" +
                "\t\t\t\t<textElement>\n" +
                "\t\t\t\t\t<font size=\"16\" isBold=\"true\"/>\n" +
                "\t\t\t\t</textElement>\n" +
                "\t\t\t\t<text><![CDATA[Carta " + cleanId + "]]></text>\n" +
                "\t\t\t</staticText>\n" +
                "\t\t</band>\n" +
                "\t</detail>\n" +
                "\t<pageFooter>\n" +
                "\t\t<band height=\"40\" splitType=\"Stretch\"/>\n" +
                "\t</pageFooter>\n" +
                "</jasperReport>\n";

        if ("JR7".equalsIgnoreCase(format)) {
            return convertToJr7(jr6Template);
        }
        return jr6Template;
    }

    /**
     * Inyecta o actualiza la propiedad de Data Adapter en un JRXML.
     */
    public static String injectDataAdapterProperty(String jrxml, String adapterRelativePath) {
        if (jrxml == null || adapterRelativePath == null || adapterRelativePath.trim().isEmpty()) {
            return jrxml;
        }

        String path = adapterRelativePath.trim().replace("\\", "/");
        String result = jrxml;

        // 1. net.sf.jasperreports.data.adapter
        Pattern p1 = Pattern.compile("<property\\s+name=\"net\\.sf\\.jasperreports\\.data\\.adapter\"\\s+value=\"[^\"]*\"\\s*\\/?>");
        Matcher m1 = p1.matcher(result);
        if (m1.find()) {
            result = m1.replaceFirst("<property name=\"net.sf.jasperreports.data.adapter\" value=\"" + path + "\"/>");
        } else {
            Pattern rootP = Pattern.compile("(<jasperReport\\b[^>]*>)");
            Matcher rootM = rootP.matcher(result);
            if (rootM.find()) {
                result = rootM.replaceFirst("$1\n\t<property name=\"net.sf.jasperreports.data.adapter\" value=\"" + path + "\"/>");
            }
        }

        // 2. com.jaspersoft.studio.data.defaultdataadapter
        Pattern p2 = Pattern.compile("<property\\s+name=\"com\\.jaspersoft\\.studio\\.data\\.defaultdataadapter\"\\s+value=\"[^\"]*\"\\s*\\/?>");
        Matcher m2 = p2.matcher(result);
        if (m2.find()) {
            result = m2.replaceFirst("<property name=\"com.jaspersoft.studio.data.defaultdataadapter\" value=\"" + path + "\"/>");
        } else {
            Pattern rootP = Pattern.compile("(<jasperReport\\b[^>]*>)");
            Matcher rootM = rootP.matcher(result);
            if (rootM.find()) {
                result = rootM.replaceFirst("$1\n\t<property name=\"com.jaspersoft.studio.data.defaultdataadapter\" value=\"" + path + "\"/>");
            }
        }

        return result;
    }
}
