import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../services/i18n.service';
import { LetterResourceInfo } from '../../../../models/letter-resource.model';

/**
 * Pantalla de bienvenida mostrada cuando no hay ninguna carta ni plantilla de ejemplo
 * seleccionada. Componente de presentación pura: sin estado propio, todo por @Input/@Output.
 */
@Component({
  selector: 'app-welcome-screen',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './welcome-screen.component.html',
  styleUrls: ['./welcome-screen.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WelcomeScreenComponent {
  @Input({ required: true }) resourceLetters: LetterResourceInfo[] = [];

  @Output() createLetter = new EventEmitter<void>();
  @Output() selectResourceLetter = new EventEmitter<string>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
