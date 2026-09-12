import { ChangeDetectionStrategy, Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService, Language } from './services/i18n.service';
import { CreateLetterRequest, DataAdapterOptionInfo, DataFileInfo } from './models/letter-resource.model';
import { XmlDataAdapterModel } from './models/data-adapter.model';
import { XmlCodeEditorComponent } from './components/xml-code-editor/xml-code-editor.component';
import { CreateLetterModalComponent } from './components/create-letter-modal/create-letter-modal.component';
import { VisualEditorPaneComponent } from './features/letter-editor/components/visual-editor-pane/visual-editor-pane.component';
import { TestResultModalComponent } from './features/letter-editor/components/modals/test-result-modal/test-result-modal.component';
import { SaveConfirmModalComponent } from './features/letter-editor/components/modals/save-confirm-modal/save-confirm-modal.component';
import { UnsavedChangesModalComponent } from './features/letter-editor/components/modals/unsaved-changes-modal/unsaved-changes-modal.component';
import { SelectAdapterModalComponent } from './features/letter-editor/components/modals/select-adapter-modal/select-adapter-modal.component';
import { SelectXmlDataModalComponent } from './features/letter-editor/components/modals/select-xml-data-modal/select-xml-data-modal.component';
import { WelcomeScreenComponent } from './features/letter-editor/components/welcome-screen/welcome-screen.component';
import { PreviewPaneComponent } from './features/letter-editor/components/preview-pane/preview-pane.component';
import { LetterEditorStore } from './features/letter-editor/state/letter-editor.store';
import { LetterCatalogService } from './features/letter-editor/state/letter-catalog.service';

