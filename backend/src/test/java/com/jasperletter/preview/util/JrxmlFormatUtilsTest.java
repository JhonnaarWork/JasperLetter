package com.jasperletter.preview.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura unitaria de JrxmlFormatUtils: la lógica de conversión JR6/JR7 está hecha con
 * expresiones regulares y es la más frágil y más usada del sistema (hallazgo A-1 de la
 * auditoría). Estos tests no pretenden cubrir cada variante posible de JRXML, pero sí fijar
 * el comportamiento de las transformaciones públicas más importantes para detectar
 * regresiones antes de que lleguen a producción.
 */
class JrxmlFormatUtilsTest {

    // ---------- detectFormat ----------

    @Test
    void detectFormat_returnsJr6_whenNull() {
        assertEquals("JR6", JrxmlFormatUtils.detectFormat(null));
    }

    @Test
    void detectFormat_returnsJr6_forTraditionalStaticText() {
        String xml = "<jasperReport><detail><band><staticText>"
                + "<reportElement x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>"
                + "<text><![CDATA[Hola]]></text></staticText></band></detail></jasperReport>";

        assertEquals("JR6", JrxmlFormatUtils.detectFormat(xml));
    }

    @Test
    void detectFormat_returnsJr7_whenElementTagPresent() {
        String xml = "<jasperReport><detail><band><element kind=\"staticText\" x=\"0\" y=\"0\" width=\"10\" height=\"10\">"
                + "<text><![CDATA[Hola]]></text></element></band></detail></jasperReport>";

        assertEquals("JR7", JrxmlFormatUtils.detectFormat(xml));
    }

    // ---------- sanitizeExpression ----------

    @Test
    void sanitizeExpression_null_returnsEmptyStringLiteral() {
        assertEquals("\"\"", JrxmlFormatUtils.sanitizeExpression(null));
    }

    @Test
    void sanitizeExpression_blankOrEmptyFieldPlaceholder_returnsEmptyStringLiteral() {
        assertEquals("\"\"", JrxmlFormatUtils.sanitizeExpression("$F{}"));
        assertEquals("\"\"", JrxmlFormatUtils.sanitizeExpression("$P{}"));
        assertEquals("\"\"", JrxmlFormatUtils.sanitizeExpression("$V{}"));
        assertEquals("\"\"", JrxmlFormatUtils.sanitizeExpression("   "));
    }

    @Test
    void sanitizeExpression_alreadyQuoted_isLeftUnchanged() {
        assertEquals("\"Hola\"", JrxmlFormatUtils.sanitizeExpression("\"Hola\""));
    }

    @Test
    void sanitizeExpression_jasperFieldReference_isLeftUnchanged() {
        assertEquals("$F{nombre}", JrxmlFormatUtils.sanitizeExpression("$F{nombre}"));
        assertEquals("$P{TITULO}", JrxmlFormatUtils.sanitizeExpression("$P{TITULO}"));
        assertEquals("$F{a} + $F{b}", JrxmlFormatUtils.sanitizeExpression("$F{a} + $F{b}"));
    }

    @Test
    void sanitizeExpression_javaExpression_isLeftUnchanged() {
        assertEquals("new java.util.Date()", JrxmlFormatUtils.sanitizeExpression("new java.util.Date()"));
        assertEquals("1 + 1", JrxmlFormatUtils.sanitizeExpression("1 + 1"));
    }

    @Test
    void sanitizeExpression_literalNumbersAndBooleans_areLeftUnchanged() {
        assertEquals("true", JrxmlFormatUtils.sanitizeExpression("true"));
        assertEquals("false", JrxmlFormatUtils.sanitizeExpression("false"));
        assertEquals("null", JrxmlFormatUtils.sanitizeExpression("null"));
        assertEquals("123", JrxmlFormatUtils.sanitizeExpression("123"));
        assertEquals("-5.5", JrxmlFormatUtils.sanitizeExpression("-5.5"));
    }

    @Test
    void sanitizeExpression_unquotedLiteralText_getsWrappedInQuotes() {
        assertEquals("\"Nuevo texto\"", JrxmlFormatUtils.sanitizeExpression("Nuevo texto"));
    }

    @Test
    void sanitizeExpression_unquotedLiteralWithEmbeddedQuotes_isEscaped() {
        assertEquals("\"Diga \\\"hola\\\"\"", JrxmlFormatUtils.sanitizeExpression("Diga \"hola\""));
    }

