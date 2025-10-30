import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Configuration options for uploading a file.
 *
 * @since 0.0.1
 */
export interface uploadOption {
  /**
   * The local file path of the file to upload.
   * Can be a file:// URL or an absolute path.
   *
   * @since 0.0.1
   */
  filePath: string;

  /**
   * The server URL endpoint where the file should be uploaded.
   *
   * @since 0.0.1
   */
  serverUrl: string;

  /**
   * The title of the upload notification shown to the user.
   * Android only.
   *
   * @default 'Uploading'
   * @since 0.0.1
   */
  notificationTitle?: number;

  /**
   * HTTP headers to send with the upload request.
   * Useful for authentication tokens, content types, etc.
   *
   * @since 0.0.1
   * @example
   * ```typescript
   * headers: {
   *   'Authorization': 'Bearer token123',
   *   'X-Custom-Header': 'value'
   * }
   * ```
   */
  headers: {
    [key: string]: string;
  };

  /**
   * The HTTP method to use for the upload request.
   *
   * @default 'POST'
   * @since 0.0.1
   */
  method?: 'PUT' | 'POST';

  /**
   * The MIME type of the file being uploaded.
   * If not specified, the plugin will attempt to determine it automatically.
   *
   * @since 0.0.1
   * @example 'image/jpeg', 'application/pdf', 'video/mp4'
   */
  mimeType?: string;

  /**
   * Additional form parameters to send with the upload request.
   * These will be included as form data in multipart uploads.
   *
   * @since 0.0.1
   */
  parameters?: { [key: string]: string };

  /**
   * The maximum number of times to retry the upload if it fails.
   *
   * @since 0.0.1
   * @default 0
   */
  maxRetries?: number;

  /**
   * The type of upload to perform.
   * - 'binary': Uploads the file as raw binary data in the request body
   * - 'multipart': Uploads the file as multipart/form-data
   *
   * @default 'binary'
   * @since 0.0.2
   */
  uploadType?: 'binary' | 'multipart';

  /**
   * The form field name for the file when using multipart upload type.
   * Only used when uploadType is 'multipart'.
   *
   * @default 'file'
   * @since 0.0.2
   */
  fileField?: string;
}

/**
 * Event emitted during the upload lifecycle.
 *
 * @since 0.0.1
 */
export interface UploadEvent {
  /**
   * The current status of the upload.
   * - 'uploading': Upload is in progress
   * - 'completed': Upload finished successfully
   * - 'failed': Upload encountered an error
   *
   * @since 0.0.1
   */
  name: 'uploading' | 'completed' | 'failed';

  /**
   * Additional data about the upload event.
   *
   * @since 0.0.1
   */
  payload: {
    /**
     * Upload progress percentage from 0 to 100.
     * Only present during 'uploading' events.
     *
     * @since 0.0.1
     */
    percent?: number;

    /**
     * Error message if the upload failed.
     * Only present during 'failed' events.
     *
     * @since 0.0.1
     */
    error?: string;

    /**
     * HTTP status code returned by the server.
     * Present during 'completed' and 'failed' events.
     *
     * @since 0.0.1
     */
    statusCode?: number;
  };

  /**
   * Unique identifier for this upload task.
   *
   * @since 0.0.1
   */
  id: string;
}

/**
 * Capacitor Uploader Plugin for uploading files with background support and progress tracking.
 *
 * @since 0.0.1
 */
export interface UploaderPlugin {
  /**
   * Start uploading a file to a server.
   *
   * The upload will continue in the background even if the app is closed or backgrounded.
   * Listen to upload events to track progress, completion, or failure.
   *
   * @param options - Configuration for the upload
   * @returns Promise that resolves with the upload ID
   * @throws Error if the upload fails to start
   * @since 0.0.1
   * @example
   * ```typescript
   * const { id } = await Uploader.startUpload({
   *   filePath: 'file:///path/to/file.jpg',
   *   serverUrl: 'https://example.com/upload',
   *   headers: {
   *     'Authorization': 'Bearer token'
   *   },
   *   method: 'POST',
   *   uploadType: 'multipart',
   *   fileField: 'photo'
   * });
   * console.log('Upload started with ID:', id);
   * ```
   */
  startUpload(options: uploadOption): Promise<{ id: string }>;

  /**
   * Cancel and remove an ongoing upload.
   *
   * This will stop the upload if it's in progress and clean up resources.
   *
   * @param options - Object containing the upload ID to remove
   * @returns Promise that resolves when the upload is removed
   * @throws Error if the upload ID is not found
   * @since 0.0.1
   * @example
   * ```typescript
   * await Uploader.removeUpload({ id: 'upload-123' });
   * ```
   */
  removeUpload(options: { id: string }): Promise<void>;

  /**
   * Listen for upload progress and status events.
   *
   * Events are fired for:
   * - Upload progress updates (with percent)
   * - Upload completion (with statusCode)
   * - Upload failure (with error and statusCode)
   *
   * @param eventName - Must be 'events'
   * @param listenerFunc - Callback function to handle upload events
   * @returns Promise that resolves with a listener handle for removal
   * @since 0.0.1
   * @example
   * ```typescript
   * const listener = await Uploader.addListener('events', (event) => {
   *   if (event.name === 'uploading') {
   *     console.log(`Upload ${event.id}: ${event.payload.percent}%`);
   *   } else if (event.name === 'completed') {
   *     console.log(`Upload ${event.id} completed`);
   *   } else if (event.name === 'failed') {
   *     console.error(`Upload ${event.id} failed:`, event.payload.error);
   *   }
   * });
   *
   * // Remove listener when done
   * await listener.remove();
   * ```
   */
  addListener(eventName: 'events', listenerFunc: (state: UploadEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Get the native Capacitor plugin version.
   *
   * @returns Promise that resolves with the plugin version
   * @throws Error if getting the version fails
   * @since 0.0.1
   * @example
   * ```typescript
   * const { version } = await Uploader.getPluginVersion();
   * console.log('Plugin version:', version);
   * ```
   */
  getPluginVersion(): Promise<{ version: string }>;
}
