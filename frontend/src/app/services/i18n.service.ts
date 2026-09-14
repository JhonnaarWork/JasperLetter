import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { Language, TRANSLATIONS, checkTranslationParity } from './translations';

export type { Language };

if (!environment.production) {
  checkTranslationParity();
}

@Injectable({
  providedIn: 'root'
})
export class I18nService {
  private readonly STORAGE_KEY = 'jasper_letter_lang';
  readonly currentLang = signal<Language>(this.getInitialLanguage());

  constructor() {
    this.updateDocumentLang(this.currentLang());
  }

  setLanguage(lang: Language): void {
    this.currentLang.set(lang);
    try {
      localStorage.setItem(this.STORAGE_KEY, lang);
    } catch {
      // Ignore localStorage errors
    }
    this.updateDocumentLang(lang);
  }

  t(key: string, ...params: (string | number)[]): string {
    const lang = this.currentLang();
    let text = TRANSLATIONS[lang]?.[key] ?? TRANSLATIONS['es']?.[key] ?? key;
    if (params && params.length > 0) {
      params.forEach((val, idx) => {
        text = text.replace(new RegExp(`\\{${idx}\\}`, 'g'), String(val));
      });
    }
    return text;
  }

  private getInitialLanguage(): Language {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY) as Language;
      if (stored === 'es' || stored === 'en') {
        return stored;
      }
    } catch {
      // Fallback
    }
    if (typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('en')) {
      return 'en';
    }
    return 'es';
  }

  private updateDocumentLang(lang: Language): void {
    if (typeof document !== 'undefined' && document.documentElement) {
      document.documentElement.lang = lang;
      document.documentElement.setAttribute('data-lang', lang);
    }
  }
}
