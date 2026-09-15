import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../services/i18n.service';

/**
 * Modal para importar una carta a partir de un JRXML ya escrito o de un PDF (layout estático de
 * partida). Mismo patrón que CreateLetterModalComponent: componente de presentación pura, sin
 * estado propio de negocio, todo por @Input/@Output — AppComponent es el dueño del estado
 * (importLetterId, importFile, etc.) y de las clases compartidas (da-modal-*, btn-*, form-*,
 * radio-card, checkbox-label) que ya vive en styles.css a nivel global.
 */
@Component({
  selector: 'app-import-letter-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './import-letter-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportLetterModalComponent {
  @Input({ required: true }) mode: 'create' | 'current' = 'current';
  /** Etiqueta de la carta/muestra actualmente abierta (p.ej. "ETIPLET002"), mostrada en vez del
   *  input de ID cuando mode === 'current'. Si es null, ese modo no está disponible (no hay
   *  ninguna carta abierta) y el selector de modo no se muestra. */
  @Input() currentLetterLabel: string | null = null;
  @Input({ required: true }) letterId = '';
  @Input({ required: true }) file: File | null = null;
  @Input({ required: true }) format: 'JR6' | 'JR7' = 'JR6';
  @Input({ required: true }) createAdapter = true;
  @Input({ required: true }) createXmlData = true;
  @Input({ required: true }) importing = false;
  @Input() error: string | null = null;

  @Output() modeChange = new EventEmitter<'create' | 'current'>();
  @Output() letterIdChange = new EventEmitter<string>();
  @Output() fileChange = new EventEmitter<File | null>();
  @Output() formatChange = new EventEmitter<'JR6' | 'JR7'>();
  @Output() createAdapterChange = new EventEmitter<boolean>();
  @Output() createXmlDataChange = new EventEmitter<boolean>();
  @Output() submitImport = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  get fileKind(): 'jrxml' | 'pdf' | null {
    const name = this.file?.name?.toLowerCase() ?? '';
    if (name.endsWith('.jrxml')) return 'jrxml';
    if (name.endsWith('.pdf')) return 'pdf';
    return null;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.fileChange.emit(input.files && input.files.length > 0 ? input.files[0] : null);
  }
}