/**
 * AppComponent es la "shell" de la aplicación: compone los paneles/modales y delega el estado
 * de la carta actual a LetterEditorStore (features/letter-editor/state) y el catálogo de
 * cartas a LetterCatalogService. Conserva directamente solo lo que es inherentemente de nivel
 * de shell: atajos de teclado globales, el guard de navegación ante cambios sin guardar (que
 * coordina entre "cambiar de carta" y "crear carta nueva"), el wiring del modal de crear
 * carta, y el modo de vista/pestañas. El subsistema de doble-click para editar texto (que
 * depende del ViewChild hacia el editor visual de terceros) vive en VisualEditorPaneComponent,
 * no acá.
 *
 * Las señales de LetterEditorStore/LetterCatalogService se re-exponen bajo el mismo nombre de
 * propiedad (ej. `readonly jrxml = this.letterEditor.jrxml`) para que el template siga
 * leyéndolas exactamente igual que antes de la extracción.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    XmlCodeEditorComponent,
    CreateLetterModalComponent,
    VisualEditorPaneComponent,
    TestResultModalComponent,
    SaveConfirmModalComponent,
    UnsavedChangesModalComponent,
    SelectAdapterModalComponent,
    SelectXmlDataModalComponent,
    WelcomeScreenComponent,
    PreviewPaneComponent
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit {
  private readonly letterEditor = inject(LetterEditorStore);
  private readonly catalog = inject(LetterCatalogService);

  readonly i18n = inject(I18nService);
  readonly currentLang = this.i18n.currentLang;

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  setLanguage(lang: Language): void {
    this.i18n.setLanguage(lang);
  }

  // ---------- Alias de LetterCatalogService (mismo nombre que antes de la extracción) ----------
  readonly resourceLetters = this.catalog.resourceLetters;
  readonly backendOnline = this.catalog.backendOnline;

  // ---------- Alias de LetterEditorStore (mismo nombre que antes de la extracción) ----------
  readonly samples = this.letterEditor.samples;
  readonly currentSelection = this.letterEditor.currentSelection;
  readonly selectedLetterId = this.letterEditor.selectedLetterId;
  readonly selectedSampleId = this.letterEditor.selectedSampleId;
  readonly letterFormat = this.letterEditor.letterFormat;
  readonly switchingLetter = this.letterEditor.switchingLetter;
  readonly switchingMessage = this.letterEditor.switchingMessage;

  readonly jrxml = this.letterEditor.jrxml;
  readonly originalJrxml = this.letterEditor.originalJrxml;
  readonly xmlData = this.letterEditor.xmlData;
  readonly originalXmlData = this.letterEditor.originalXmlData;
  readonly dataAdapter = this.letterEditor.dataAdapter;
  readonly originalDataAdapter = this.letterEditor.originalDataAdapter;
  readonly dataAdapterMode = this.letterEditor.dataAdapterMode;
  readonly dataAdapterModel = this.letterEditor.dataAdapterModel;
  readonly testingDataAdapter = this.letterEditor.testingDataAdapter;
  readonly testResultModalOpen = this.letterEditor.testResultModalOpen;
  readonly testResult = this.letterEditor.testResult;
  readonly isDataAdapterConnected = this.letterEditor.isDataAdapterConnected;
  readonly parameters = this.letterEditor.parameters;

  readonly isJrxmlDirty = this.letterEditor.isJrxmlDirty;
  readonly isDataAdapterDirty = this.letterEditor.isDataAdapterDirty;
  readonly isXmlDataDirty = this.letterEditor.isXmlDataDirty;
  readonly hasUnsavedChanges = this.letterEditor.hasUnsavedChanges;
  readonly dirtyCount = this.letterEditor.dirtyCount;

  readonly saveConfirmModalOpen = this.letterEditor.saveConfirmModalOpen;
  readonly saveSelectJrxml = this.letterEditor.saveSelectJrxml;
  readonly saveSelectDataAdapter = this.letterEditor.saveSelectDataAdapter;
  readonly saveSelectXmlData = this.letterEditor.saveSelectXmlData;

  readonly selectAdapterModalOpen = this.letterEditor.selectAdapterModalOpen;
  readonly availableAdapters = this.letterEditor.availableAdapters;
  readonly loadingAdapters = this.letterEditor.loadingAdapters;

  readonly selectXmlDataModalOpen = this.letterEditor.selectXmlDataModalOpen;
  readonly availableXmlFiles = this.letterEditor.availableXmlFiles;
  readonly loadingXmlFiles = this.letterEditor.loadingXmlFiles;
  readonly newXmlFileName = this.letterEditor.newXmlFileName;
  readonly isCreatingNewXml = this.letterEditor.isCreatingNewXml;

  readonly pdfUrl = this.letterEditor.pdfUrl;
  readonly rawPdfBlob = this.letterEditor.rawPdfBlob;
  readonly loading = this.letterEditor.loading;
  readonly errorMessage = this.letterEditor.errorMessage;

  readonly saving = this.letterEditor.saving;
  readonly saveStatus = this.letterEditor.saveStatus;

  readonly parameterEntries = this.letterEditor.parameterEntries;

  // Indicador de cambio de carta (Spinner) — alias de letterEditor.switchingLetter/-Message ya declarados arriba

  // Modal: Crear Carta (queda en AppComponent: es estado propio del modal, no del dominio de la carta)
  readonly createLetterModalOpen = signal<boolean>(false);
  readonly newLetterId = signal<string>('');
  readonly newLetterFormat = signal<'JR6' | 'JR7'>('JR6');
  readonly newLetterCreateAdapter = signal<boolean>(true);
  readonly newLetterCreateXml = signal<boolean>(true);
  readonly creatingLetter = signal<boolean>(false);
  readonly createLetterError = signal<string | null>(null);

  // Modal: Cambios sin Guardar (Guard de Navegación) — coordina entre "cambiar de carta" y
  // "crear carta nueva", por eso queda en el shell y no en una feature específica.
  readonly unsavedModalOpen = signal<boolean>(false);
  readonly pendingTargetSelection = signal<string | null>(null);
  readonly pendingAction = signal<'select' | 'create' | null>(null);

  // Pestaña activa en el panel izquierdo: 'editor' | 'jrxmlCode' | 'xmlData' | 'dataAdapter'
  readonly leftTab = signal<'editor' | 'xmlData' | 'jrxmlCode' | 'dataAdapter'>('editor');

  // Modos de vista: 'split' (dividido), 'editor' (solo editor), 'preview' (solo vista previa)
  readonly viewMode = signal<'split' | 'editor' | 'preview'>('editor');
  // Pestaña derecha en modo dividido: 'preview' (PDF) o 'params' (Formulario de datos)
  readonly rightTab = signal<'preview' | 'params'>('preview');

  ngOnInit(): void {
    this.checkHealth();
    this.loadAvailableLetters();
  }

  // ---------- Delegados a LetterCatalogService ----------
  checkHealth(): void {
    this.catalog.checkHealth();
  }

  loadAvailableLetters(): void {
    this.catalog.loadAvailableLetters();
  }

  // ---------- Delegados a LetterEditorStore ----------
  onSelectionChange(val: string): void {
    this.letterEditor.onSelectionChange(val);
  }

  loadLetterFromResource(letterId: string): void {
    this.letterEditor.loadLetterFromResource(letterId);
  }

  loadSample(sampleId: string): void {
    this.letterEditor.loadSample(sampleId);
  }

  onJrxmlChange(newXml: string): void {
    this.letterEditor.onJrxmlChange(newXml);
  }

  onXmlDataChange(newXml: string): void {
    this.letterEditor.onXmlDataChange(newXml);
  }

  resetXmlData(): void {
    this.letterEditor.resetXmlData();
  }

  reloadXmlDataFromLetter(letterId: string): void {
    this.letterEditor.reloadXmlDataFromLetter(letterId);
  }

  setDataAdapterMode(mode: 'form' | 'xml'): void {
    this.letterEditor.setDataAdapterMode(mode);
  }

  updateDataAdapterField<K extends keyof XmlDataAdapterModel>(field: K, value: XmlDataAdapterModel[K]): void {
    this.letterEditor.updateDataAdapterField(field, value);
  }

  suggestStandardXmlPath(): void {
    this.letterEditor.suggestStandardXmlPath();
  }

  onDataAdapterChange(newData: string): void {
    this.letterEditor.onDataAdapterChange(newData);
  }

  resetDataAdapter(): void {
    this.letterEditor.resetDataAdapter();
  }

  runDataAdapterTest(): void {
    this.letterEditor.runDataAdapterTest();
  }

  closeTestModal(): void {
    this.letterEditor.closeTestModal();
  }

  onParameterChange(key: string, value: string): void {
    this.letterEditor.onParameterChange(key, value);
  }

  addCustomParameter(key: string, value: string): void {
    this.letterEditor.addCustomParameter(key, value);
  }

  removeParameter(key: string): void {
    this.letterEditor.removeParameter(key);
  }

  generatePreview(): void {
    this.letterEditor.generatePreview();
  }

  linkDataAdapterToJrxml(adapterRelativePath: string): void {
    this.letterEditor.linkDataAdapterToJrxml(adapterRelativePath);
  }

  linkDataXmlToAdapter(xmlRelativePath: string): void {
    this.letterEditor.linkDataXmlToAdapter(xmlRelativePath);
  }

  openSelectAdapterModal(): void {
    this.letterEditor.openSelectAdapterModal();
  }

  closeSelectAdapterModal(): void {
    this.letterEditor.closeSelectAdapterModal();
  }

  selectExistingAdapter(adapter: DataAdapterOptionInfo): void {
    this.letterEditor.selectExistingAdapter(adapter);
  }

  createNewAdapterForCurrentLetter(): void {
    this.letterEditor.createNewAdapterForCurrentLetter();
  }

  openSelectXmlDataModal(): void {
    this.letterEditor.openSelectXmlDataModal();
  }

  closeSelectXmlDataModal(): void {
    this.letterEditor.closeSelectXmlDataModal();
  }

  selectExistingXmlData(file: DataFileInfo): void {
    this.letterEditor.selectExistingXmlData(file);
  }

  submitCreateXmlData(): void {
    this.letterEditor.submitCreateXmlData();
  }

  openSaveConfirmModal(scope?: 'jrxml' | 'dataAdapter' | 'xmlData'): void {
    this.letterEditor.openSaveConfirmModal(scope);
  }

  closeSaveConfirmModal(): void {
    this.letterEditor.closeSaveConfirmModal();
  }

  confirmGranularSave(): void {
    void this.letterEditor.saveGranular();
  }

  // Atajo de teclado global Ctrl+S / Cmd+S
  @HostListener('window:keydown', ['$event'])
  handleGlobalKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault();
      if (this.selectedLetterId()) {
        this.openSaveConfirmModal();
      }
    }
  }

  // Protección ante cierre/recarga de ventana
  @HostListener('window:beforeunload', ['$event'])
  handleBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }

  // ==========================================================================
  // CREACIÓN DE NUEVA CARTA DESDE CERO
  // ==========================================================================
  openCreateLetterModal(): void {
    this.newLetterId.set('');
    this.newLetterFormat.set('JR6');
    this.newLetterCreateAdapter.set(true);
    this.newLetterCreateXml.set(true);
    this.createLetterError.set(null);
    this.createLetterModalOpen.set(true);
  }

  closeCreateLetterModal(): void {
    this.createLetterModalOpen.set(false);
  }

  requestCreateLetter(): void {
    if (this.hasUnsavedChanges()) {
      this.pendingAction.set('create');
      this.unsavedModalOpen.set(true);
      return;
    }
    this.openCreateLetterModal();
  }

  async submitCreateLetter(): Promise<void> {
    const rawId = this.newLetterId();
    if (!rawId || !rawId.trim()) {
      this.createLetterError.set('Debes ingresar un código/identificador para la carta.');
      return;
    }

    const cleanId = rawId.trim().toUpperCase().replace(/[^A-Z0-9_-]/g, '');
    if (!cleanId) {
      this.createLetterError.set('El identificador sólo puede contener letras, números, guiones y guiones bajos.');
      return;
    }

    this.creatingLetter.set(true);
    this.createLetterError.set(null);

    const req: CreateLetterRequest = {
      letterId: cleanId,
      format: this.newLetterFormat(),
      createDataAdapter: this.newLetterCreateAdapter(),
      createXmlData: this.newLetterCreateXml()
    };

    try {
      await this.letterEditor.createLetter(req);
      this.creatingLetter.set(false);
      this.closeCreateLetterModal();
    } catch (err: any) {
      this.creatingLetter.set(false);
      this.createLetterError.set(err.error?.message || err.message || 'Error al crear la carta en disco.');
    }
  }

  // ==========================================================================
  // GUARD DE NAVEGACIÓN ANTE CAMBIOS SIN GUARDAR
  // ==========================================================================
  requestChangeSelection(val: string): void {
    if (val === this.currentSelection()) return;
    if (this.hasUnsavedChanges()) {
      this.pendingTargetSelection.set(val);
      this.pendingAction.set('select');
      this.unsavedModalOpen.set(true);
      return;
    }
    this.onSelectionChange(val);
  }

  async resolveUnsavedModal(action: 'save' | 'discard' | 'cancel'): Promise<void> {
    if (action === 'cancel') {
      this.unsavedModalOpen.set(false);
      this.pendingTargetSelection.set(null);
      this.pendingAction.set(null);
      return;
    }

    if (action === 'discard') {
      this.unsavedModalOpen.set(false);
      const target = this.pendingTargetSelection();
      const pAction = this.pendingAction();
      this.pendingTargetSelection.set(null);
      this.pendingAction.set(null);

      if (pAction === 'create') {
        this.openCreateLetterModal();
      } else if (target !== null) {
        this.onSelectionChange(target);
      }
      return;
    }

    if (action === 'save') {
      this.unsavedModalOpen.set(false);
      const target = this.pendingTargetSelection();
      const pAction = this.pendingAction();
      this.pendingTargetSelection.set(null);
      this.pendingAction.set(null);

      // Configurar guardado granular para los componentes modificados
      this.saveSelectJrxml.set(this.isJrxmlDirty());
      this.saveSelectDataAdapter.set(this.isDataAdapterDirty());
      this.saveSelectXmlData.set(this.isXmlDataDirty());

      const success = await this.letterEditor.saveGranular();
      if (success) {
        if (pAction === 'create') {
          this.openCreateLetterModal();
        } else if (target !== null) {
          this.onSelectionChange(target);
        }
      }
    }
  }

  setViewMode(mode: 'split' | 'editor' | 'preview'): void {
    this.viewMode.set(mode);
  }

  onPreviewClick(): void {
    this.setViewMode('preview');
    this.setRightTab('preview');
    this.generatePreview();
  }

  setLeftTab(tab: 'editor' | 'xmlData' | 'jrxmlCode' | 'dataAdapter'): void {
    this.leftTab.set(tab);
    if (tab === 'xmlData' && !this.xmlData() && this.selectedLetterId()) {
      this.reloadXmlDataFromLetter(this.selectedLetterId()!);
    }
  }

  setRightTab(tab: 'preview' | 'params'): void {
    this.rightTab.set(tab);
  }

  setLetterFormat(format: 'JR6' | 'JR7'): void {
    this.letterFormat.set(format);
  }
}
