export interface LetterSample {
  id: string;
  name: string;
  description: string;
  jrxml: string;
  defaultParameters: Record<string, string>;
}

export const LETTER_SAMPLES: LetterSample[] = [
  {
    id: 'notificacion',
    name: 'Carta de Notificación Formal',
    description: 'Comunicación oficial con asunto, cuerpo de notificación y firma',
    defaultParameters: {
      DESTINATARIO: 'Dra. María Elena Restrepo',
      CARGO_DESTINATARIO: 'Directora de Operaciones',
      EMPRESA_DESTINATARIO: 'Innovación & Tecnología S.A.S.',
      CIUDAD: 'Bogotá D.C.',
      FECHA: '09 de Septiembre de 2026',
      REFERENCIA: 'Aprobación de Solicitud de Proyecto REF-2026-894',
      CUERPO: 'Por medio de la presente nos complace comunicarle que, luego de una detallada evaluación técnica y financiera, su solicitud para el proyecto ha sido formalmente APROBADA conforme a los términos y condiciones convenidos. El equipo de gestión se contactará en los próximos días para la formalización del acta de inicio.',
      REMITENTE: 'Ing. Fernando Valenzuela',
      CARGO_REMITENTE: 'Gerente General de Asuntos Corporativos'
    },
    jrxml: `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd" name="CartaNotificacion" pageWidth="595" pageHeight="842" columnWidth="515" leftMargin="40" rightMargin="40" topMargin="40" bottomMargin="40">
	<parameter name="DESTINATARIO" class="java.lang.String"/>
	<parameter name="CARGO_DESTINATARIO" class="java.lang.String"/>
	<parameter name="EMPRESA_DESTINATARIO" class="java.lang.String"/>
	<parameter name="CIUDAD" class="java.lang.String"/>
	<parameter name="FECHA" class="java.lang.String"/>
	<parameter name="REFERENCIA" class="java.lang.String"/>
	<parameter name="CUERPO" class="java.lang.String"/>
	<parameter name="REMITENTE" class="java.lang.String"/>
	<parameter name="CARGO_REMITENTE" class="java.lang.String"/>
	<title height="220">
		<element kind="staticText" x="0" y="10" width="515" height="28" fontSize="18" bold="true" hTextAlign="Center" forecolor="#1E3A8A">
			<text><![CDATA[COMUNICACIÓN OFICIAL]]></text>
		</element>
		<element kind="line" x="0" y="42" width="515" height="2" forecolor="#CBD5E1"/>
		<element kind="textField" x="0" y="55" width="515" height="18" fontSize="10" hTextAlign="Right" forecolor="#64748B">
			<expression><![CDATA[$P{CIUDAD} + ", " + $P{FECHA}]]></expression>
		</element>
		<element kind="textField" x="0" y="80" width="515" height="18" fontSize="11" bold="true" forecolor="#0F172A">
			<expression><![CDATA["Señor(a): " + $P{DESTINATARIO}]]></expression>
		</element>
		<element kind="textField" x="0" y="98" width="515" height="16" fontSize="10" forecolor="#334155">
			<expression><![CDATA[$P{CARGO_DESTINATARIO}]]></expression>
		</element>
		<element kind="textField" x="0" y="114" width="515" height="16" fontSize="10" forecolor="#334155">
			<expression><![CDATA[$P{EMPRESA_DESTINATARIO}]]></expression>
		</element>
		<element kind="textField" x="0" y="145" width="515" height="20" fontSize="11" bold="true" forecolor="#1E293B">
			<expression><![CDATA["Asunto: " + $P{REFERENCIA}]]></expression>
		</element>
		<element kind="staticText" x="0" y="180" width="515" height="18" fontSize="11" forecolor="#1E293B">
			<text><![CDATA[Respetado(a) doctor(a):]]></text>
		</element>
	</title>
	<detail>
		<band height="180">
			<element kind="textField" x="0" y="10" width="515" height="140" fontSize="11">
				<expression><![CDATA[$P{CUERPO}]]></expression>
			</element>
		</band>
	</detail>
	<pageFooter height="160">
		<element kind="staticText" x="0" y="10" width="515" height="18" fontSize="11" forecolor="#1E293B">
			<text><![CDATA[Atentamente,]]></text>
		</element>
		<element kind="line" x="0" y="80" width="220" height="1" forecolor="#0F172A"/>
		<element kind="textField" x="0" y="86" width="260" height="18" fontSize="11" bold="true" forecolor="#0F172A">
			<expression><![CDATA[$P{REMITENTE}]]></expression>
		</element>
		<element kind="textField" x="0" y="104" width="260" height="16" fontSize="9" forecolor="#64748B">
			<expression><![CDATA[$P{CARGO_REMITENTE}]]></expression>
		</element>
		<element kind="staticText" x="0" y="135" width="515" height="14" fontSize="8" hTextAlign="Center" forecolor="#94A3B8">
			<text><![CDATA[Este documento es una comunicación oficial generada mediante el motor JasperReports.]]></text>
		</element>
	</pageFooter>
</jasperReport>`
  },
  {
    id: 'certificado',
    name: 'Certificado Laboral',
    description: 'Constancia oficial de empleo, sueldo y antigüedad',
    defaultParameters: {
      NOMBRE_EMPLEADO: 'CARLOS ANDRÉS RAMÍREZ SILVA',
      DOCUMENTO_IDENTIDAD: '1.020.345.678',
      CARGO: 'Ingeniero de Software Senior',
      TIPO_CONTRATO: 'Término Indefinido',
      FECHA_INGRESO: '15 de Enero de 2022',
      SALARIO_MENSUAL: '$ 8.500.000 COP',
      FECHA_EXPEDICION: '09 de Septiembre de 2026',
      DIRECTOR_RRHH: 'DRA. PATRICIA MONTOYA',
      CARGO_DIRECTOR: 'Directora de Talento Humano'
    },
    jrxml: `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd" name="CertificadoLaboral" pageWidth="595" pageHeight="842" columnWidth="515" leftMargin="40" rightMargin="40" topMargin="40" bottomMargin="40">
	<parameter name="NOMBRE_EMPLEADO" class="java.lang.String"/>
	<parameter name="DOCUMENTO_IDENTIDAD" class="java.lang.String"/>
	<parameter name="CARGO" class="java.lang.String"/>
	<parameter name="TIPO_CONTRATO" class="java.lang.String"/>
	<parameter name="FECHA_INGRESO" class="java.lang.String"/>
	<parameter name="SALARIO_MENSUAL" class="java.lang.String"/>
	<parameter name="FECHA_EXPEDICION" class="java.lang.String"/>
	<parameter name="DIRECTOR_RRHH" class="java.lang.String"/>
	<parameter name="CARGO_DIRECTOR" class="java.lang.String"/>
	<title height="140">
		<element kind="staticText" x="0" y="15" width="515" height="30" fontSize="20" bold="true" hTextAlign="Center" forecolor="#047857">
			<text><![CDATA[CERTIFICACIÓN LABORAL]]></text>
		</element>
		<element kind="staticText" x="0" y="95" width="515" height="24" fontSize="14" bold="true" hTextAlign="Center" forecolor="#1F2937">
			<text><![CDATA[A QUIEN PUEDA INTERESAR]]></text>
		</element>
	</title>
	<detail>
		<band height="240">
			<element kind="textField" x="0" y="20" width="515" height="120" fontSize="11">
				<expression><![CDATA["El suscrito Director de Gestión Humana hace constar que el(la) señor(a) " + $P{NOMBRE_EMPLEADO} + ", identificado(a) con cédula de ciudadanía No. " + $P{DOCUMENTO_IDENTIDAD} + ", labora en nuestra organización desempeñando el cargo de " + $P{CARGO} + " bajo la modalidad de contrato a " + $P{TIPO_CONTRATO} + " desde el " + $P{FECHA_INGRESO} + " devengando una asignación salarial mensual de " + $P{SALARIO_MENSUAL} + "."]]></expression>
			</element>
			<element kind="textField" x="0" y="155" width="515" height="40" fontSize="11">
				<expression><![CDATA["La presente certificación se expide a solicitud de la parte interesada el " + $P{FECHA_EXPEDICION} + "."]]></expression>
			</element>
		</band>
	</detail>
	<pageFooter height="150">
		<element kind="line" x="0" y="60" width="220" height="1" forecolor="#111827"/>
		<element kind="textField" x="0" y="66" width="300" height="18" fontSize="11" bold="true" forecolor="#111827">
			<expression><![CDATA[$P{DIRECTOR_RRHH}]]></expression>
		</element>
		<element kind="textField" x="0" y="84" width="300" height="16" fontSize="10" forecolor="#4B5563">
			<expression><![CDATA[$P{CARGO_DIRECTOR}]]></expression>
		</element>
	</pageFooter>
</jasperReport>`
  }
];
