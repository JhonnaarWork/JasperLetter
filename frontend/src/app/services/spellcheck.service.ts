import { Injectable, signal } from '@angular/core';
// @ts-ignore
import nspell from 'nspell';

export interface SpellError {
  word: string;
  startIndex: number;
  endIndex: number;
  suggestions: string[];
}

@Injectable({
  providedIn: 'root'
})
export class SpellcheckService {
  private nspellEs: any = null;
  private nspellEn: any = null;
  private loadingPromise: Promise<void> | null = null;

  readonly isLoaded = signal<boolean>(false);

  private readonly ignoredWords = new Set<string>();

  // Whitelist of valid domain, business, and utility terms in Spanish
  private readonly domainEs = new Set<string>([
    'distribuidora', 'distribuidoras', 'distribuidor', 'distribuidores',
    'regularizar', 'regularizarlas', 'regularizarlos', 'regularizarlo', 'regularizarla',
    'regularizarse', 'regularización', 'regularizacion',
    'abastecimiento', 'suministro', 'suministros', 'facturación', 'facturacion',
    'instalación', 'instalacion', 'instalaciones', 'responsabilidades',
    'incms', 'onesait', 'indra', 'minsait', 'jasper', 'jasperreports', 'jrxml',
    'etiplet003', 'etiplet', 'iban', 'cif', 'nif', 'dni', 'nis', 'titular', 'titulares'
  ]);

  private readonly domainEn = new Set<string>([
    'incms', 'onesait', 'indra', 'minsait', 'jasper', 'jasperreports', 'jrxml',
    'etiplet003', 'etiplet', 'iban', 'cif', 'nif', 'dni', 'nis'
  ]);

  constructor() {
    this.init();
  }

  async init(): Promise<void> {
    if (this.isLoaded()) return;
    if (this.loadingPromise) return this.loadingPromise;

    this.loadingPromise = (async () => {
      try {
        const [esAffRes, esDicRes, enAffRes, enDicRes] = await Promise.all([
          fetch('/assets/dictionaries/es.aff').then(r => r.text()),
          fetch('/assets/dictionaries/es.dic').then(r => r.text()),
          fetch('/assets/dictionaries/en.aff').then(r => r.text()),
          fetch('/assets/dictionaries/en.dic').then(r => r.text())
        ]);

        this.nspellEs = nspell({ aff: esAffRes, dic: esDicRes });
        this.nspellEn = nspell({ aff: enAffRes, dic: enDicRes });
        this.isLoaded.set(true);
      } catch (e) {
        console.warn('Could not load Hunspell dictionaries', e);
      }
    })();

    return this.loadingPromise;
  }

  isWordValid(word: string, lang: 'es' | 'en'): boolean {
    const clean = word.trim().replace(/^[¿¡"'(«\[{]+|[?!)"'»\]}.,;:]+$/g, '');
    if (!clean || clean.length <= 1) return true; // single letters like 'y', 'a', 'o', 'I'

    // Ignore numbers, dates, formulas, hex codes, UUIDs
    if (/^[\d.,\/\-_]+$/.test(clean)) return true;
    // Ignore uppercase acronyms/codes: CIF, DNI, IBAN, ETIPLET003
    if (/^[A-Z0-9_]{2,}$/.test(clean)) return true;

    const lower = clean.toLowerCase();
    if (this.ignoredWords.has(lower)) return true;

    if (lang === 'es') {
      if (this.domainEs.has(lower)) return true;

      if (this.nspellEs) {
        if (this.nspellEs.correct(clean) || this.nspellEs.correct(lower)) {
          return true;
        }

        // Spanish enclitic pronouns: regularizar + las -> regularizarlas
        const enclitics = [
          'melo', 'mela', 'melos', 'melas', 'telo', 'tela', 'telos', 'telas',
          'selo', 'sela', 'selos', 'selas', 'noslo', 'nosla', 'noslos', 'noslas',
          'oslo', 'osla', 'oslos', 'oslas', 'lo', 'la', 'los', 'las', 'le', 'les',
          'me', 'te', 'se', 'nos', 'os'
        ];

        for (const enc of enclitics) {
          if (lower.endsWith(enc)) {
            const stem = lower.slice(0, -enc.length);
            if (stem.endsWith('ar') || stem.endsWith('er') || stem.endsWith('ir')) {
              if (this.nspellEs.correct(stem) || this.domainEs.has(stem)) return true;
            }
            if (stem.endsWith('ando') || stem.endsWith('iendo')) {
              if (this.nspellEs.correct(stem)) return true;
            }
          }
        }
      }
      return false;
    } else {
      if (this.domainEn.has(lower)) return true;

      if (this.nspellEn) {
        if (this.nspellEn.correct(clean) || this.nspellEn.correct(lower)) {
          return true;
        }
      }
      return false;
    }
  }

  checkText(text: string, lang: 'es' | 'en' | 'off', isExpression = false): SpellError[] {
    if (lang === 'off' || !text || text.trim().length === 0) return [];

    const errors: SpellError[] = [];

    if (isExpression) {
      // In Jasper expression mode ($F{...}, $P{...}), only spellcheck literal quoted strings: "..."
      const stringLiteralRegex = /"([^"\\]*(\\.[^"\\]*)*)"/g;
      let match: RegExpExecArray | null;
      while ((match = stringLiteralRegex.exec(text)) !== null) {
        const literalContent = match[1];
        const offset = match.index + 1; // skip opening quote
        this.extractAndCheckWords(literalContent, offset, lang, errors);
      }
    } else {
      // Plain text mode (staticText): check entire content
      this.extractAndCheckWords(text, 0, lang, errors);
    }

    return errors;
  }

  private extractAndCheckWords(
    content: string,
    baseOffset: number,
    lang: 'es' | 'en',
    errorsOut: SpellError[]
  ): void {
    const wordRegex = /[a-záéíóúñüA-ZÁÉÍÓÚÑÜ]+/g;
    let match: RegExpExecArray | null;

    while ((match = wordRegex.exec(content)) !== null) {
      const rawWord = match[0];
      const startIndex = baseOffset + match.index;
      const endIndex = startIndex + rawWord.length;

      // Skip words that are part of Jasper expressions like $F{word} or $P{word}
      if (startIndex > 0 && content[match.index - 1] === '$') continue;

      if (!this.isWordValid(rawWord, lang)) {
        errorsOut.push({
          word: rawWord,
          startIndex,
          endIndex,
          suggestions: []
        });
      }
    }
  }

  ignoreWord(word: string): void {
    this.ignoredWords.add(word.toLowerCase());
  }
}
