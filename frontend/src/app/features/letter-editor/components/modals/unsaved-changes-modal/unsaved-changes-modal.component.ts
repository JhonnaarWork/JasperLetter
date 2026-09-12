import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../../services/i18n.service';

/**
 * Modal del guard de navegación ante cambios sin guardar. Componente de presentación pura:
 * sin estado propio, todo por @Input/@Output. La decisión de qué hacer con la acción pendiente
 * (cambiar de carta o crear una nueva) queda en AppComponent, que es quien conoce esos flujos.
 */
@Component({
  selector: 'app-unsaved-changes-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './unsaved-changes-modal.component.html',
  styleUrls: ['./unsaved-changes-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UnsavedChangesModalComponent {
  @Input({ required: true }) open = false;
  @Input({ required: true }) letterId: string | null = null;
  @Input({ required: true }) jrxmlDirty = false;
  @Input({ required: true }) dataAdapterDirty = false;
  @Input({ required: true }) xmlDataDirty = false;
  @Input() xmlDataLocation = '';

  @Output() resolve = new EventEmitter<'save' | 'discard' | 'cancel'>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
