import { Injectable, inject, signal } from '@angular/core';
import { PreviewService } from '../../../services/preview.service';
import { LetterResourceInfo } from '../../../models/letter-resource.model';

/**
 * Estado del catálogo de cartas y de la conectividad con el backend — separado de
 * LetterEditorStore (que posee el estado de la carta actualmente abierta) porque este
 * catálogo existe independientemente de si hay o no una carta seleccionada.
 */
@Injectable({ providedIn: 'root' })
export class LetterCatalogService {
  private readonly previewService = inject(PreviewService);

  readonly resourceLetters = signal<LetterResourceInfo[]>([]);
  readonly backendOnline = signal<boolean>(false);

  checkHealth(): void {
    this.previewService.checkHealth().subscribe({
      next: (res) => {
        this.backendOnline.set(res.status === 'UP');
      },
      error: () => {
        this.backendOnline.set(false);
      }
    });
  }

  loadAvailableLetters(): void {
    this.previewService.getAvailableLetters().subscribe({
      next: (letters) => {
        this.resourceLetters.set(letters);
        this.backendOnline.set(true);
      },
      error: () => {
        this.backendOnline.set(false);
      }
    });
  }
}