    // ---------- sanitizeStaticTextContent ----------

    @Test
    void sanitizeStaticTextContent_null_returnsEmptyString() {
        assertEquals("", JrxmlFormatUtils.sanitizeStaticTextContent(null));
    }

    @Test
    void sanitizeStaticTextContent_stripsLeadingAndTrailingIndentationNewlines() {
        String raw = "\n\t\tHola Mundo\n\t";
        assertEquals("Hola Mundo", JrxmlFormatUtils.sanitizeStaticTextContent(raw));
    }

    @Test
    void sanitizeStaticTextContent_extractsFromCdataAndTrims() {
        String raw = "<![CDATA[\n\t\tHola Mundo\n\t]]>";
        assertEquals("Hola Mundo", JrxmlFormatUtils.sanitizeStaticTextContent(raw));
    }

    // ---------- convertToJr7 ----------

    @Test
    void convertToJr7_convertsTraditionalStaticTextToElementKind() {
        String jr6 = "<jasperReport>\n"
                + "  <detail>\n"
                + "    <band height=\"20\">\n"
                + "      <staticText>\n"
                + "        <reportElement x=\"0\" y=\"0\" width=\"100\" height=\"20\" uuid=\"11111111-1111-1111-1111-111111111111\"/>\n"
                + "        <text><![CDATA[Hola Mundo]]></text>\n"
                + "      </staticText>\n"
                + "    </band>\n"
                + "  </detail>\n"
                + "</jasperReport>";

        String jr7 = JrxmlFormatUtils.convertToJr7(jr6);

        assertTrue(jr7.contains("kind=\"staticText\""), "Debe contener un <element kind=\"staticText\">");
        assertTrue(jr7.contains("width=\"100\"") && jr7.contains("height=\"20\""),
                "Los atributos de reportElement deben fusionarse en el propio <element> (formato compacto JR7)");
        assertTrue(jr7.contains("Hola Mundo"));
        assertFalse(jr7.contains("<staticText>"), "No debe quedar la etiqueta <staticText> tradicional");
        assertFalse(jr7.contains("<reportElement"), "En el formato compacto de JR7 no debe quedar un <reportElement> anidado");
    }

    @Test
    void convertToJr7_normalizesIsPrefixedAttributes() {
        String jr6 = "<jasperReport>"
                + "<parameter name=\"P1\" class=\"java.lang.String\" isForPrompting=\"false\"/>"
                + "</jasperReport>";

        String jr7 = JrxmlFormatUtils.convertToJr7(jr6);

        assertTrue(jr7.contains("forPrompting=\"false\""));
        assertFalse(jr7.contains("isForPrompting="));
    }

    @Test
    void convertToJr7_convertsQueryStringAndFieldDescriptionTags() {
        String jr6 = "<jasperReport>"
                + "<queryString><![CDATA[SELECT 1]]></queryString>"
                + "<field name=\"f1\" class=\"java.lang.String\"><fieldDescription><![CDATA[Campo 1]]></fieldDescription></field>"
                + "</jasperReport>";

        String jr7 = JrxmlFormatUtils.convertToJr7(jr6);

        assertTrue(jr7.contains("<query"));
        assertFalse(jr7.contains("<queryString"));
        assertTrue(jr7.contains("<description"));
        assertFalse(jr7.contains("<fieldDescription"));
    }

    @Test
    void convertToJr7_wrapsUnquotedLiteralExpressions() {
        String jr6 = "<jasperReport><detail><band height=\"20\">"
                + "<textField><reportElement x=\"0\" y=\"0\" width=\"50\" height=\"20\"/>"
                + "<textFieldExpression><![CDATA[Nuevo texto]]></textFieldExpression></textField>"
                + "</band></detail></jasperReport>";

        String jr7 = JrxmlFormatUtils.convertToJr7(jr6);

        assertTrue(jr7.contains("<expression><![CDATA[\"Nuevo texto\"]]></expression>"));
    }

    // ---------- convertToJr6 ----------

