import type { PluginListenerHandle } from "@capacitor/core";

export interface uploadOption {
  /**
   * @since 1.0.0
   * @description The file path of the file to upload
   */
  filePath: string;
  /**
   * @since 1.0.0
   * @description The url of the server
   */
  serverUrl: string;
  /**
  * @since 1.0.0
  * @default 'Uploading'
  * @description The title of the notification
  * Android only
  */
  notificationTitle?: number; 
  /**
   * @since 1.0.0
   * @description The headers to send with the request
   */
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
    statusCode?: number;
  }
  id: string;
}

export interface UploaderPlugin {
  startUpload(options: uploadOption): Promise<{ id: string }>;
  removeUpload(options: { id: string }): Promise<void>;
  addListener(
    eventName: "events",
    listenerFunc: (state: UploadEvent) => void,
  ): Promise<PluginListenerHandle>;
}
