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

/**
 * Modal de edición de texto estático / expresión de campo, con corrector ortográfico nativo
 * del navegador (spellcheck + lang en el textarea) — sin overlay propio: se probó en vivo que
 * la técnica anterior de un <div> de fondo sincronizado con subrayados propios convivía sin
 * coordinarse con el corrector nativo del navegador (que sigue activo por defecto en cualquier
 * textarea), mostrando subrayados duplicados/inconsistentes y sugerencias de clic derecho en
 * el idioma equivocado. Ahora se apaga el corrector nativo cuando el usuario lo desactiva,
 * dejando que el propio navegador (y su menú de clic derecho con sugerencias reales) se
 * encargue de todo.
 *
 * No hay selector de idioma para el corrector: se probó y la mayoría de navegadores basados en
 * Chromium ignoran el atributo lang por elemento para elegir el diccionario de corrección — usan
 * el idioma configurado en las preferencias del propio navegador, no el de un control dentro de
 * la página. El atributo lang igual se fija al idioma actual de la app (útil en los navegadores
 * que sí lo respetan, como Firefox), pero no se expone como algo elegible porque no sería un
 * control real en la mayoría de casos.
 *
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

  private readonly i18n = inject(I18nService);

  readonly editedContent = signal<string>('');
  readonly spellcheckEnabled = signal<boolean>(true);
  readonly currentAppLang = this.i18n.currentLang;

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.editedContent.set(this.content);

      setTimeout(() => {
        if (this.modalTextareaRef?.nativeElement) {
          const textarea = this.modalTextareaRef.nativeElement;
          textarea.focus();
          textarea.setSelectionRange(0, 0);
          textarea.scrollTop = 0;
        }
      }, 60);
    }
  }

  setSpellcheckEnabled(enabled: boolean): void {
    this.spellcheckEnabled.set(enabled);
    setTimeout(() => {
      this.modalTextareaRef?.nativeElement?.focus();
    }, 0);
  }

  onInput(val: string): void {
    this.editedContent.set(val);
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
