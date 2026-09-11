import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { JasperEditorComponent } from '@florianrauscha/ngx-jrxml-editor';
import { PreviewService } from './services/preview.service';
import { I18nService, Language } from './services/i18n.service';
import { LETTER_SAMPLES, LetterSample } from './models/samples';
import {
  LetterResourceInfo,
  CreateLetterRequest,
  DataAdapterOptionInfo,
  DataFileInfo
} from './models/letter-resource.model';
import { SpellcheckService, SpellError } from './services/spellcheck.service';
import {
  XmlDataAdapterModel,
  getDefaultXmlDataAdapterModel,
  parseXmlDataAdapter,
  serializeXmlDataAdapter,
  TestDataAdapterResponse
} from './models/data-adapter.model';
import { XmlCodeEditorComponent } from './components/xml-code-editor/xml-code-editor.component';
import { environment } from '../environments/environment';

/**
 * Forma mínima del store interno de @florianrauscha/ngx-jrxml-editor que este componente
 * necesita para seleccionar elementos y editar su contenido desde el modal de texto. No es
 * parte de la API pública documentada de la librería — puede cambiar o desaparecer en
 * cualquier actualización sin que sea un breaking change desde su punto de vista. Se accede a
 * él únicamente a través de AppComponent.getEditorStore(), de forma que una futura ruptura
 * solo obligue a tocar ese único método en vez de los puntos dispersos donde se use.
 */
