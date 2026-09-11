import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CreateLetterRequest,
  DataAdapterOptionInfo,
  DataFileInfo,
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
