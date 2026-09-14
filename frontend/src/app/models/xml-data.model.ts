/**
 * Ruta base por defecto donde JasperReportService.java arma el XPath de la fuente de datos
 * cuando el JRXML no declara su propio <queryString> — ver ese fallback en
 * JasperReportService.fillReport(). Se replica aquí para que "Generar Datos" ubique los fields
 * en el mismo sitio que usará el motor de reportes al previsualizar/generar el PDF real.
 */
const DEFAULT_QUERY_BASE = '/content/letterContents/letterTypeData/letterTabs';

export interface GenerateXmlDataResult {
  xml: string;
  /** Nombres de fields para los que se creó el nodo/atributo (no existía). */
  added: string[];
  /** Nombres de fields cuyo nodo/atributo ya tenía datos; no se tocaron. */
  alreadyPresent: string[];
  /** Nombres de fields sin fieldDescription (o con una ruta vacía) que no se pudieron ubicar. */
  skipped: string[];
}

export class XmlDataGenerationError extends Error {}

/**
 * Analiza los <field> del JRXML (su fieldDescription, un XPath relativo a <queryString>, p.ej.
 * "LEMETEN048/row/@T1") y agrega al XML de datos los nodos/atributos que falten, con el propio
 * nombre del field como valor de relleno — igual a la convención que ya usan las cartas
 * existentes del repositorio para los campos sin dato de prueba real todavía. Los valores que ya
 * existen NUNCA se sobrescriben. Reserializa el documento completo con indentación de tabs
 * (mismo estilo que createStarterXmlData), así que reformatea el archivo entero, no solo lo que
 * agrega — coherente con lo que ya hace el botón "Formatear" del código JRXML.
 */
export function generateXmlDataFromFields(jrxml: string, existingXmlData: string): GenerateXmlDataResult {
  if (!jrxml.trim()) {
    throw new XmlDataGenerationError('No hay JRXML cargado para analizar.');
  }

  const jrxmlDoc = new DOMParser().parseFromString(jrxml, 'application/xml');
  if (jrxmlDoc.querySelector('parsererror')) {
    throw new XmlDataGenerationError('El código JRXML no es XML válido; corrígelo antes de generar los datos.');
  }

  const queryText = jrxmlDoc.querySelector('queryString')?.textContent?.trim();
  const baseSegments = splitDataXPath(queryText || DEFAULT_QUERY_BASE);
  if (baseSegments.length === 0) {
    throw new XmlDataGenerationError('No se pudo determinar la ruta base de los datos (queryString).');
  }

  const fields = Array.from(jrxmlDoc.querySelectorAll('field')).map((el) => ({
    name: el.getAttribute('name') || '',
    description: el.querySelector('fieldDescription')?.textContent?.trim() || ''
  }));

  const dataDoc = parseOrCreateDataDocument(existingXmlData, baseSegments[0]);

  let container: Element = dataDoc.documentElement;
  for (const segment of baseSegments.slice(1)) {
    container = findOrCreateChildElement(dataDoc, container, segment);
  }

  const added: string[] = [];
  const alreadyPresent: string[] = [];
  const skipped: string[] = [];

  for (const field of fields) {
    if (!field.name || !field.description) {
      if (field.name) skipped.push(field.name);
      continue;
    }

    const segments = splitDataXPath(field.description);
    if (segments.length === 0) {
      skipped.push(field.name);
      continue;
    }

    const last = segments[segments.length - 1];
    const isAttribute = last.startsWith('@');
    const elementSegments = isAttribute ? segments.slice(0, -1) : segments;

    let node = container;
    for (const segment of elementSegments) {
      node = findOrCreateChildElement(dataDoc, node, segment);
    }

    if (isAttribute) {
      const attrName = last.substring(1);
      if (node.hasAttribute(attrName)) {
        alreadyPresent.push(field.name);
      } else {
        node.setAttribute(attrName, field.name);
        added.push(field.name);
      }
    } else {
      if (getDirectText(node)) {
        alreadyPresent.push(field.name);
      } else {
        node.textContent = field.name;
        added.push(field.name);
      }
    }
  }

  return { xml: serializeDataDocument(dataDoc), added, alreadyPresent, skipped };
}

