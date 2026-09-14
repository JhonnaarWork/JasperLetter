import { Injectable, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';
import { PreviewService } from '../../../services/preview.service';
import { I18nService } from '../../../services/i18n.service';
import { LetterCatalogService } from './letter-catalog.service';
import { LETTER_SAMPLES } from '../../../models/samples';
import { CreateLetterRequest, DataAdapterOptionInfo, DataFileInfo } from '../../../models/letter-resource.model';
import {
  XmlDataAdapterModel,
  TestDataAdapterResponse,
  getDefaultXmlDataAdapterModel,
  linkDataAdapterToJrxml as linkDataAdapterToJrxmlContent,
  normalizeXmlDataAdapterPath,
  parseXmlDataAdapter,
  serializeXmlDataAdapter
} from '../../../models/data-adapter.model';
import { createStarterXmlData } from '../../../models/xml-data.model';
import { environment } from '../../../../environments/environment';

/**
 * Estado y operaciones de la carta actualmente abierta: contenido (JRXML, XML de datos, Data
 * Adapter), seguimiento de cambios sin guardar, guardado granular, vista previa PDF,
 * parámetros de prueba, y los flujos de vinculación de Data Adapter (probar conexión,
 * seleccionar uno existente, crear uno nuevo). Extraído de AppComponent (que concentraba todo
 * esto directamente) como parte del refactor de arquitectura.
 *
 * Es deliberadamente una única store y no varias más pequeñas: una sola acción del usuario
 * (por ejemplo, "crear Data Adapter para esta carta") escribe a la vez sobre jrxml, dataAdapter
 * y xmlData, así que separarlas obligaría a esas operaciones a cruzar servicios o duplicar
 * lógica de vinculación entre ellos.
 */
@Injectable({ providedIn: 'root' })
export class LetterEditorStore {
  private readonly previewService = inject(PreviewService);
  private readonly i18n = inject(I18nService);
  private readonly catalog = inject(LetterCatalogService);
  private readonly sanitizer = inject(DomSanitizer);

  // ---------- Selección de carta / muestra ----------
  readonly samples = LETTER_SAMPLES;
  readonly currentSelection = signal<string>('');
  readonly selectedLetterId = signal<string | null>(null);
  readonly selectedSampleId = signal<string | null>(null);
  readonly letterFormat = signal<'JR6' | 'JR7'>('JR6');
  readonly switchingLetter = signal<boolean>(false);
  readonly switchingMessage = signal<string>('');

  // ---------- Contenido de la carta ----------
  readonly jrxml = signal<string>('');
  readonly originalJrxml = signal<string>('');
  readonly xmlData = signal<string>('');
  readonly originalXmlData = signal<string>('');
  readonly dataAdapter = signal<string>('');
  readonly originalDataAdapter = signal<string>('');
  readonly dataAdapterMode = signal<'form' | 'xml'>('form');
  readonly dataAdapterModel = signal<XmlDataAdapterModel>(getDefaultXmlDataAdapterModel());
  readonly isDataAdapterConnected = signal<boolean>(false);
  readonly parameters = signal<Record<string, string>>({});

  // ---------- Seguimiento de cambios sin guardar ----------
  readonly isJrxmlDirty = computed(() => !!this.selectedLetterId() && this.jrxml() !== this.originalJrxml());
  readonly isDataAdapterDirty = computed(() => !!this.selectedLetterId() && this.dataAdapter() !== this.originalDataAdapter());
  readonly isXmlDataDirty = computed(() => !!this.selectedLetterId() && this.xmlData() !== this.originalXmlData());
  readonly hasUnsavedChanges = computed(() => this.isJrxmlDirty() || this.isDataAdapterDirty() || this.isXmlDataDirty());
  readonly dirtyCount = computed(() => (this.isJrxmlDirty() ? 1 : 0) + (this.isDataAdapterDirty() ? 1 : 0) + (this.isXmlDataDirty() ? 1 : 0));

  // ---------- Parámetros de prueba ----------
  readonly parameterEntries = computed(() => Object.entries(this.parameters()).map(([key, value]) => ({ key, value })));

  // ---------- Vista previa PDF ----------
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  readonly rawPdfBlob = signal<Blob | null>(null);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  // ---------- Guardado granular ----------
  readonly saveConfirmModalOpen = signal<boolean>(false);
  readonly saveSelectJrxml = signal<boolean>(true);
  readonly saveSelectDataAdapter = signal<boolean>(true);
  readonly saveSelectXmlData = signal<boolean>(true);
  readonly saving = signal<boolean>(false);
  readonly saveStatus = signal<{ type: 'success' | 'error' | null; message: string | null }>({
    type: null,
    message: null
  });

  // ---------- Prueba de Data Adapter ----------
  readonly testingDataAdapter = signal<boolean>(false);
  readonly testResultModalOpen = signal<boolean>(false);
  readonly testResult = signal<TestDataAdapterResponse | null>(null);

  // ---------- Selección de Data Adapter existente ----------
  readonly selectAdapterModalOpen = signal<boolean>(false);
  readonly availableAdapters = signal<DataAdapterOptionInfo[]>([]);
  readonly loadingAdapters = signal<boolean>(false);

  // ---------- Selección/creación de archivo de datos XML ----------
  readonly selectXmlDataModalOpen = signal<boolean>(false);
  readonly availableXmlFiles = signal<DataFileInfo[]>([]);
  readonly loadingXmlFiles = signal<boolean>(false);
  readonly newXmlFileName = signal<string>('');
  readonly isCreatingNewXml = signal<boolean>(false);

  // ==========================================================================
  // Carga de carta / muestra
  // ==========================================================================

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
      return;
    }
    if (val.startsWith('resource:')) {
      this.loadLetterFromResource(val.substring('resource:'.length));
    } else if (val.startsWith('sample:')) {
      this.loadSample(val.substring('sample:'.length));
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
        this.errorMessage.set(this.i18n.t('toast.loadError', letterId, err.error?.message || err.message));
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

  // ==========================================================================
  // Edición de JRXML y datos XML
  // ==========================================================================

  onJrxmlChange(newXml: string): void {
    this.jrxml.set(newXml);
  }

  /**
   * El editor visual re-serializa el JRXML al recibir un contenido nuevo desde fuera (p.ej. al
   * cambiar de carta) y emite ese resultado como si fuera un cambio. No es una edición real del
   * usuario, así que se actualiza también el "original" para que no quede marcada como con
   * cambios pendientes solo por haberla abierto. Ver VisualEditorPaneComponent.jrxmlBaselineSync.
   */
  onJrxmlBaselineSync(newXml: string): void {
    this.jrxml.set(newXml);
    this.originalJrxml.set(newXml);
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

  // ==========================================================================
  // Gestión de Data Adapter
  // ==========================================================================

  setDataAdapterMode(mode: 'form' | 'xml'): void {
    if (mode === 'form') {
      this.dataAdapterModel.set(parseXmlDataAdapter(this.dataAdapter(), this.selectedLetterId() || undefined));
    } else {
      this.dataAdapter.set(serializeXmlDataAdapter(this.dataAdapterModel()));
    }
    this.dataAdapterMode.set(mode);
  }

  updateDataAdapterField<K extends keyof XmlDataAdapterModel>(field: K, value: XmlDataAdapterModel[K]): void {
    const updated = { ...this.dataAdapterModel(), [field]: value };
    this.dataAdapterModel.set(updated);
    this.dataAdapter.set(serializeXmlDataAdapter(updated));
  }

  suggestStandardXmlPath(): void {
    const letterId = this.selectedLetterId();
    if (!letterId) return;
    this.updateDataAdapterField('location', `src\\main\\resources\\data\\xml\\${letterId}.xml`);
  }

  onDataAdapterChange(newData: string): void {
    this.dataAdapter.set(newData);
    try {
      this.dataAdapterModel.set(parseXmlDataAdapter(newData, this.selectedLetterId() || undefined));
    } catch {}
  }

  resetDataAdapter(): void {
    const original = this.originalDataAdapter();
    this.dataAdapter.set(original);
    this.dataAdapterModel.set(parseXmlDataAdapter(original, this.selectedLetterId() || undefined));
  }

  runDataAdapterTest(): void {
    const xml = this.dataAdapterMode() === 'form' ? serializeXmlDataAdapter(this.dataAdapterModel()) : this.dataAdapter();

    this.testingDataAdapter.set(true);
    this.previewService.testDataAdapter({ letterId: this.selectedLetterId() || undefined, dataAdapterXml: xml }).subscribe({
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

  linkDataAdapterToJrxml(adapterRelativePath: string): void {
    if (!this.jrxml()) return;
    this.jrxml.set(linkDataAdapterToJrxmlContent(this.jrxml(), adapterRelativePath));
  }

  linkDataXmlToAdapter(xmlRelativePath: string): void {
    this.updateDataAdapterField('location', normalizeXmlDataAdapterPath(xmlRelativePath));
  }

  // ---------- Seleccionar Data Adapter existente ----------

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
    this.dataAdapter.set(serializeXmlDataAdapter(model));
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

    this.dataAdapter.set(serializeXmlDataAdapter(model));
    this.dataAdapterModel.set(model);
    this.linkDataAdapterToJrxml('xmlDataAdapter.xml');
    this.linkDataXmlToAdapter(xmlLocation);
    this.reloadXmlDataFromLetter(letterId);
  }

  // ---------- Seleccionar o crear archivo de datos XML ----------

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
    const starter = createStarterXmlData(letterId);

    this.previewService.createDataXmlFile({ letterId, fileName: fname, content: starter }).subscribe({
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
        // TODO(F-3 de la Fase 3, SelectXmlDataModal): reemplazar por saveStatus/errorMessage,
        // consistente con el resto de la app en vez de un alert() nativo.
        alert(err.error?.message || 'Error al crear archivo de datos XML');
      }
    });
  }

  // ==========================================================================
  // Parámetros de prueba
  // ==========================================================================

  onParameterChange(key: string, value: string): void {
    this.parameters.set({ ...this.parameters(), [key]: value });
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

  // ==========================================================================
  // Vista previa PDF
  // ==========================================================================

  generatePreview(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const letterId = this.selectedLetterId() || undefined;
    const xmlData = this.xmlData() || undefined;

    this.previewService.generatePdf(this.jrxml(), this.parameters(), letterId, xmlData).subscribe({
      next: (blob: Blob) => {
        this.rawPdfBlob.set(blob);
        const objectUrl = URL.createObjectURL(blob);
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
        this.catalog.backendOnline.set(true);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 0) {
          this.catalog.backendOnline.set(false);
          const apiBaseUrl = environment.apiBaseUrl || (typeof window !== 'undefined' ? window.location.origin : '');
          this.errorMessage.set(this.i18n.t('preview.errorConnection', apiBaseUrl));
        } else if (err.error instanceof Blob) {
          const reader = new FileReader();
          reader.onload = () => {
            try {
              const parsed = JSON.parse(reader.result as string);
              this.errorMessage.set(parsed.cause || parsed.message || this.i18n.t('preview.errorGeneric'));
            } catch {
              this.errorMessage.set(this.i18n.t('preview.errorTemplate'));
            }
          };
          reader.readAsText(err.error);
        } else {
          this.errorMessage.set(err.message || this.i18n.t('preview.errorGeneric'));
        }
      }
    });
  }

  // ==========================================================================
  // Guardado granular
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

  /**
   * Guarda los componentes seleccionados (saveSelectJrxml/DataAdapter/XmlData) para la carta
   * actual. Devuelve true si el guardado se completó (o si no había nada seleccionado, en cuyo
   * caso simplemente cierra el modal), false si falló — para que el llamador (por ejemplo, el
   * guard de cambios sin guardar) sepa si corresponde continuar con la acción pendiente.
   */
  async saveGranular(): Promise<boolean> {
    const letterId = this.selectedLetterId();
    if (!letterId) return false;

    const doJrxml = this.saveSelectJrxml();
    const doAdapter = this.saveSelectDataAdapter();
    const doXml = this.saveSelectXmlData();

    if (!doJrxml && !doAdapter && !doXml) {
      this.closeSaveConfirmModal();
      return false;
    }

    this.saving.set(true);
    this.saveStatus.set({ type: null, message: null });

    try {
      const res = await firstValueFrom(
        this.previewService.saveLetter(letterId, {
          jrxml: doJrxml ? this.jrxml() : undefined,
          saveJrxml: doJrxml,
          xmlData: doXml ? this.xmlData() || '' : undefined,
          saveXmlData: doXml,
          dataAdapter: doAdapter ? this.dataAdapter() || '' : undefined,
          saveDataAdapter: doAdapter,
          format: this.letterFormat()
        })
      );

      this.saving.set(false);
      this.closeSaveConfirmModal();

      if (doJrxml) this.originalJrxml.set(this.jrxml());
      if (doAdapter) this.originalDataAdapter.set(this.dataAdapter());
      if (doXml) this.originalXmlData.set(this.xmlData());

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

      return true;
    } catch (err: any) {
      this.saving.set(false);
      this.saveStatus.set({
        type: 'error',
        message: this.i18n.t('toast.saveError', err.error?.message || err.message)
      });
      return false;
    }
  }

  // ==========================================================================
  // Creación de nueva carta
  // ==========================================================================

  /**
   * Crea la carta en el backend, recarga el catálogo y selecciona la recién creada. La
   * validación del id y el manejo de creatingLetter/createLetterError quedan del lado del
   * llamador (AppComponent): esos son estado de UI del modal de creación, no de esta store.
   * Lanza si el request falla, para que el llamador decida cómo mostrar el error.
   */
  async createLetter(req: CreateLetterRequest): Promise<void> {
    await firstValueFrom(this.previewService.createLetter(req));
    const letters = await firstValueFrom(this.previewService.getAvailableLetters());
    this.catalog.resourceLetters.set(letters);
    this.currentSelection.set(`resource:${req.letterId}`);
    this.loadLetterFromResource(req.letterId!);
  }
}
