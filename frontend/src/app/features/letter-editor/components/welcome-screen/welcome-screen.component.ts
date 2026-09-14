import { ChangeDetectionStrategy, Component, EventEmitter, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { I18nService } from '../../../../services/i18n.service';

/**
 * Pantalla de bienvenida mostrada cuando no hay ninguna carta ni plantilla de ejemplo
 * seleccionada. Componente de presentación pura: sin estado propio, todo por @Input/@Output.
 * Solo ofrece crear una carta nueva — seleccionar una existente se hace desde el selector del
 * header, que ya lista todas las cartas y escala mejor que repetir esa lista aquí (con muchas
 * cartas en el repositorio, un muro de botones sin buscador deja de ser usable).
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
  @Output() createLetter = new EventEmitter<void>();

  private readonly i18n = inject(I18nService);

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }
}