interface JasperEditorInternalStore {
  stopEditing(): void;
  selectedElement(): { kind: string; text?: string; expression?: string } | null | undefined;
  selections(): unknown[] | undefined;
  select(path: unknown): void;
  updateSelected(updater: (el: any) => any): void;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, JasperEditorComponent, XmlCodeEditorComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit, OnDestroy {
  @ViewChild('editorComp') editorComp?: JasperEditorComponent;
  @ViewChild('modalTextarea') modalTextareaRef?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('backdropRef') backdropRef?: ElementRef<HTMLDivElement>;

  // Estado del modal de edición de texto
  readonly textModalOpen = signal<boolean>(false);
  readonly textModalKind = signal<'staticText' | 'textField'>('staticText');
  readonly textModalContent = signal<string>('');
  readonly textModalOriginalContent = signal<string>('');

  // Selector de idioma del corrector ortográfico
  readonly spellcheckLang = signal<'es' | 'en' | 'off'>('es');
  readonly spellErrors = signal<SpellError[]>([]);
  readonly backdropHtml = signal<string>('');

  readonly spellcheckService = inject(SpellcheckService);
  private readonly previewService = inject(PreviewService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly i18n = inject(I18nService);
  readonly currentLang = this.i18n.currentLang;

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  setLanguage(lang: Language): void {
    this.i18n.setLanguage(lang);
  }

  // Muestras de ejemplo incorporadas
  readonly samples = LETTER_SAMPLES;

  // Cartas descubiertas en el directorio resources/
  readonly resourceLetters = signal<LetterResourceInfo[]>([]);

  // Selección actual en el selector ("resource:{id}" o "sample:{id}") - Inicialmente vacía
  readonly currentSelection = signal<string>('');
  readonly selectedLetterId = signal<string | null>(null);
  readonly selectedSampleId = signal<string | null>(null);

  // Formato objetivo de guardado (JR6 tradicional o JR7 moderno)
  readonly letterFormat = signal<'JR6' | 'JR7'>('JR6');

  // Estado del editor y reporte
  readonly jrxml = signal<string>('');
  readonly originalJrxml = signal<string>('');
  readonly xmlData = signal<string>('');
  readonly originalXmlData = signal<string>('');
  readonly dataAdapter = signal<string>('');
  readonly originalDataAdapter = signal<string>('');
  readonly dataAdapterMode = signal<'form' | 'xml'>('form');
  readonly dataAdapterModel = signal<XmlDataAdapterModel>(getDefaultXmlDataAdapterModel());
  readonly testingDataAdapter = signal<boolean>(false);
  readonly testResultModalOpen = signal<boolean>(false);
  readonly testResult = signal<TestDataAdapterResponse | null>(null);
  readonly isDataAdapterConnected = signal<boolean>(false);
  readonly parameters = signal<Record<string, string>>({});

  // Seguimiento de cambios sin guardar (Dirty State)
  readonly isJrxmlDirty = computed(() => !!this.selectedLetterId() && this.jrxml() !== this.originalJrxml());
  readonly isDataAdapterDirty = computed(() => !!this.selectedLetterId() && this.dataAdapter() !== this.originalDataAdapter());
  readonly isXmlDataDirty = computed(() => !!this.selectedLetterId() && this.xmlData() !== this.originalXmlData());
  readonly hasUnsavedChanges = computed(() => this.isJrxmlDirty() || this.isDataAdapterDirty() || this.isXmlDataDirty());
  readonly dirtyCount = computed(() => (this.isJrxmlDirty() ? 1 : 0) + (this.isDataAdapterDirty() ? 1 : 0) + (this.isXmlDataDirty() ? 1 : 0));

  // Indicador de cambio de carta (Spinner)
  readonly switchingLetter = signal<boolean>(false);
  readonly switchingMessage = signal<string>('');

  // Modal: Crear Carta
  readonly createLetterModalOpen = signal<boolean>(false);
  readonly newLetterId = signal<string>('');
  readonly newLetterFormat = signal<'JR6' | 'JR7'>('JR6');
  readonly newLetterCreateAdapter = signal<boolean>(true);
  readonly newLetterCreateXml = signal<boolean>(true);
  readonly creatingLetter = signal<boolean>(false);
  readonly createLetterError = signal<string | null>(null);

  // Modal: Confirmar Guardado Granular
  readonly saveConfirmModalOpen = signal<boolean>(false);
  readonly saveSelectJrxml = signal<boolean>(true);
  readonly saveSelectDataAdapter = signal<boolean>(true);
  readonly saveSelectXmlData = signal<boolean>(true);

  // Modal: Cambios sin Guardar (Guard de Navegación)
  readonly unsavedModalOpen = signal<boolean>(false);
  readonly pendingTargetSelection = signal<string | null>(null);
  readonly pendingAction = signal<'select' | 'create' | null>(null);

  // Modal: Seleccionar Data Adapter existente
  readonly selectAdapterModalOpen = signal<boolean>(false);
  readonly availableAdapters = signal<DataAdapterOptionInfo[]>([]);
  readonly loadingAdapters = signal<boolean>(false);

  // Modal: Seleccionar o Crear Datos XML
  readonly selectXmlDataModalOpen = signal<boolean>(false);
  readonly availableXmlFiles = signal<DataFileInfo[]>([]);
  readonly loadingXmlFiles = signal<boolean>(false);
  readonly newXmlFileName = signal<string>('');
  readonly isCreatingNewXml = signal<boolean>(false);

  // Pestaña activa en el panel izquierdo: 'editor' | 'jrxmlCode' | 'xmlData' | 'dataAdapter'
  readonly leftTab = signal<'editor' | 'xmlData' | 'jrxmlCode' | 'dataAdapter'>('editor');

  // Modos de vista: 'split' (dividido), 'editor' (solo editor), 'preview' (solo vista previa)
  readonly viewMode = signal<'split' | 'editor' | 'preview'>('editor');
  // Pestaña derecha en modo dividido: 'preview' (PDF) o 'params' (Formulario de datos)
  readonly rightTab = signal<'preview' | 'params'>('preview');

  // Estado del preview
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  readonly rawPdfBlob = signal<Blob | null>(null);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly backendOnline = signal<boolean>(false);

  // Estado de guardado
  readonly saving = signal<boolean>(false);
  readonly saveStatus = signal<{ type: 'success' | 'error' | null; message: string | null }>({
    type: null,
    message: null
  });

  // Lista de parámetros computada como pares [clave, valor]
  readonly parameterEntries = computed(() => {
    const params = this.parameters();
    return Object.entries(params).map(([key, value]) => ({ key, value }));
  });

  // Información de la carta de resources seleccionada actualmente
  readonly activeResourceInfo = computed(() => {
    const id = this.selectedLetterId();
    if (!id) return null;
    return this.resourceLetters().find((l) => l.id === id) || null;
  });

  ngOnInit(): void {
    this.checkHealth();
    this.loadAvailableLetters();
    if (typeof document !== 'undefined') {
      document.addEventListener('dblclick', this.handleDocumentDblClick, true);
    }
  }

  ngOnDestroy(): void {
    if (typeof document !== 'undefined') {
      document.removeEventListener('dblclick', this.handleDocumentDblClick, true);
    }
  }

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
        // Al entrar a la aplicación no seleccionamos ninguna carta automáticamente,
        // mostrando el estado inicial de bienvenida
      },
      error: () => {
        this.backendOnline.set(false);
      }
    });
  }

  onSelectionChange(val: string): void {
    this.currentSelection.set(val);
    if (!val) {
      this.selectedLetterId.set(null);
      this.selectedSampleId.set(null);
      this.jrxml.set('');
      this.originalJrxml.set('');
      this.xmlData.set('');
      this.originalXmlData.set('');
      this.dataAdapter.set('');
      this.originalDataAdapter.set('');
      this.pdfUrl.set(null);
      return;
    }
    if (val.startsWith('resource:')) {
      const letterId = val.substring('resource:'.length);
      this.loadLetterFromResource(letterId);
    } else if (val.startsWith('sample:')) {
      const sampleId = val.substring('sample:'.length);
      this.loadSample(sampleId);
    }
  }

  loadLetterFromResource(letterId: string): void {
    this.loading.set(true);
    this.switchingLetter.set(true);
    this.switchingMessage.set(`Cargando carta ${letterId}...`);
    this.errorMessage.set(null);
    this.saveStatus.set({ type: null, message: null });

    this.previewService.getLetterDetail(letterId).subscribe({
      next: (detail) => {
        this.selectedLetterId.set(detail.id);
        this.selectedSampleId.set(null);
        this.letterFormat.set(detail.detectedFormat || (detail as any).format || 'JR6');
        this.jrxml.set(detail.jrxml);
        this.originalJrxml.set(detail.jrxml);
        this.xmlData.set(detail.xmlData || '');
        this.originalXmlData.set(detail.xmlData || '');
        const adapterXml = detail.dataAdapter || '';
        this.dataAdapter.set(adapterXml);
        this.originalDataAdapter.set(adapterXml);
        this.dataAdapterModel.set(parseXmlDataAdapter(adapterXml, detail.id));
        this.isDataAdapterConnected.set(!!detail.dataAdapterConnected);
        this.parameters.set({});
        this.generatePreview();
        this.switchingLetter.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.switchingLetter.set(false);
        this.errorMessage.set(
          this.t('toast.loadError', letterId, err.error?.message || err.message)
        );
      }
    });
  }

  loadSample(sampleId: string): void {
    const sample = this.samples.find((s) => s.id === sampleId);
    if (!sample) return;

    this.selectedLetterId.set(null);
    this.selectedSampleId.set(sampleId);
    this.letterFormat.set('JR7');
    this.jrxml.set(sample.jrxml);
    this.originalJrxml.set(sample.jrxml);
    this.xmlData.set('');
    this.originalXmlData.set('');
    this.dataAdapter.set('');
    this.originalDataAdapter.set('');
    this.dataAdapterModel.set(getDefaultXmlDataAdapterModel());
    this.isDataAdapterConnected.set(false);
    this.parameters.set({ ...sample.defaultParameters });
    this.saveStatus.set({ type: null, message: null });
    this.generatePreview();
  }

  onJrxmlChange(newXml: string): void {
    this.jrxml.set(newXml);
  }

  onXmlDataChange(newXml: string): void {
    this.xmlData.set(newXml);
  }

  resetXmlData(): void {
    if (this.originalXmlData()) {
      this.xmlData.set(this.originalXmlData());
    } else if (this.selectedLetterId()) {
      this.reloadXmlDataFromLetter(this.selectedLetterId()!);
    }
  }

  reloadXmlDataFromLetter(letterId: string): void {
    this.previewService.getLetterDetail(letterId).subscribe({
      next: (detail) => {
        if (detail.xmlData) {
          this.xmlData.set(detail.xmlData);
          this.originalXmlData.set(detail.xmlData);
        }
        if (detail.dataAdapterConnected !== undefined) {
          this.isDataAdapterConnected.set(detail.dataAdapterConnected);
        }
      },
      error: () => {}
    });
  }

  setDataAdapterMode(mode: 'form' | 'xml'): void {
    if (mode === 'form') {
      const parsed = parseXmlDataAdapter(this.dataAdapter(), this.selectedLetterId() || undefined);
      this.dataAdapterModel.set(parsed);
    } else {
      const xml = serializeXmlDataAdapter(this.dataAdapterModel());
      this.dataAdapter.set(xml);
    }
    this.dataAdapterMode.set(mode);
  }

  updateDataAdapterField<K extends keyof XmlDataAdapterModel>(field: K, value: XmlDataAdapterModel[K]): void {
    const updated = { ...this.dataAdapterModel(), [field]: value };
    this.dataAdapterModel.set(updated);
    const xml = serializeXmlDataAdapter(updated);
    this.dataAdapter.set(xml);
  }

  suggestStandardXmlPath(): void {
    const letterId = this.selectedLetterId();
    if (!letterId) return;
    const path = `src\\main\\resources\\data\\xml\\${letterId}.xml`;
    this.updateDataAdapterField('location', path);
  }

  onDataAdapterChange(newData: string): void {
    this.dataAdapter.set(newData);
    try {
      const parsed = parseXmlDataAdapter(newData, this.selectedLetterId() || undefined);
      this.dataAdapterModel.set(parsed);
    } catch {}
  }

  resetDataAdapter(): void {
    const original = this.originalDataAdapter();
    this.dataAdapter.set(original);
    this.dataAdapterModel.set(parseXmlDataAdapter(original, this.selectedLetterId() || undefined));
  }

  runDataAdapterTest(): void {
    const xml = this.dataAdapterMode() === 'form'
      ? serializeXmlDataAdapter(this.dataAdapterModel())
      : this.dataAdapter();

    this.testingDataAdapter.set(true);
    this.previewService.testDataAdapter({
      letterId: this.selectedLetterId() || undefined,
      dataAdapterXml: xml
    }).subscribe({
      next: (res) => {
        this.testingDataAdapter.set(false);
        this.testResult.set(res);
        this.testResultModalOpen.set(true);
        this.isDataAdapterConnected.set(!!res.success);
        if (res.xmlContent) {
          this.xmlData.set(res.xmlContent);
          this.originalXmlData.set(res.xmlContent);
        }
      },
      error: (err) => {
        this.testingDataAdapter.set(false);
        this.testResult.set({
          success: false,
          status: 'ERROR',
          message: err.error?.message || err.message,
          fileExists: false,
          fileSizeBytes: 0,
          xmlValid: false,
          xpathMatches: 0
        });
        this.testResultModalOpen.set(true);
        this.isDataAdapterConnected.set(false);
      }
    });
  }

  closeTestModal(): void {
    this.testResultModalOpen.set(false);
  }

  onParameterChange(key: string, value: string): void {
    const current = { ...this.parameters(), [key]: value };
    this.parameters.set(current);
  }

  addCustomParameter(key: string, value: string): void {
    if (!key || !key.trim()) return;
    const cleanKey = key.trim().toUpperCase().replace(/\s+/g, '_');
    this.parameters.set({ ...this.parameters(), [cleanKey]: value });
  }

  removeParameter(key: string): void {
    const updated = { ...this.parameters() };
    delete updated[key];
    this.parameters.set(updated);
  }

  generatePreview(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const letterId = this.selectedLetterId() || undefined;
    const xmlData = this.xmlData() || undefined;

    this.previewService
      .generatePdf(this.jrxml(), this.parameters(), letterId, xmlData)
      .subscribe({
        next: (blob: Blob) => {
          this.rawPdfBlob.set(blob);
          const objectUrl = URL.createObjectURL(blob);
          this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
          this.backendOnline.set(true);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          if (err.status === 0) {
            this.backendOnline.set(false);
            this.errorMessage.set(this.t('preview.errorConnection', environment.apiBaseUrl || window.location.origin));
          } else {
            if (err.error instanceof Blob) {
              const reader = new FileReader();
              reader.onload = () => {
                try {
                  const parsed = JSON.parse(reader.result as string);
                  this.errorMessage.set(
                    parsed.cause || parsed.message || this.t('preview.errorGeneric')
                  );
                } catch {
                  this.errorMessage.set(this.t('preview.errorTemplate'));
                }
              };
              reader.readAsText(err.error);
            } else {
              this.errorMessage.set(err.message || this.t('preview.errorGeneric'));
            }
          }
        }
      });
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
  // GESTIÓN DE GUARDADO GRANULAR Y MODAL DE CONFIRMACIÓN
  // ==========================================================================
  openSaveConfirmModal(scope?: 'jrxml' | 'dataAdapter' | 'xmlData'): void {
    if (!this.selectedLetterId()) return;

    if (scope === 'jrxml') {
      this.saveSelectJrxml.set(true);
      this.saveSelectDataAdapter.set(false);
      this.saveSelectXmlData.set(false);
    } else if (scope === 'dataAdapter') {
      this.saveSelectJrxml.set(false);
      this.saveSelectDataAdapter.set(true);
      this.saveSelectXmlData.set(false);
    } else if (scope === 'xmlData') {
      this.saveSelectJrxml.set(false);
      this.saveSelectDataAdapter.set(false);
      this.saveSelectXmlData.set(true);
    } else {
      const anyDirty = this.hasUnsavedChanges();
      this.saveSelectJrxml.set(anyDirty ? this.isJrxmlDirty() : true);
      this.saveSelectDataAdapter.set(anyDirty ? this.isDataAdapterDirty() : !!this.dataAdapter());
      this.saveSelectXmlData.set(anyDirty ? this.isXmlDataDirty() : !!this.xmlData());
    }

    this.saveConfirmModalOpen.set(true);
  }

  closeSaveConfirmModal(): void {
    this.saveConfirmModalOpen.set(false);
  }

  confirmGranularSave(onSuccessCallback?: () => void): void {
    const letterId = this.selectedLetterId();
    if (!letterId) return;

    const doJrxml = this.saveSelectJrxml();
    const doAdapter = this.saveSelectDataAdapter();
    const doXml = this.saveSelectXmlData();

    if (!doJrxml && !doAdapter && !doXml) {
      this.closeSaveConfirmModal();
      return;
    }

    this.saving.set(true);
    this.saveStatus.set({ type: null, message: null });

    this.previewService
      .saveLetter(letterId, {
        jrxml: doJrxml ? this.jrxml() : undefined,
        saveJrxml: doJrxml,
        xmlData: doXml ? (this.xmlData() || '') : undefined,
        saveXmlData: doXml,
        dataAdapter: doAdapter ? (this.dataAdapter() || '') : undefined,
        saveDataAdapter: doAdapter,
        format: this.letterFormat()
      })
      .subscribe({
        next: (res) => {
          this.saving.set(false);
          this.closeSaveConfirmModal();

          if (doJrxml) {
            this.originalJrxml.set(this.jrxml());
          }
          if (doAdapter) {
            this.originalDataAdapter.set(this.dataAdapter());
          }
          if (doXml) {
            this.originalXmlData.set(this.xmlData());
          }

          if (res.dataAdapterConnected !== undefined) {
            this.isDataAdapterConnected.set(res.dataAdapterConnected);
          }
          if (res.xmlData && doXml) {
            this.xmlData.set(res.xmlData);
            this.originalXmlData.set(res.xmlData);
          }

          const parts: string[] = [];
          if (doJrxml) parts.push('Plantilla JRXML');
          if (doAdapter) parts.push('Data Adapter');
          if (doXml) parts.push('Datos XML');

          this.saveStatus.set({
            type: 'success',
            message: `Guardado exitoso de: ${parts.join(', ')} para ${letterId}.`
          });

          setTimeout(() => {
            if (this.saveStatus().type === 'success') {
              this.saveStatus.set({ type: null, message: null });
            }
          }, 4000);

          if (onSuccessCallback) {
            onSuccessCallback();
          }
        },
        error: (err) => {
          this.saving.set(false);
          this.saveStatus.set({
            type: 'error',
            message: this.t('toast.saveError', err.error?.message || err.message)
          });
        }
      });
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

  submitCreateLetter(): void {
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

    this.previewService.createLetter(req).subscribe({
      next: (detail) => {
        this.creatingLetter.set(false);
        this.closeCreateLetterModal();
        // Recargar cartas disponibles y abrir la recién creada
        this.previewService.getAvailableLetters().subscribe({
          next: (letters) => {
            this.resourceLetters.set(letters);
            this.currentSelection.set(`resource:${cleanId}`);
            this.loadLetterFromResource(cleanId);
          }
        });
      },
      error: (err) => {
        this.creatingLetter.set(false);
        this.createLetterError.set(err.error?.message || err.message || 'Error al crear la carta en disco.');
      }
    });
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

  resolveUnsavedModal(action: 'save' | 'discard' | 'cancel'): void {
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

      this.confirmGranularSave(() => {
        if (pAction === 'create') {
          this.openCreateLetterModal();
        } else if (target !== null) {
          this.onSelectionChange(target);
        }
      });
    }
  }

  // ==========================================================================
  // VINCULACIÓN EN CASCADA ("PADRE - HIJO")
  // ==========================================================================
  linkDataAdapterToJrxml(adapterRelativePath: string): void {
    const cleanPath = adapterRelativePath.replace(/\\/g, '/');
    let content = this.jrxml();
    if (!content) return;

    if (content.includes('name="net.sf.jasperreports.data.adapter"')) {
      content = content.replace(
        /<property\s+name="net\.sf\.jasperreports\.data\.adapter"\s+value="[^"]*"\s*\/?>/,
        `<property name="net.sf.jasperreports.data.adapter" value="${cleanPath}"/>`
      );
    } else {
      content = content.replace(
        /(<jasperReport\b[^>]*>)/,
        `$1\n\t<property name="net.sf.jasperreports.data.adapter" value="${cleanPath}"/>`
      );
    }

    if (content.includes('name="com.jaspersoft.studio.data.defaultdataadapter"')) {
      content = content.replace(
        /<property\s+name="com\.jaspersoft\.studio\.data\.defaultdataadapter"\s+value="[^"]*"\s*\/?>/,
        `<property name="com.jaspersoft.studio.data.defaultdataadapter" value="${cleanPath}"/>`
      );
    } else {
      content = content.replace(
        /(<jasperReport\b[^>]*>)/,
        `$1\n\t<property name="com.jaspersoft.studio.data.defaultdataadapter" value="${cleanPath}"/>`
      );
    }

    this.jrxml.set(content);
  }

  linkDataXmlToAdapter(xmlRelativePath: string): void {
    const cleanPath = xmlRelativePath.replace(/\//g, '\\');
    this.updateDataAdapterField('location', cleanPath);
  }

  // ==========================================================================
  // GESTIÓN Y ASIGNACIÓN DE DATA ADAPTER
  // ==========================================================================
  openSelectAdapterModal(): void {
    this.loadingAdapters.set(true);
    this.selectAdapterModalOpen.set(true);
    this.previewService.getDataAdapters().subscribe({
      next: (adapters) => {
        this.availableAdapters.set(adapters);
        this.loadingAdapters.set(false);
      },
      error: () => {
        this.loadingAdapters.set(false);
      }
    });
  }

  closeSelectAdapterModal(): void {
    this.selectAdapterModalOpen.set(false);
  }

  selectExistingAdapter(adapter: DataAdapterOptionInfo): void {
    this.linkDataAdapterToJrxml(adapter.relativePath);
    const model: XmlDataAdapterModel = {
      ...getDefaultXmlDataAdapterModel(),
      name: adapter.name,
      location: adapter.location || ''
    };
    const newAdapterXml = serializeXmlDataAdapter(model);

    this.dataAdapter.set(newAdapterXml);
    this.dataAdapterModel.set(model);
    this.closeSelectAdapterModal();
    if (adapter.location && this.selectedLetterId()) {
      this.reloadXmlDataFromLetter(this.selectedLetterId()!);
    }
  }

  createNewAdapterForCurrentLetter(): void {
    const letterId = this.selectedLetterId();
    if (!letterId) return;

    // Convención de ruta usada en el resto del flujo (relativa a la raíz del workspace),
    // distinta del "src\main\resources\..." que trae getDefaultXmlDataAdapterModel() por defecto.
    const xmlLocation = `resources\\data\\xml\\${letterId}.xml`;
    const model: XmlDataAdapterModel = { ...getDefaultXmlDataAdapterModel(letterId), location: xmlLocation };
    const newAdapterXml = serializeXmlDataAdapter(model);

    this.dataAdapter.set(newAdapterXml);
    this.dataAdapterModel.set(model);
    this.linkDataAdapterToJrxml('xmlDataAdapter.xml');
    this.linkDataXmlToAdapter(xmlLocation);
    this.reloadXmlDataFromLetter(letterId);
  }

  // ==========================================================================
  // GESTIÓN Y ASIGNACIÓN DE DATOS XML
  // ==========================================================================
  openSelectXmlDataModal(): void {
    this.loadingXmlFiles.set(true);
    this.selectXmlDataModalOpen.set(true);
    this.newXmlFileName.set(this.selectedLetterId() ? `${this.selectedLetterId()}.xml` : 'data.xml');
    this.isCreatingNewXml.set(false);

    this.previewService.getDataXmlFiles().subscribe({
      next: (files) => {
        this.availableXmlFiles.set(files);
        this.loadingXmlFiles.set(false);
      },
      error: () => {
        this.loadingXmlFiles.set(false);
      }
    });
  }

  closeSelectXmlDataModal(): void {
    this.selectXmlDataModalOpen.set(false);
  }

  selectExistingXmlData(file: DataFileInfo): void {
    this.linkDataXmlToAdapter(file.relativePath);
    this.closeSelectXmlDataModal();
    if (this.selectedLetterId()) {
      this.reloadXmlDataFromLetter(this.selectedLetterId()!);
    }
  }

  submitCreateXmlData(): void {
    const letterId = this.selectedLetterId();
    if (!letterId) return;

    let fname = this.newXmlFileName().trim();
    if (!fname) fname = `${letterId}.xml`;
    if (!fname.toLowerCase().endsWith('.xml')) fname += '.xml';

    this.loadingXmlFiles.set(true);
    const starter =
      `<?xml version="1.0" encoding="UTF-8"?>\n` +
      `<content>\n` +
      `\t<letterType>${letterId}</letterType>\n` +
      `\t<letterContents>\n` +
      `\t\t<letterTypeData>\n` +
      `\t\t\t<letterData>\n` +
      `\t\t\t\t<idLetterFormat>1001</idLetterFormat>\n` +
      `\t\t\t\t<printDate>${new Date().toISOString().substring(0, 10)}</printDate>\n` +
      `\t\t\t</letterData>\n` +
      `\t\t\t<argumentList>\n` +
      `\t\t\t\t<argumentData>\n` +
      `\t\t\t\t\t<argumentName>LETTER_TYPE</argumentName>\n` +
      `\t\t\t\t\t<argumentValue>${letterId}</argumentValue>\n` +
      `\t\t\t\t</argumentData>\n` +
      `\t\t\t</argumentList>\n` +
      `\t\t</letterTypeData>\n` +
      `\t</letterContents>\n` +
      `\t<language>es</language>\n` +
      `\t<extTemplate>${letterId}</extTemplate>\n` +
      `</content>\n`;

    this.previewService.createDataXmlFile({
      letterId,
      fileName: fname,
      content: starter
    }).subscribe({
      next: (created) => {
        this.loadingXmlFiles.set(false);
        this.closeSelectXmlDataModal();
        this.linkDataXmlToAdapter(created.relativePath);
        this.xmlData.set(starter);
        this.originalXmlData.set(starter);
        this.isDataAdapterConnected.set(true);
      },
      error: (err) => {
        this.loadingXmlFiles.set(false);
        alert(err.error?.message || 'Error al crear archivo de datos XML');
      }
    });
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

  // ==========================================================================
  // Manejo de Doble Click en Elementos de Texto y Modal de Edición
  // ==========================================================================

  private activeEditingPath: any = null;

  /** Único punto de acceso al store interno no documentado del editor visual (ver F-4). */
  private getEditorStore(): JasperEditorInternalStore | undefined {
    return (this.editorComp as unknown as { store?: JasperEditorInternalStore } | undefined)?.store;
  }

  private handleDocumentDblClick = (event: MouseEvent): void => {
    // Solo interceptar en la pestaña del Diseñador Visual
    if (this.leftTab() !== 'editor') return;

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
          this.openTextModal(
            el.kind,
            el.kind === 'staticText' ? (el.text ?? '') : (el.expression ?? '')
          );
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
    this.textModalOriginalContent.set(content);

    // Ajustar idioma del corrector al idioma actual de la aplicación si no está desactivado
    if (this.spellcheckLang() !== 'off') {
      this.spellcheckLang.set(this.i18n.currentLang() === 'en' ? 'en' : 'es');
    }

    this.textModalOpen.set(true);

    // Ensure Hunspell dictionaries are loaded then update spellcheck
    this.spellcheckService.init().then(() => {
      this.updateSpellcheck();
      this.syncScrollAndWidth();
    });

    setTimeout(() => {
      if (this.modalTextareaRef?.nativeElement) {
        const textarea = this.modalTextareaRef.nativeElement;
        textarea.focus();
        textarea.setSelectionRange(0, 0);
        textarea.scrollTop = 0;
        this.syncScrollAndWidth();
      }
      this.updateSpellcheck();
    }, 60);
  }

  setSpellcheckLang(lang: 'es' | 'en' | 'off'): void {
    this.spellcheckLang.set(lang);
    this.updateSpellcheck();
    this.syncScrollAndWidth();
    setTimeout(() => {
      this.modalTextareaRef?.nativeElement?.focus();
    }, 0);
  }

  onTextModalInput(val: string): void {
    this.textModalContent.set(val);
    this.updateSpellcheck();
    this.syncScrollAndWidth();
  }

  onTextareaScroll(): void {
    if (this.backdropRef?.nativeElement && this.modalTextareaRef?.nativeElement) {
      this.backdropRef.nativeElement.scrollTop = this.modalTextareaRef.nativeElement.scrollTop;
      this.backdropRef.nativeElement.scrollLeft = this.modalTextareaRef.nativeElement.scrollLeft;
    }
  }

  syncScrollAndWidth(): void {
    if (this.backdropRef?.nativeElement && this.modalTextareaRef?.nativeElement) {
      const textarea = this.modalTextareaRef.nativeElement;
      const backdrop = this.backdropRef.nativeElement;
      backdrop.scrollTop = textarea.scrollTop;
      backdrop.scrollLeft = textarea.scrollLeft;
      const scrollbarWidth = textarea.offsetWidth - textarea.clientWidth;
      backdrop.style.paddingRight = `${14 + scrollbarWidth}px`;
    }
  }

  updateSpellcheck(): void {
    const lang = this.spellcheckLang();
    const text = this.textModalContent();
    const isExpr = this.textModalKind() === 'textField';

    if (lang === 'off' || !text) {
      this.spellErrors.set([]);
      this.backdropHtml.set(this.escapeHtml(text));
      return;
    }

    const errors = this.spellcheckService.checkText(text, lang, isExpr);
    this.spellErrors.set(errors);
    this.backdropHtml.set(this.buildBackdropHtml(text, errors));
  }

  buildBackdropHtml(text: string, errors: SpellError[]): string {
    if (!text) return '&nbsp;';
    if (!errors || errors.length === 0) return this.escapeHtml(text);

    const sorted = [...errors].sort((a, b) => a.startIndex - b.startIndex);
    let html = '';
    let lastIdx = 0;

    for (const err of sorted) {
      if (err.startIndex > lastIdx) {
        html += this.escapeHtml(text.substring(lastIdx, err.startIndex));
      }
      const word = text.substring(err.startIndex, err.endIndex);
      html += `<mark class="spell-error-mark">${this.escapeHtml(word)}</mark>`;
      lastIdx = err.endIndex;
    }
    if (lastIdx < text.length) {
      html += this.escapeHtml(text.substring(lastIdx));
    }
    return html;
  }

  escapeHtml(str: string): string {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  closeTextModal(): void {
    this.textModalOpen.set(false);
    const store = this.getEditorStore();
    if (store) {
      store.stopEditing();
    }
  }

  saveTextModal(): void {
    const store = this.getEditorStore();
    const kind = this.textModalKind();
    const newContent = this.modalTextareaRef?.nativeElement?.value ?? this.textModalContent();
    this.textModalContent.set(newContent);

    if (store) {
      const currentSelections = store.selections();
      if (this.activeEditingPath && (!currentSelections || currentSelections.length === 0)) {
        store.select(this.activeEditingPath);
      }

      if (kind === 'staticText') {
        store.updateSelected((el: any) => ({
          ...el,
          text: newContent
        }));
      } else if (kind === 'textField') {
        store.updateSelected((el: any) => ({
          ...el,
          expression: newContent
        }));
      }
    }

    this.closeTextModal();
  }

  onModalKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.saveTextModal();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.closeTextModal();
    }
  }

  insertIntoExpression(snippet: string): void {
    const textarea = this.modalTextareaRef?.nativeElement;
    if (textarea) {
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const val = textarea.value;
      const next = val.substring(0, start) + snippet + val.substring(end);
      this.textModalContent.set(next);
      setTimeout(() => {
        textarea.focus();
        textarea.setSelectionRange(start + snippet.length, start + snippet.length);
      }, 0);
    } else {
      this.textModalContent.set(this.textModalContent() + snippet);
    }
  }

  getLineCount(text: string): number {
    return text ? text.split('\n').length : 1;
  }
}
