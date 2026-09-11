export interface LetterResourceInfo {
  id: string;
  name: string;
  folderPath: string;
  jrxmlFileName: string;
  format: 'JR6' | 'JR7';
  hasDataAdapter: boolean;
  dataAdapterFile?: string;
  hasXmlData: boolean;
  xmlDataFile?: string;
}

export interface LetterDetailResponse {
  id: string;
  name: string;
  detectedFormat: 'JR6' | 'JR7';
  jrxml: string;
  xmlData?: string;
  dataAdapter?: string;
  images: string[];
  dataAdapterConnected?: boolean;
}

export interface SaveLetterRequest {
  jrxml?: string;
  xmlData?: string;
  dataAdapter?: string;
  format?: 'JR6' | 'JR7';
  saveJrxml?: boolean;
  saveDataAdapter?: boolean;
  saveXmlData?: boolean;
}

export interface SaveLetterResponse {
  status: 'SUCCESS' | 'ERROR';
  message: string;
  dataAdapterConnected?: boolean;
  xmlData?: string;
}

export interface CreateLetterRequest {
  letterId: string;
  name?: string;
  format?: 'JR6' | 'JR7';
  description?: string;
  createDataAdapter?: boolean;
  createXmlData?: boolean;
}

export interface DataFileInfo {
  fileName: string;
  relativePath: string;
  sizeBytes: number;
}

export interface DataAdapterOptionInfo {
  name: string;
  relativePath: string;
  letterId: string;
  location?: string;
}