    @Test
    void convertToJr6_convertsElementKindStaticTextBackToTraditionalTag() {
        String jr7 = "<jasperReport>\n"
                + "  <detail>\n"
                + "    <band height=\"20\">\n"
                + "      <element kind=\"staticText\" x=\"0\" y=\"0\" width=\"100\" height=\"20\">\n"
                + "        <text><![CDATA[Hola Mundo]]></text>\n"
                + "      </element>\n"
                + "    </band>\n"
                + "  </detail>\n"
                + "</jasperReport>";

        String jr6 = JrxmlFormatUtils.convertToJr6(jr7);

        assertTrue(jr6.contains("<staticText>"), "Debe reconstruir la etiqueta <staticText> tradicional");
        assertTrue(jr6.contains("<reportElement"));
        assertTrue(jr6.contains("Hola Mundo"));
    }

    @Test
    void convertToJr6_convertsQueryBackToQueryString() {
        String jr7 = "<jasperReport><query language=\"xPath\"><![CDATA[/root]]></query></jasperReport>";

        String jr6 = JrxmlFormatUtils.convertToJr6(jr7);

        assertTrue(jr6.contains("<queryString"));
        assertFalse(jr6.contains("<query "));
    }

    @Test
    void convertToJr6_wrapsSingleBandSectionsInBandTag() {
        String jr7 = "<jasperReport><pageHeader height=\"71\" splitType=\"Stretch\"/></jasperReport>";

        String jr6 = JrxmlFormatUtils.convertToJr6(jr7);

        assertTrue(jr6.contains("<pageHeader>"));
        assertTrue(jr6.contains("<band"));
    }

    // ---------- round trip ----------

    @Test
    void roundTrip_jr6ToJr7ToJr6_preservesStaticTextContent() {
        String jr6 = "<jasperReport>\n"
                + "  <detail>\n"
                + "    <band height=\"20\">\n"
                + "      <staticText>\n"
                + "        <reportElement x=\"0\" y=\"0\" width=\"100\" height=\"20\"/>\n"
                + "        <text><![CDATA[Texto de prueba]]></text>\n"
                + "      </staticText>\n"
                + "    </band>\n"
                + "  </detail>\n"
                + "</jasperReport>";

        String jr7 = JrxmlFormatUtils.convertToJr7(jr6);
        String backToJr6 = JrxmlFormatUtils.convertToJr6(jr7);

        assertEquals("JR7", JrxmlFormatUtils.detectFormat(jr7));
        assertEquals("JR6", JrxmlFormatUtils.detectFormat(backToJr6));
        assertTrue(backToJr6.contains("Texto de prueba"));
        assertTrue(backToJr6.contains("<staticText>"));
    }

    // ---------- createDefaultJrxml ----------

    @Test
    void createDefaultJrxml_jr6_containsLetterIdAndIsDetectedAsJr6() {
        String xml = JrxmlFormatUtils.createDefaultJrxml("TESTID", "JR6");

        assertEquals("JR6", JrxmlFormatUtils.detectFormat(xml));
        assertTrue(xml.contains("TESTID"));
        assertTrue(xml.contains("<jasperReport"));
    }

    @Test
    void createDefaultJrxml_jr7_isDetectedAsJr7() {
        String xml = JrxmlFormatUtils.createDefaultJrxml("TESTID", "JR7");

        assertEquals("JR7", JrxmlFormatUtils.detectFormat(xml));
        assertTrue(xml.contains("TESTID"));
    }

    // ---------- injectDataAdapterProperty ----------

    @Test
    void injectDataAdapterProperty_insertsPropertyWhenAbsent() {
        String jrxml = "<jasperReport name=\"X\"><detail/></jasperReport>";

        String result = JrxmlFormatUtils.injectDataAdapterProperty(jrxml, "xmlDataAdapter.xml");

        assertTrue(result.contains("name=\"net.sf.jasperreports.data.adapter\" value=\"xmlDataAdapter.xml\""));
        assertTrue(result.contains("name=\"com.jaspersoft.studio.data.defaultdataadapter\" value=\"xmlDataAdapter.xml\""));
    }

    @Test
    void injectDataAdapterProperty_replacesExistingPropertyValue() {
        String jrxml = "<jasperReport name=\"X\">"
                + "<property name=\"net.sf.jasperreports.data.adapter\" value=\"old.xml\"/>"
                + "<detail/></jasperReport>";

        String result = JrxmlFormatUtils.injectDataAdapterProperty(jrxml, "nuevo.xml");

        assertTrue(result.contains("value=\"nuevo.xml\""));
        assertFalse(result.contains("old.xml"));
    }
}
