import { WebPlugin } from '@capacitor/core';

import type { UploaderPlugin, uploadOption } from './definitions';

export class UploaderWeb extends WebPlugin implements UploaderPlugin {
  private uploads: Map<string, { controller: AbortController; retries: number }> = new Map();

  async startUpload(options: uploadOption): Promise<{ id: string }> {
    console.log('startUpload', options);

    const id = Math.random().toString(36).substring(2, 15);
    const controller = new AbortController();
    const maxRetries = options.maxRetries || 3;
    this.uploads.set(id, { controller, retries: maxRetries });

    this.doUpload(id, options);

    return { id };
  }

  async removeUpload(options: { id: string }): Promise<void> {
    console.log('removeUpload', options);
    const upload = this.uploads.get(options.id);
    if (upload) {
      upload.controller.abort();
      this.uploads.delete(options.id);
      this.notifyListeners('events', {
        name: 'cancelled',
        id: options.id,
        payload: {},
      });
    }
  }

  private async doUpload(id: string, options: uploadOption) {
    const { filePath, serverUrl, headers = {}, method = 'POST', parameters = {} } = options;
    const upload = this.uploads.get(id);

    if (!upload) return;

    try {
      const file = await this.getFileFromPath(filePath);
      if (!file) throw new Error('File not found');

      const formData = new FormData();
      formData.append('file', file);

      for (const [key, value] of Object.entries(parameters)) {
        formData.append(key, value);
      }

      const response = await fetch(serverUrl, {
        method,
        headers,
        body: method === 'PUT' ? file : formData,
        signal: upload.controller.signal,
      });

      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

      this.notifyListeners('events', {
        name: 'completed',
        id,
        payload: { statusCode: response.status },
      });

      this.uploads.delete(id);
    } catch (error) {
      if ((error as Error).name === 'AbortError') return;

      if (upload.retries > 0) {
        upload.retries--;
        console.log(`Retrying upload (retries left: ${upload.retries})`);
        setTimeout(() => this.doUpload(id, options), 1000);
      } else {
        this.notifyListeners('events', {
          name: 'failed',
          id,
          payload: { error: (error as Error).message },
        });
        this.uploads.delete(id);
      }
    }
  }

  private async getFileFromPath(filePath: string): Promise<File | null> {
    // This is a simplified version. In a real-world scenario,
    // you might need to handle different types of paths or use a file system API.
    try {
      const response = await fetch(filePath);
      const blob = await response.blob();
      return new File([blob], filePath.split('/').pop() || 'file', {
        type: blob.type,
      });
    } catch (error) {
      console.error('Error getting file:', error);
      return null;
    }
  }
}
