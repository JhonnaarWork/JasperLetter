import { ES_TRANSLATIONS } from './translations.es';
import { EN_TRANSLATIONS } from './translations.en';

export type Language = 'es' | 'en';

export const TRANSLATIONS: Record<Language, Record<string, string>> = {
  es: ES_TRANSLATIONS,
  en: EN_TRANSLATIONS
};

/**
 * Avisa en consola, solo en desarrollo, si alguna clave de traducción existe en un idioma pero
 * no en el otro. TRANSLATIONS no tiene ningún chequeo de tipos que lo garantice (es un
 * Record<string, string> abierto en cada idioma), así que sin esto un idioma puede quedar
 * atrás en silencio cuando se agrega una clave nueva.
 */
export function checkTranslationParity(): void {
  const keysEs = new Set(Object.keys(TRANSLATIONS.es));
  const keysEn = new Set(Object.keys(TRANSLATIONS.en));
  const missingInEn = [...keysEs].filter(k => !keysEn.has(k));
  const missingInEs = [...keysEn].filter(k => !keysEs.has(k));

  if (missingInEn.length > 0) {
    console.warn(`[i18n] Faltan en 'en' las claves: ${missingInEn.join(', ')}`);
  }
  if (missingInEs.length > 0) {
    console.warn(`[i18n] Faltan en 'es' las claves: ${missingInEs.join(', ')}`);
  }
}
