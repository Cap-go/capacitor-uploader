import { WebPlugin } from "@capacitor/core";
import { openDB } from 'idb';

import { PathHelper } from "./PathHelper";

import type { UploaderPlugin, uploadOption } from "./definitions";

export class UploaderWeb extends WebPlugin implements UploaderPlugin {
  private uploads: Map<
    string,
    { controller: AbortController; retries: number }
  > = new Map();

  async startUpload(options: uploadOption): Promise<{ id: string }> {
    console.log("startUpload", options);

    const id = Math.random().toString(36).substring(2, 15);
    const controller = new AbortController();
    const maxRetries = options.maxRetries || 3;
    this.uploads.set(id, { controller, retries: maxRetries });

    this.doUpload(id, options);

    return { id };
  }

  async removeUpload(options: { id: string }): Promise<void> {
    console.log("removeUpload", options);
    const upload = this.uploads.get(options.id);
    if (upload) {
      upload.controller.abort();
      this.uploads.delete(options.id);
      this.notifyListeners("events", {
        name: "cancelled",
        id: options.id,
        payload: {},
      });
    }
  }

  private async doUpload(id: string, options: uploadOption) {
    const {
      filePath,
      serverUrl,
      headers = {},
      method = "POST",
      parameters = {},
    } = options;
    const upload = this.uploads.get(id);

    if (!upload) return;

    try {
      const file = await this.getFileFromPath(filePath);
      if (!file) throw new Error("File not found");

      const formData = new FormData();
      formData.append("file", file);

      for (const [key, value] of Object.entries(parameters)) {
        formData.append(key, value);
      }

      const response = await fetch(serverUrl, {
        method,
        headers,
        body: method === "PUT" ? file : formData,
        signal: upload.controller.signal,
      });

      if (!response.ok)
        throw new Error(`HTTP error! status: ${response.status}`);

      this.notifyListeners("events", {
        name: "completed",
        id,
        payload: { statusCode: response.status },
      });

      this.uploads.delete(id);
    } catch (error) {
      if ((error as Error).name === "AbortError") return;

      if (upload.retries > 0) {
        upload.retries--;
        console.log(`Retrying upload (retries left: ${upload.retries})`);
        setTimeout(() => this.doUpload(id, options), 1000);
      } else {
        this.notifyListeners("events", {
          name: "failed",
          id,
          payload: { error: (error as Error).message },
        });
        this.uploads.delete(id);
      }
    }
  }

  private async getFileFromPath(filePath: string): Promise<File | null> {
    // Check if the path is an IndexedDB path
    if (PathHelper.isIndexedDBPath(filePath)) {
      return this.getFileFromIndexedDB(filePath);
    }

    // Otherwise, treat it as a file path from the system
    return this.getFileFromSystem(filePath);
  }

  // Retrieve the file from IndexedDB
  private async getFileFromIndexedDB(filePath: string): Promise<File | null> {
    // Parse the path to get the database, store name, and key
    const { database, storeName, key } = PathHelper.parseIndexedDBPath(filePath);

    try {
      // Open the IndexedDB database and access the object store
      const db = await openDB(database, 1, {
        upgrade(db) {
          if (!db.objectStoreNames.contains(storeName)) {
            db.createObjectStore(storeName);
          }
        },
      });

      // Get the blob from the store
      const blob = await db.get(storeName, key);
      if (!blob) {
        console.error(`File with key "${key}" not found in store "${storeName}" in database "${database}"`);
        return null;
      }

      // Convert the Blob to a File object
      return new File([blob], key, { type: blob.type });
    } catch (error) {
      console.error('Error retrieving file from IndexedDB:', error);
      return null;
    }
  }

  // Retrieve the file from the system (local file)
  private async getFileFromSystem(filePath: string): Promise<File | null> {
    try {
      // This is a simplified version. In a real-world scenario,
      // you might need to handle different types of paths or use a file system API.
      const response = await fetch(filePath);
      const blob = await response.blob();
      return new File([blob], filePath.split("/").pop() || "file", {
        type: blob.type,
      });
    } catch (error) {
      console.error("Error getting file from system:", error);
      return null;
    }
  }
}
