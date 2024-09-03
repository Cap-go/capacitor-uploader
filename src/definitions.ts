import type { PluginListenerHandle } from "@capacitor/core";

export interface uploadOption {
  id: string;
  filePath: string;
  serverUrl: string;
  notificationTitle: number;
  headers: {
    [key: string]: string;
  }
}
export interface UploadEvent {
  /**
   * Current status of upload, between 0 and 100.
   *
   */
  name: 'uploading' | 'completed' | 'failed';
  payload: {
    percent?: number;
    error?: string; 
  }
  id: string;
}

export interface UploaderPlugin {
  startUpload(options: uploadOption): Promise<{ value: string }>;
  removeUpload(options: { id: string }): Promise<void>;
  addListener(
    eventName: "events",
    listenerFunc: (state: UploadEvent) => void,
  ): Promise<PluginListenerHandle>;
}
