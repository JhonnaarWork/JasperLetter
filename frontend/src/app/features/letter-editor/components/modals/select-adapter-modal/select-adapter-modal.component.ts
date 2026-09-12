import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../../services/i18n.service';
import { DataAdapterOptionInfo } from '../../../../../models/letter-resource.model';

/**
 * Modal para asignar un Data Adapter existente a la carta actual, o generar uno nuevo.
 * Componente de presentación pura: sin estado propio, todo por @Input/@Output.
 */
@Component({
  selector: 'app-select-adapter-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './select-adapter-modal.component.html',
  styleUrls: ['./select-adapter-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectAdapterModalComponent {
  @Input({ required: true }) open = false;
  @Input({ required: true }) letterId: string | null = null;
  @Input({ required: true }) loading = false;
  @Input({ required: true }) adapters: DataAdapterOptionInfo[] = [];

  @Output() selectAdapter = new EventEmitter<DataAdapterOptionInfo>();
  @Output() createNew = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
