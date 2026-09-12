import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../../services/i18n.service';

/**
 * Modal de confirmación de guardado granular (Ctrl+S / botón Guardar). Componente de
 * presentación pura: sin estado propio, todo por @Input/@Output.
 */
@Component({
  selector: 'app-save-confirm-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './save-confirm-modal.component.html',
  styleUrls: ['./save-confirm-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SaveConfirmModalComponent {
  @Input({ required: true }) open = false;
  @Input({ required: true }) letterId: string | null = null;
  @Input({ required: true }) saving = false;

  @Input({ required: true }) jrxmlDirty = false;
  @Input({ required: true }) dataAdapterDirty = false;
  @Input({ required: true }) xmlDataDirty = false;

  @Input({ required: true }) selectJrxml = true;
  @Input({ required: true }) selectDataAdapter = true;
  @Input({ required: true }) selectXmlData = true;
  @Input() xmlDataLocation = '';

  @Output() selectJrxmlChange = new EventEmitter<boolean>();
  @Output() selectDataAdapterChange = new EventEmitter<boolean>();
  @Output() selectXmlDataChange = new EventEmitter<boolean>();
  @Output() confirm = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
