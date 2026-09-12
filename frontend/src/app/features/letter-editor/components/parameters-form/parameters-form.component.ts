import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../services/i18n.service';

export interface ParameterEntry {
  key: string;
  value: string;
}

/**
 * Formulario de parámetros de prueba ($P{...}) para la previsualización del reporte.
 * Componente de presentación pura: sin estado propio, todo por @Input/@Output.
 */
@Component({
  selector: 'app-parameters-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './parameters-form.component.html',
  styleUrls: ['./parameters-form.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ParametersFormComponent {
  @Input({ required: true }) parameterEntries: ParameterEntry[] = [];

  @Output() parameterChange = new EventEmitter<{ key: string; value: string }>();
  @Output() addParameter = new EventEmitter<{ key: string; value: string }>();
  @Output() removeParameter = new EventEmitter<string>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
