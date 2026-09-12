/**
 * Vocabulario específico del cliente/dominio de este despliegue de JasperLetter Studio
 * (Onesait/InCMS, Indra/Minsait, terminología de facturación eléctrica) que un corrector
 * ortográfico genérico no reconoce por defecto. Si este proyecto se reutiliza para otro
 * cliente, este es el único archivo que debería cambiar — SpellcheckService en sí no conoce
 * ningún término de negocio.
 */
export const DOMAIN_TERMS_ES: readonly string[] = [
  'distribuidora', 'distribuidoras', 'distribuidor', 'distribuidores',
  'regularizar', 'regularizarlas', 'regularizarlos', 'regularizarlo', 'regularizarla',
  'regularizarse', 'regularización', 'regularizacion',
  'abastecimiento', 'suministro', 'suministros', 'facturación', 'facturacion',
  'instalación', 'instalacion', 'instalaciones', 'responsabilidades',
  'incms', 'onesait', 'indra', 'minsait', 'jasper', 'jasperreports', 'jrxml',
  'etiplet003', 'etiplet', 'iban', 'cif', 'nif', 'dni', 'nis', 'titular', 'titulares'
];

export const DOMAIN_TERMS_EN: readonly string[] = [
  'incms', 'onesait', 'indra', 'minsait', 'jasper', 'jasperreports', 'jrxml',
  'etiplet003', 'etiplet', 'iban', 'cif', 'nif', 'dni', 'nis'
];
