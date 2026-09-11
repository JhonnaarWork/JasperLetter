import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  TemplateRef,
  ViewChild,
  ViewEncapsulation,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import * as Prism from 'prismjs';
import { I18nService } from '../../services/i18n.service';

/**
 * Formatea e indenta una cadena XML pura con 2 espacios por nivel.
 */
export function formatXmlString(xml: string): string {
  if (!xml || !xml.trim()) return '';

  let formatted = '';
  let indent = 0;
  const tab = '  ';

  // Normalizar saltos de línea entre tags contiguos
  const cleanXml = xml
    .replace(/(>)\s*(<)(\/?)/g, '$1\n$2$3')
    .replace(/(<[?][^>]*[?>])\s*(<)/g, '$1\n$2');

  const lines = cleanXml.split('\n');

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    const isClosing = /^<\/[^>]+>/.test(line);
    const isSelfClosing = /<[^\/>]+?\/>/.test(line);
    const isSingleLineTag = /^<([a-zA-Z0-9_\-:]+)(?:\s+[^>]*?)?>.*?<\/\1>$/.test(line);
    const isSpecial = /^<\?|^<!--|^<!\[CDATA\[/.test(line);
    const isOpening = /^<[a-zA-Z0-9_\-:]+(?:\s+[^>]*?)?>$/.test(line);

    if (isClosing) {
      indent = Math.max(0, indent - 1);
    }

    formatted += tab.repeat(indent) + line + '\n';

    if (isOpening && !isSelfClosing && !isSingleLineTag && !isSpecial) {
      indent++;
    }
  }

  return formatted.trimEnd();
}

@Component({
  selector: 'app-xml-code-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './xml-code-editor.component.html',
  styleUrls: ['./xml-code-editor.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None
})
export class XmlCodeEditorComponent implements OnInit, OnChanges {
  @ViewChild('textareaRef') textareaRef?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('preRef') preRef?: ElementRef<HTMLPreElement>;
  @ViewChild('gutterRef') gutterRef?: ElementRef<HTMLDivElement>;
  @ViewChild('searchInputRef') searchInputRef?: ElementRef<HTMLInputElement>;

  @Input() code: string = '';
  @Input() readonly: boolean = false;
  @Input() placeholder: string = '';
  @Input() filename: string = '';
  @Input() formatBadge: string = '';
  @Input() subtitle: string = '';
  @Input() extraActionsTemplate?: TemplateRef<any>;
  @Input() defaultTheme: 'light' | 'dark' = 'light';
  @Input() showToolbar: boolean = true;
  @Input() showGutter: boolean = true;
  @Input() showStatusbar: boolean = true;

  @Output() codeChange = new EventEmitter<string>();

  private readonly sanitizer = inject(DomSanitizer);
  readonly i18n = inject(I18nService);

  readonly activeTheme = signal<'light' | 'dark'>('light');
  readonly wordWrap = signal<boolean>(false);
  readonly copied = signal<boolean>(false);
  readonly formattedNotice = signal<boolean>(false);

  // Búsqueda
  readonly searchOpen = signal<boolean>(false);
  readonly searchQuery = signal<string>('');
  readonly searchMatches = signal<{ start: number; end: number; line: number }[]>([]);
  readonly currentMatchIndex = signal<number>(-1);

  // Posición del cursor
  readonly cursorLine = signal<number>(1);
  readonly cursorCol = signal<number>(1);

  // Código interno y resaltado
  readonly currentCode = signal<string>('');
  readonly highlightedHtml = signal<SafeHtml>('');
  readonly lineCount = signal<number>(1);

  readonly lineNumbers = computed(() => {
    const total = this.lineCount();
    const arr: number[] = new Array(total);
    for (let i = 0; i < total; i++) {
      arr[i] = i + 1;
    }
    return arr;
  });

  readonly fileSizeText = computed(() => {
    const bytes = new Blob([this.currentCode()]).size;
    if (bytes < 1024) return `${bytes} B`;
    return `${(bytes / 1024).toFixed(1)} KB`;
  });

  t(key: string, ...params: (string | number)[]): string {
    return this.i18n.t(key, ...params);
  }

  ngOnInit(): void {
    if (this.defaultTheme && this.activeTheme() !== this.defaultTheme) {
      this.activeTheme.set(this.defaultTheme);
    }
    if (!this.currentCode() && this.code) {
      this.updateInternalCode(this.code);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['code']) {
      const newCode = this.code ?? '';
      if (newCode !== this.currentCode()) {
        this.updateInternalCode(newCode);
      }
    }
    if (changes['defaultTheme'] && changes['defaultTheme'].isFirstChange()) {
      this.activeTheme.set(this.defaultTheme);
    }
  }

  private updateInternalCode(val: string): void {
    this.currentCode.set(val);
    const count = val ? val.split('\n').length : 1;
    this.lineCount.set(count);

    let html = '';
    if (val) {
      const rawHighlight = Prism.highlight(val, Prism.languages['markup'], 'markup');
      // Asegurar que si termina en \n la última línea vacía no colapse
      html = val.endsWith('\n') ? rawHighlight + '<br>&nbsp;' : rawHighlight;
    }
    this.highlightedHtml.set(this.sanitizer.bypassSecurityTrustHtml(html));

    if (this.searchOpen() && this.searchQuery().trim()) {
      this.computeSearchMatches();
    }
  }

  onInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    const value = target.value;
    this.updateInternalCode(value);
    this.codeChange.emit(value);
    this.updateCursorPos();
  }

  onScroll(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    const top = target.scrollTop;
    const left = target.scrollLeft;

    if (this.preRef?.nativeElement) {
      this.preRef.nativeElement.scrollTop = top;
      this.preRef.nativeElement.scrollLeft = left;
    }
    if (this.gutterRef?.nativeElement) {
      this.gutterRef.nativeElement.scrollTop = top;
    }
  }

  onKeyDown(event: KeyboardEvent): void {
    const textarea = this.textareaRef?.nativeElement;
    if (!textarea) return;

    // Manejo de tecla Tab para sangría de 2 espacios
    if (event.key === 'Tab') {
      event.preventDefault();
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const val = textarea.value;

      if (!event.shiftKey) {
        // Insertar 2 espacios
        const updated = val.substring(0, start) + '  ' + val.substring(end);
        this.updateInternalCode(updated);
        this.codeChange.emit(updated);
        setTimeout(() => {
          textarea.selectionStart = textarea.selectionEnd = start + 2;
          this.updateCursorPos();
        }, 0);
      } else {
        // Shift+Tab: desindentar si la línea comienza con 2 espacios
        const lineStart = val.lastIndexOf('\n', start - 1) + 1;
        if (val.substring(lineStart, lineStart + 2) === '  ') {
          const updated = val.substring(0, lineStart) + val.substring(lineStart + 2);
          this.updateInternalCode(updated);
          this.codeChange.emit(updated);
          setTimeout(() => {
            const newPos = Math.max(lineStart, start - 2);
            textarea.selectionStart = textarea.selectionEnd = newPos;
            this.updateCursorPos();
          }, 0);
        }
      }
      return;
    }

    // Atajo Ctrl+F o Cmd+F para abrir búsqueda
    if ((event.ctrlKey || event.metaKey) && (event.key === 'f' || event.key === 'F')) {
      event.preventDefault();
      this.openSearch();
      return;
    }

    // Escape para cerrar búsqueda
    if (event.key === 'Escape' && this.searchOpen()) {
      event.preventDefault();
      this.closeSearch();
      textarea.focus();
      return;
    }
  }

  updateCursorPos(): void {
    const textarea = this.textareaRef?.nativeElement;
    if (!textarea) return;

    const selStart = textarea.selectionStart;
    const val = textarea.value.substring(0, selStart);
    const lines = val.split('\n');
    this.cursorLine.set(lines.length);
    this.cursorCol.set(lines[lines.length - 1].length + 1);
  }

  formatCode(): void {
    if (this.readonly) return;
    const formatted = formatXmlString(this.currentCode());
    if (formatted !== this.currentCode()) {
      this.updateInternalCode(formatted);
      this.codeChange.emit(formatted);
      if (this.textareaRef?.nativeElement) {
        this.textareaRef.nativeElement.value = formatted;
      }
    }
    this.formattedNotice.set(true);
    setTimeout(() => this.formattedNotice.set(false), 2200);
  }

  copyCode(): void {
    const text = this.currentCode();
    if (navigator?.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      });
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    }
  }

  toggleWordWrap(): void {
    this.wordWrap.update((v) => !v);
  }

  toggleTheme(): void {
    this.activeTheme.update((t) => (t === 'light' ? 'dark' : 'light'));
  }

  // ==========================================================================
  // Búsqueda en el Código
  // ==========================================================================

  openSearch(): void {
    this.searchOpen.set(true);
    setTimeout(() => {
      this.searchInputRef?.nativeElement?.focus();
      this.searchInputRef?.nativeElement?.select();
    }, 50);
  }

  closeSearch(): void {
    this.searchOpen.set(false);
    this.searchQuery.set('');
    this.searchMatches.set([]);
    this.currentMatchIndex.set(-1);
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.computeSearchMatches();
    if (this.searchMatches().length > 0) {
      this.currentMatchIndex.set(0);
      this.highlightCurrentMatch();
    } else {
      this.currentMatchIndex.set(-1);
    }
  }

  private computeSearchMatches(): void {
    const q = this.searchQuery();
    const code = this.currentCode();
    if (!q || !code) {
      this.searchMatches.set([]);
      this.currentMatchIndex.set(-1);
      return;
    }

    const matches: { start: number; end: number; line: number }[] = [];
    const lowerCode = code.toLowerCase();
    const lowerQ = q.toLowerCase();
    let idx = lowerCode.indexOf(lowerQ);

    while (idx !== -1) {
      const lineNum = code.substring(0, idx).split('\n').length;
      matches.push({
        start: idx,
        end: idx + q.length,
        line: lineNum
      });
      idx = lowerCode.indexOf(lowerQ, idx + q.length);
    }

    this.searchMatches.set(matches);
  }

  findNext(): void {
    const matches = this.searchMatches();
    if (matches.length === 0) return;
    const nextIdx = (this.currentMatchIndex() + 1) % matches.length;
    this.currentMatchIndex.set(nextIdx);
    this.highlightCurrentMatch();
  }

  findPrev(): void {
    const matches = this.searchMatches();
    if (matches.length === 0) return;
    const prevIdx = (this.currentMatchIndex() - 1 + matches.length) % matches.length;
    this.currentMatchIndex.set(prevIdx);
    this.highlightCurrentMatch();
  }

  private highlightCurrentMatch(): void {
    const matches = this.searchMatches();
    const idx = this.currentMatchIndex();
    if (idx < 0 || idx >= matches.length) return;

    const match = matches[idx];
    const textarea = this.textareaRef?.nativeElement;
    if (!textarea) return;

    textarea.focus();
    textarea.setSelectionRange(match.start, match.end);

    const targetScrollTop = Math.max(0, (match.line - 6) * 20);
    textarea.scrollTop = targetScrollTop;
    if (this.preRef?.nativeElement) {
      this.preRef.nativeElement.scrollTop = targetScrollTop;
    }
    if (this.gutterRef?.nativeElement) {
      this.gutterRef.nativeElement.scrollTop = targetScrollTop;
    }
    this.updateCursorPos();
  }
}
