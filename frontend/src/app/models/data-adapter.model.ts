const DEFAULT_LOCALE = 'es_ES';
const DEFAULT_TIME_ZONE = 'America/Montevideo';

export interface XmlDataAdapterModel {
  name: string;
  location: string;
  locationType: string;
  useConnection: boolean;
  namespaceAware: boolean;
  selectExpression: string;
  locale: string;
  timeZone: string;
}

export interface TestDataAdapterResponse {
  success: boolean;
  status: 'SUCCESS' | 'FILE_NOT_FOUND' | 'INVALID_ADAPTER_XML' | 'INVALID_DATA_XML' | 'MISSING_LOCATION' | 'ERROR';
  message: string;
  adapterName?: string;
  location?: string;
  resolvedPath?: string;
  fileExists: boolean;
  fileSizeBytes: number;
  xmlValid: boolean;
  rootElement?: string;
  selectExpression?: string;
  xpathMatches: number;
  locale?: string;
  timeZone?: string;
  testedPaths?: string[];
  xmlContent?: string;
}

export function getDefaultXmlDataAdapterModel(letterId?: string): XmlDataAdapterModel {
  return {
    name: letterId ? `xmlDataAdapter_${letterId}` : 'xmlDataAdapter',
    location: letterId ? `src\\main\\resources\\data\\xml\\${letterId}.xml` : '',
    locationType: 'repositoryDataLocation',
    useConnection: true,
    namespaceAware: false,
    selectExpression: '',
    locale: DEFAULT_LOCALE,
    timeZone: DEFAULT_TIME_ZONE
  };
}

export function parseXmlDataAdapter(xmlStr: string, letterId?: string): XmlDataAdapterModel {
  const def = getDefaultXmlDataAdapterModel(letterId);
  if (!xmlStr || !xmlStr.trim()) return def;

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(xmlStr, 'application/xml');
    const parseError = doc.querySelector('parsererror');
    if (parseError) {
      return parseXmlDataAdapterFallback(xmlStr, def);
    }

    const nameEl = doc.querySelector('name');
    const locationEl = doc.querySelector('location');
    const dataFileEl = doc.querySelector('dataFile');
    const useConnEl = doc.querySelector('useConnection');
    const nsAwareEl = doc.querySelector('namespaceAware');
    const selectEl = doc.querySelector('selectExpression');
    const localeEl = doc.querySelector('locale');
    const timeZoneEl = doc.querySelector('timeZone');

    return {
      name: nameEl?.textContent?.trim() ?? def.name,
      location: locationEl?.textContent?.trim() ?? def.location,
      locationType: dataFileEl?.getAttribute('xsi:type') || def.locationType,
      useConnection: useConnEl ? useConnEl.textContent?.trim().toLowerCase() === 'true' : def.useConnection,
      namespaceAware: nsAwareEl ? nsAwareEl.textContent?.trim().toLowerCase() === 'true' : def.namespaceAware,
      selectExpression: selectEl?.textContent?.trim() ?? def.selectExpression,
      locale: localeEl?.textContent?.trim() ?? def.locale,
      timeZone: timeZoneEl?.textContent?.trim() ?? def.timeZone
    };
  } catch {
    return parseXmlDataAdapterFallback(xmlStr, def);
  }
}

function parseXmlDataAdapterFallback(xml: string, def: XmlDataAdapterModel): XmlDataAdapterModel {
  const getTag = (tag: string) => {
    const m = xml.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'));
    return m ? m[1].trim() : null;
  };
  const locTypeMatch = xml.match(/xsi:type="([^"]+)"/i);

  return {
    name: getTag('name') ?? def.name,
    location: getTag('location') ?? def.location,
    locationType: locTypeMatch ? locTypeMatch[1] : def.locationType,
    useConnection: getTag('useConnection') ? getTag('useConnection')!.toLowerCase() === 'true' : def.useConnection,
    namespaceAware: getTag('namespaceAware') ? getTag('namespaceAware')!.toLowerCase() === 'true' : def.namespaceAware,
    selectExpression: getTag('selectExpression') ?? def.selectExpression,
    locale: getTag('locale') ?? def.locale,
    timeZone: getTag('timeZone') ?? def.timeZone
  };
}

export function serializeXmlDataAdapter(model: XmlDataAdapterModel): string {
  const locType = model.locationType || 'repositoryDataLocation';
  return `<?xml version="1.0" encoding="UTF-8"?>
<xmlDataAdapter class="net.sf.jasperreports.data.xml.XmlDataAdapterImpl">
  <name>${escapeXml(model.name)}</name>
  <dataFile xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="${escapeXml(locType)}">
    <location>${escapeXml(model.location)}</location>
  </dataFile>
  <useConnection>${model.useConnection ? 'true' : 'false'}</useConnection>
  <namespaceAware>${model.namespaceAware ? 'true' : 'false'}</namespaceAware>
  <selectExpression>${escapeXml(model.selectExpression)}</selectExpression>
  <locale>${escapeXml(model.locale || DEFAULT_LOCALE)}</locale>
  <timeZone>${escapeXml(model.timeZone || DEFAULT_TIME_ZONE)}</timeZone>
</xmlDataAdapter>
`;
}

/**
 * Inserta o actualiza, dentro del JRXML, las dos propiedades que vinculan el reporte a un
 * Data Adapter (net.sf.jasperreports.data.adapter y su equivalente de Jaspersoft Studio).
 * Función pura: no depende de estado de componente, solo transforma el string de entrada.
 */
export function linkDataAdapterToJrxml(jrxml: string, adapterRelativePath: string): string {
  if (!jrxml) return jrxml;
  const cleanPath = adapterRelativePath.replace(/\\/g, '/');
  let content = jrxml;

  if (content.includes('name="net.sf.jasperreports.data.adapter"')) {
    content = content.replace(
      /<property\s+name="net\.sf\.jasperreports\.data\.adapter"\s+value="[^"]*"\s*\/?>/,
      `<property name="net.sf.jasperreports.data.adapter" value="${cleanPath}"/>`
    );
  } else {
    content = content.replace(
      /(<jasperReport\b[^>]*>)/,
      `$1\n\t<property name="net.sf.jasperreports.data.adapter" value="${cleanPath}"/>`
    );
  }

  if (content.includes('name="com.jaspersoft.studio.data.defaultdataadapter"')) {
    content = content.replace(
      /<property\s+name="com\.jaspersoft\.studio\.data\.defaultdataadapter"\s+value="[^"]*"\s*\/?>/,
      `<property name="com.jaspersoft.studio.data.defaultdataadapter" value="${cleanPath}"/>`
    );
  } else {
    content = content.replace(
      /(<jasperReport\b[^>]*>)/,
      `$1\n\t<property name="com.jaspersoft.studio.data.defaultdataadapter" value="${cleanPath}"/>`
    );
  }

  return content;
}

/**
 * Normaliza una ruta relativa de archivo XML de datos al formato con backslashes usado por
 * <location> en el Data Adapter (ej: "data/xml/X.xml" -> "data\\xml\\X.xml").
 */
export function normalizeXmlDataAdapterPath(xmlRelativePath: string): string {
  return xmlRelativePath.replace(/\//g, '\\');
}

function escapeXml(unsafe: string): string {
  if (!unsafe) return '';
  return unsafe
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}
