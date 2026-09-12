import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  signal
} from '@angular/core';
import { JasperEditorComponent } from '@florianrauscha/ngx-jrxml-editor';
import { TextEditModalComponent } from './text-edit-modal/text-edit-modal.component';

/**
 * Forma mínima del store interno de @florianrauscha/ngx-jrxml-editor que este componente
 * necesita para seleccionar elementos y editar su contenido desde el modal de texto. No es
 * parte de la API pública documentada de la librería — puede cambiar o desaparecer en
 * cualquier actualización sin que sea un breaking change desde su punto de vista. Se accede a
 * él únicamente a través de getEditorStore(), de forma que una futura ruptura solo obligue a
 * tocar ese único método en vez de los puntos dispersos donde se use.
 */
interface JasperEditorInternalStore {
  stopEditing(): void;
  selectedElement(): { kind: string; text?: string; expression?: string } | null | undefined;
  selections(): unknown[] | undefined;
  select(path: unknown): void;
  updateSelected(updater: (el: any) => any): void;
}

/**
 * Hospeda el editor visual de terceros (<lib-jasper-editor>) y el subsistema de doble-click
 * para editar texto estático/expresiones de campo. Es una "pane de feature" (no un modal
 * puro): inyecta directamente el ViewChild hacia el editor de terceros y expone únicamente
 * jrxml por @Input/@Output — no inyecta LetterEditorStore directamente porque no necesita
 * nada más de ella que el propio contenido JRXML, que ya recibe del padre.
 */
@Component({
  selector: 'app-visual-editor-pane',
  standalone: true,
  imports: [JasperEditorComponent, TextEditModalComponent],
  templateUrl: './visual-editor-pane.component.html',
  styleUrls: ['./visual-editor-pane.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VisualEditorPaneComponent implements OnInit, OnDestroy {
  @Input({ required: true }) jrxml = '';
  @Output() jrxmlChange = new EventEmitter<string>();

  @ViewChild('editorComp') editorComp?: JasperEditorComponent;

  readonly textModalOpen = signal(false);
  readonly textModalKind = signal<'staticText' | 'textField'>('staticText');
  readonly textModalContent = signal('');

  private activeEditingPath: any = null;

  ngOnInit(): void {
    if (typeof document !== 'undefined') {
      document.addEventListener('dblclick', this.handleDocumentDblClick, true);
    }
  }

  ngOnDestroy(): void {
    if (typeof document !== 'undefined') {
      document.removeEventListener('dblclick', this.handleDocumentDblClick, true);
    }
  }

  onJrxmlChange(newXml: string): void {
    this.jrxmlChange.emit(newXml);
  }

  /** Único punto de acceso al store interno no documentado del editor visual. */
  private getEditorStore(): JasperEditorInternalStore | undefined {
    return (this.editorComp as unknown as { store?: JasperEditorInternalStore } | undefined)?.store;
  }

  // Nota: ya no hace falta comprobar la pestaña activa (a diferencia de la versión anterior en
  // AppComponent): este componente entero está detrás de un @if (leftTab() === 'editor') en su
  // padre, así que solo existe (y por lo tanto solo escucha dblclick) cuando esa pestaña está activa.
  private handleDocumentDblClick = (event: MouseEvent): void => {
    const target = event.target as HTMLElement | null;
    if (!target) return;

    // Detectar si se hizo doble click sobre un elemento de texto o campo de expresión
    const textEl = target.closest(
      'jasper-static-text-element, jasper-text-field-element, .je-static-text, .je-text-field'
    ) as HTMLElement | null;
    if (!textEl) return;

    // Prevenir el editor inline nativo en miniatura
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();

    const store = this.getEditorStore();
    if (store) {
      store.stopEditing();

      // Si por alguna razón el elemento no estaba seleccionado, seleccionarlo
      if (!store.selectedElement()) {
        textEl.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      }

      const openFromElement = () => {
        this.activeEditingPath = store.selections()?.[0] ?? null;
        const el = store.selectedElement();
        if (el && (el.kind === 'staticText' || el.kind === 'textField')) {
          this.openTextModal(el.kind, el.kind === 'staticText' ? el.text ?? '' : el.expression ?? '');
        } else {
          // Fallback en caso de que el elemento aún no se haya reflejado en el store
          const isStatic =
            textEl.tagName.toLowerCase() === 'jasper-static-text-element' ||
            textEl.classList.contains('je-static-text');
          const kind: 'staticText' | 'textField' = isStatic ? 'staticText' : 'textField';
          const content = textEl.innerText || textEl.textContent || '';
          this.openTextModal(kind, content.trim());
        }
      };

      const selected = store.selectedElement();
      if (selected && (selected.kind === 'staticText' || selected.kind === 'textField')) {
        openFromElement();
      } else {
        setTimeout(openFromElement, 40);
      }
    }
  };

  openTextModal(kind: 'staticText' | 'textField', content: string): void {
    this.textModalKind.set(kind);
    this.textModalContent.set(content);
    this.textModalOpen.set(true);
  }

  closeTextModal(): void {
    this.textModalOpen.set(false);
    this.getEditorStore()?.stopEditing();
  }

  handleTextModalSave(newContent: string): void {
    const store = this.getEditorStore();
    const kind = this.textModalKind();

    if (store) {
      const currentSelections = store.selections();
      if (this.activeEditingPath && (!currentSelections || currentSelections.length === 0)) {
        store.select(this.activeEditingPath);
      }

      if (kind === 'staticText') {
        store.updateSelected((el: any) => ({ ...el, text: newContent }));
      } else if (kind === 'textField') {
        store.updateSelected((el: any) => ({ ...el, expression: newContent }));
      }
    }

    this.closeTextModal();
  }
}
