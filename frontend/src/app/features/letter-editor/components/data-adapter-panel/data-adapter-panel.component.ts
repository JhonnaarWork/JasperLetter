import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../services/i18n.service';
import { XmlCodeEditorComponent } from '../../../../components/xml-code-editor/xml-code-editor.component';
import { XmlDataAdapterModel } from '../../../../models/data-adapter.model';
import { LetterEditorStore } from '../../state/letter-editor.store';

/**
 * Pestaña de edición del Data Adapter de la carta actual: formulario con campos específicos
 * (estilo Jaspersoft Studio) o código XML plano, más las acciones de probar/cambiar adapter.
 * Es la "pane de feature" más acoplada al estado (plan de refactor, Fase 4): a diferencia de
 * los modales y paneles puros del resto de la app, inyecta LetterEditorStore directamente en
 * vez de recibir ~15 @Input/@Output, porque es una superficie singleton (no un átomo de UI
 * reutilizable) que ya toca casi todo el estado del Data Adapter.
 */
@Component({
  selector: 'app-data-adapter-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, XmlCodeEditorComponent],
  templateUrl: './data-adapter-panel.component.html',
  styleUrls: ['./data-adapter-panel.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataAdapterPanelComponent {
  readonly store = inject(LetterEditorStore);
  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  updateField<K extends keyof XmlDataAdapterModel>(field: K, value: XmlDataAdapterModel[K]): void {
    this.store.updateDataAdapterField(field, value);
  }
}
