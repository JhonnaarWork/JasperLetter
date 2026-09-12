import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SafeResourceUrl } from '@angular/platform-browser';
import { I18nService } from '../../../../services/i18n.service';
import { ParametersFormComponent, ParameterEntry } from '../parameters-form/parameters-form.component';

/**
 * Panel derecho: pestañas de vista previa PDF y parámetros de prueba. Componente de
 * presentación pura: sin estado propio, todo por @Input/@Output. Hospeda ParametersFormComponent
 * como hijo para la pestaña de parámetros.
 */
@Component({
  selector: 'app-preview-pane',
  standalone: true,
  imports: [CommonModule, ParametersFormComponent],
  templateUrl: './preview-pane.component.html',
  styleUrls: ['./preview-pane.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PreviewPaneComponent {
  @Input({ required: true }) rightTab: 'preview' | 'params' = 'preview';
  @Input({ required: true }) hasSelectedLetter = false;
  @Input({ required: true }) parameterEntries: ParameterEntry[] = [];
  @Input({ required: true }) errorMessage: string | null = null;
  @Input({ required: true }) loading = false;
  @Input({ required: true }) pdfUrl: SafeResourceUrl | null = null;

  @Output() rightTabChange = new EventEmitter<'preview' | 'params'>();
  @Output() refresh = new EventEmitter<void>();
  @Output() parameterChange = new EventEmitter<{ key: string; value: string }>();
  @Output() addParameter = new EventEmitter<{ key: string; value: string }>();
  @Output() removeParameter = new EventEmitter<string>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
