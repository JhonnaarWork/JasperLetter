import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CreateLetterRequest,
  DataAdapterOptionInfo,
  DataFileInfo,
  ImportJrxmlRequest,
  LetterDetailResponse,
  LetterResourceInfo,
  SaveLetterRequest,
  SaveLetterResponse
} from '../models/letter-resource.model';
import { TestDataAdapterResponse } from '../models/data-adapter.model';

export interface HealthResponse {
  status: string;
  engine: string;
  description: string;
}

export interface PreviewRequestPayload {
  jrxml: string;
  parameters?: Record<string, any>;
  letterId?: string;
  xmlData?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PreviewService {
  private readonly http = inject(HttpClient);
  private readonly baseReportsUrl = `${environment.apiBaseUrl}/api/reports`;
  private readonly baseResourcesUrl = `${environment.apiBaseUrl}/api/resources`;

  checkHealth(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>(`${this.baseReportsUrl}/health`);
  }

  getAvailableLetters(): Observable<LetterResourceInfo[]> {
    return this.http.get<LetterResourceInfo[]>(`${this.baseResourcesUrl}/letters`);
  }

  getLetterDetail(letterId: string): Observable<LetterDetailResponse> {
    return this.http.get<LetterDetailResponse>(`${this.baseResourcesUrl}/letters/${letterId}`);
  }

  saveLetter(letterId: string, payload: SaveLetterRequest): Observable<SaveLetterResponse> {
    return this.http.post<SaveLetterResponse>(`${this.baseResourcesUrl}/letters/${letterId}/save`, payload);
  }

  generatePdf(
    jrxml: string,
    parameters?: Record<string, any>,
    letterId?: string,
    xmlData?: string
  ): Observable<Blob> {
    const payload: PreviewRequestPayload = {
      jrxml,
      parameters: parameters || {},
      letterId,
      xmlData
    };
    return this.http.post(`${this.baseReportsUrl}/preview`, payload, {
      responseType: 'blob'
    });
  }

  testDataAdapter(payload: { letterId?: string; dataAdapterXml: string; customXmlData?: string }): Observable<TestDataAdapterResponse> {
    return this.http.post<TestDataAdapterResponse>(`${this.baseResourcesUrl}/data-adapter/test`, payload);
  }

  createLetter(req: CreateLetterRequest): Observable<LetterDetailResponse> {
    return this.http.post<LetterDetailResponse>(`${this.baseResourcesUrl}/letters/create`, req);
  }

  importJrxmlLetter(req: ImportJrxmlRequest): Observable<LetterDetailResponse> {
    return this.http.post<LetterDetailResponse>(`${this.baseResourcesUrl}/letters/import-jrxml`, req);
  }

  importPdfLetter(
    letterId: string,
    file: File,
    format: 'JR6' | 'JR7',
    createDataAdapter: boolean,
    createXmlData: boolean
  ): Observable<LetterDetailResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('letterId', letterId);
    formData.append('format', format);
    formData.append('createDataAdapter', String(createDataAdapter));
    formData.append('createXmlData', String(createXmlData));
    return this.http.post<LetterDetailResponse>(`${this.baseResourcesUrl}/letters/import-pdf`, formData);
  }

  /**
   * Genera un JRXML de layout estático a partir de un PDF sin crear ninguna carta — para cuando
   * el usuario elige "cargar sobre la carta actual" en vez de "crear carta nueva".
   */
  generateJrxmlFromPdf(file: File, letterId: string, format: 'JR6' | 'JR7'): Observable<{ jrxml: string }> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('letterId', letterId);
    formData.append('format', format);
    return this.http.post<{ jrxml: string }>(`${this.baseResourcesUrl}/generate-jrxml-from-pdf`, formData);
  }

  getDataAdapters(): Observable<DataAdapterOptionInfo[]> {
    return this.http.get<DataAdapterOptionInfo[]>(`${this.baseResourcesUrl}/data-adapters`);
  }

  getDataXmlFiles(): Observable<DataFileInfo[]> {
    return this.http.get<DataFileInfo[]>(`${this.baseResourcesUrl}/data-xml-files`);
  }

  createDataXmlFile(payload: { letterId: string; fileName: string; content?: string }): Observable<DataFileInfo> {
    return this.http.post<DataFileInfo>(`${this.baseResourcesUrl}/data-xml-files/create`, payload);
  }
}
