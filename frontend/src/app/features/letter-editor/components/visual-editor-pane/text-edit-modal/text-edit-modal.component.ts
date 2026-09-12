import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../../services/i18n.service';
import { SpellcheckService, SpellError } from '../../../../../services/spellcheck.service';

/**
 * Modal de edición de texto estático / expresión de campo, con corrector ortográfico.
 * Anidado dentro de VisualEditorPaneComponent (no es un modal de nivel raíz de la app): su
 * botón "Aplicar" necesita escribir sobre el store interno del editor visual de terceros, que
 * solo VisualEditorPaneComponent conoce (ver el comentario de esa clase). Este componente es
 * puro: no toca el store del editor directamente, solo recibe el contenido inicial y emite el
 * contenido final por (save).
 */
@Component({
  selector: 'app-text-edit-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './text-edit-modal.component.html',
  styleUrls: ['./text-edit-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TextEditModalComponent implements OnChanges {
  @Input({ required: true }) open = false;
  @Input({ required: true }) kind: 'staticText' | 'textField' = 'staticText';
  @Input({ required: true }) content = '';

  @Output() save = new EventEmitter<string>();
  @Output() cancel = new EventEmitter<void>();

  @ViewChild('modalTextarea') modalTextareaRef?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('backdropRef') backdropRef?: ElementRef<HTMLDivElement>;

  private readonly spellcheckService = inject(SpellcheckService);
  private readonly i18n = inject(I18nService);

  readonly editedContent = signal<string>('');
  readonly spellcheckLang = signal<'es' | 'en' | 'off'>('es');
  readonly spellErrors = signal<SpellError[]>([]);
  readonly backdropHtml = signal<string>('');

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.editedContent.set(this.content);

      // Ajustar idioma del corrector al idioma actual de la aplicación si no está desactivado
      if (this.spellcheckLang() !== 'off') {
        this.spellcheckLang.set(this.i18n.currentLang() === 'en' ? 'en' : 'es');
      }

      // Ensure Hunspell dictionaries are loaded then update spellcheck
      this.spellcheckService.init().then(() => {
        this.updateSpellcheck();
        this.syncScrollAndWidth();
      });

      setTimeout(() => {
        if (this.modalTextareaRef?.nativeElement) {
          const textarea = this.modalTextareaRef.nativeElement;
          textarea.focus();
          textarea.setSelectionRange(0, 0);
          textarea.scrollTop = 0;
          this.syncScrollAndWidth();
        }
        this.updateSpellcheck();
      }, 60);
    }
  }

  setSpellcheckLang(lang: 'es' | 'en' | 'off'): void {
    this.spellcheckLang.set(lang);
    this.updateSpellcheck();
    this.syncScrollAndWidth();
    setTimeout(() => {
      this.modalTextareaRef?.nativeElement?.focus();
    }, 0);
  }

  onInput(val: string): void {
    this.editedContent.set(val);
    this.updateSpellcheck();
    this.syncScrollAndWidth();
  }

  onTextareaScroll(): void {
    if (this.backdropRef?.nativeElement && this.modalTextareaRef?.nativeElement) {
      this.backdropRef.nativeElement.scrollTop = this.modalTextareaRef.nativeElement.scrollTop;
      this.backdropRef.nativeElement.scrollLeft = this.modalTextareaRef.nativeElement.scrollLeft;
    }
  }

  syncScrollAndWidth(): void {
    if (this.backdropRef?.nativeElement && this.modalTextareaRef?.nativeElement) {
      const textarea = this.modalTextareaRef.nativeElement;
      const backdrop = this.backdropRef.nativeElement;
      backdrop.scrollTop = textarea.scrollTop;
      backdrop.scrollLeft = textarea.scrollLeft;
      const scrollbarWidth = textarea.offsetWidth - textarea.clientWidth;
      backdrop.style.paddingRight = `${14 + scrollbarWidth}px`;
    }
  }

  updateSpellcheck(): void {
    const lang = this.spellcheckLang();
    const text = this.editedContent();
    const isExpr = this.kind === 'textField';

    if (lang === 'off' || !text) {
      this.spellErrors.set([]);
      this.backdropHtml.set(this.escapeHtml(text));
      return;
    }

    const errors = this.spellcheckService.checkText(text, lang, isExpr);
    this.spellErrors.set(errors);
    this.backdropHtml.set(this.buildBackdropHtml(text, errors));
  }

  buildBackdropHtml(text: string, errors: SpellError[]): string {
    if (!text) return '&nbsp;';
    if (!errors || errors.length === 0) return this.escapeHtml(text);

    const sorted = [...errors].sort((a, b) => a.startIndex - b.startIndex);
    let html = '';
    let lastIdx = 0;

    for (const err of sorted) {
      if (err.startIndex > lastIdx) {
        html += this.escapeHtml(text.substring(lastIdx, err.startIndex));
      }
      const word = text.substring(err.startIndex, err.endIndex);
      html += `<mark class="spell-error-mark">${this.escapeHtml(word)}</mark>`;
      lastIdx = err.endIndex;
    }
    if (lastIdx < text.length) {
      html += this.escapeHtml(text.substring(lastIdx));
    }
    return html;
  }

  escapeHtml(str: string): string {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  insertIntoExpression(snippet: string): void {
    const textarea = this.modalTextareaRef?.nativeElement;
    if (textarea) {
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const val = textarea.value;
      const next = val.substring(0, start) + snippet + val.substring(end);
      this.editedContent.set(next);
      setTimeout(() => {
        textarea.focus();
        textarea.setSelectionRange(start + snippet.length, start + snippet.length);
      }, 0);
    } else {
      this.editedContent.set(this.editedContent() + snippet);
    }
  }

  getLineCount(text: string): number {
    return text ? text.split('\n').length : 1;
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.saveClick();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.cancel.emit();
    }
  }

  saveClick(): void {
    const newContent = this.modalTextareaRef?.nativeElement?.value ?? this.editedContent();
    this.save.emit(newContent);
  }
}
