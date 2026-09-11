import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../services/i18n.service';

/**
 * Modal de creación de una nueva carta, extraído de AppComponent (hallazgo F-1: el
 * componente raíz concentraba demasiadas responsabilidades). Es un componente de
 * presentación puro: no posee estado propio de negocio, solo recibe los valores del
 * formulario por @Input() y emite los cambios/acciones por @Output(), dejando a
 * AppComponent como único dueño del estado (newLetterId, newLetterFormat, etc.).
 *
 * Su estilo depende de clases compartidas (da-modal-*, btn-*, form-*, radio-card,
 * checkbox-label) que viven en styles.css a nivel global, no en un stylesheet propio,
 * porque esas mismas clases las siguen usando otros modales de AppComponent.
 */
@Component({
  selector: 'app-create-letter-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './create-letter-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateLetterModalComponent {
  @Input({ required: true }) letterId = '';
  @Input({ required: true }) format: 'JR6' | 'JR7' = 'JR6';
  @Input({ required: true }) createAdapter = true;
  @Input({ required: true }) createXmlData = true;
  @Input({ required: true }) creating = false;
  @Input() error: string | null = null;

  @Output() letterIdChange = new EventEmitter<string>();
  @Output() formatChange = new EventEmitter<'JR6' | 'JR7'>();
  @Output() createAdapterChange = new EventEmitter<boolean>();
  @Output() createXmlDataChange = new EventEmitter<boolean>();
  @Output() submitCreate = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
