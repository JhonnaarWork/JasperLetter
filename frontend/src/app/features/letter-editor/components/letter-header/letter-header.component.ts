import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService, Language } from '../../../../services/i18n.service';
import { LetterResourceInfo } from '../../../../models/letter-resource.model';
import { LetterSample } from '../../../../models/samples';

/**
 * Barra superior: marca corporativa, selector de carta/plantilla, formato JR6/JR7, modo de
 * vista, acciones (previsualizar/guardar) e idioma. Componente de presentación pura: sin
 * estado propio, todo por @Input/@Output. La lógica de guard ante cambios sin guardar (al
 * cambiar de selección) y la orquestación de previsualizar+cambiar de pestaña quedan en
 * AppComponent, que es quien conoce esos flujos cruzados.
 */
@Component({
  selector: 'app-letter-header',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './letter-header.component.html',
  styleUrls: ['./letter-header.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LetterHeaderComponent {
  @Input({ required: true }) currentSelection = '';
  @Input({ required: true }) resourceLetters: LetterResourceInfo[] = [];
  @Input({ required: true }) samples: LetterSample[] = [];
  @Input({ required: true }) letterFormat: 'JR6' | 'JR7' = 'JR6';
  @Input({ required: true }) viewMode: 'split' | 'editor' | 'preview' = 'editor';
  @Input({ required: true }) loading = false;
  @Input({ required: true }) saving = false;
  @Input({ required: true }) hasUnsavedChanges = false;
  @Input({ required: true }) selectedLetterId: string | null = null;
  @Input({ required: true }) dirtyCount = 0;
  @Input({ required: true }) currentLang: Language = 'es';

  @Output() createLetter = new EventEmitter<void>();
  @Output() selectionChange = new EventEmitter<string>();
  @Output() formatChange = new EventEmitter<'JR6' | 'JR7'>();
  @Output() viewModeChange = new EventEmitter<'split' | 'editor' | 'preview'>();
  @Output() previewClick = new EventEmitter<void>();
  @Output() saveClick = new EventEmitter<void>();
  @Output() languageChange = new EventEmitter<Language>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