function splitDataXPath(path: string): string[] {
  return path
    .split('/')
    .map((s) => s.trim())
    .filter(Boolean);
}

function findOrCreateChildElement(doc: Document, parent: Element, tagName: string): Element {
  const existing = Array.from(parent.children).find((c) => c.tagName === tagName);
  if (existing) return existing;
  const created = doc.createElement(tagName);
  parent.appendChild(created);
  return created;
}

function parseOrCreateDataDocument(existingXml: string, rootTag: string): Document {
  const trimmed = existingXml.trim();
  if (!trimmed) {
    return new DOMParser().parseFromString(`<?xml version="1.0" encoding="UTF-8"?><${rootTag}></${rootTag}>`, 'application/xml');
  }
  const doc = new DOMParser().parseFromString(existingXml, 'application/xml');
  if (doc.querySelector('parsererror')) {
    throw new XmlDataGenerationError('El XML de datos actual no es válido; corrígelo o vacíalo antes de generar.');
  }
  return doc;
}

function getDirectText(el: Element): string {
  let text = '';
  for (const node of Array.from(el.childNodes)) {
    if (node.nodeType === Node.TEXT_NODE || node.nodeType === Node.CDATA_SECTION_NODE) {
      text += node.textContent || '';
    }
  }
  return text.trim();
}

function escapeXmlAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeXmlText(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function serializeDataDocument(doc: Document): string {
  const lines: string[] = ['<?xml version="1.0" encoding="UTF-8"?>'];
  serializeDataElement(doc.documentElement, 0, lines);
  return lines.join('\n') + '\n';
}

function serializeDataElement(el: Element, depth: number, lines: string[]): void {
  const indent = '\t'.repeat(depth);
  const attrs = Array.from(el.attributes)
    .map((a) => ` ${a.name}="${escapeXmlAttr(a.value)}"`)
    .join('');
  const childElements = Array.from(el.children);
  const textContent = getDirectText(el);

  if (childElements.length === 0 && !textContent) {
    lines.push(`${indent}<${el.tagName}${attrs}/>`);
    return;
  }
  if (childElements.length === 0) {
    lines.push(`${indent}<${el.tagName}${attrs}>${escapeXmlText(textContent)}</${el.tagName}>`);
    return;
  }
  lines.push(`${indent}<${el.tagName}${attrs}>`);
  for (const child of childElements) {
    serializeDataElement(child, depth + 1, lines);
  }
  lines.push(`${indent}</${el.tagName}>`);
}

/**
 * Contenido XML de arranque para un archivo de datos nuevo de una carta. Función pura,
 * extraída de AppComponent.submitCreateXmlData() (antes una plantilla hardcodeada inline).
 */
export function createStarterXmlData(letterId: string): string {
  return (
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<content>\n` +
    `\t<letterType>${letterId}</letterType>\n` +
    `\t<letterContents>\n` +
    `\t\t<letterTypeData>\n` +
    `\t\t\t<letterData>\n` +
    `\t\t\t\t<idLetterFormat>1001</idLetterFormat>\n` +
    `\t\t\t\t<printDate>${new Date().toISOString().substring(0, 10)}</printDate>\n` +
    `\t\t\t</letterData>\n` +
    `\t\t\t<argumentList>\n` +
    `\t\t\t\t<argumentData>\n` +
    `\t\t\t\t\t<argumentName>LETTER_TYPE</argumentName>\n` +
    `\t\t\t\t\t<argumentValue>${letterId}</argumentValue>\n` +
    `\t\t\t\t</argumentData>\n` +
    `\t\t\t</argumentList>\n` +
    `\t\t</letterTypeData>\n` +
    `\t</letterContents>\n` +
    `\t<language>es</language>\n` +
    `\t<extTemplate>${letterId}</extTemplate>\n` +
    `</content>\n`
  );
}
