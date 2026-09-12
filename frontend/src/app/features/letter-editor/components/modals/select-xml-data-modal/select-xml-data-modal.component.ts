import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../../services/i18n.service';
import { DataFileInfo } from '../../../../../models/letter-resource.model';

/**
 * Modal para vincular un archivo XML de datos existente al Data Adapter de la carta actual,
 * o crear uno nuevo. Componente de presentación pura: sin estado propio, todo por @Input/@Output.
 */
@Component({
  selector: 'app-select-xml-data-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './select-xml-data-modal.component.html',
  styleUrls: ['./select-xml-data-modal.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectXmlDataModalComponent {
  @Input({ required: true }) open = false;
  @Input({ required: true }) loading = false;
  @Input({ required: true }) files: DataFileInfo[] = [];
  @Input({ required: true }) creatingNew = false;
  @Input({ required: true }) newFileName = '';

  @Output() creatingNewChange = new EventEmitter<boolean>();
  @Output() newFileNameChange = new EventEmitter<string>();
  @Output() selectFile = new EventEmitter<DataFileInfo>();
  @Output() submitCreate = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
