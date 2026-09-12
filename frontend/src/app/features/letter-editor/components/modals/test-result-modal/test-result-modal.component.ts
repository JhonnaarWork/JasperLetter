import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../../services/i18n.service';
import { TestDataAdapterResponse } from '../../../../../models/data-adapter.model';

/**
 * Muestra el resultado de "Probar Conexión" del Data Adapter. Componente de presentación
 * pura: sin estado propio, todo por @Input/@Output (mismo patrón que CreateLetterModalComponent).
 */
@Component({
  selector: 'app-test-result-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './test-result-modal.component.html',
  styleUrls: ['./test-result-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TestResultModalComponent {
  @Input({ required: true }) open = false;
  @Input({ required: true }) result: TestDataAdapterResponse | null = null;

  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
