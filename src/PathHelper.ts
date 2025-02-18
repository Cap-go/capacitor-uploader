export class PathHelper {
  // Check if the path follows the idb://[databaseName]/[storeName]/[key] format
  static isIndexedDBPath(path: string): boolean {
    const regex = /^idb:\/\/([^/]+)\/([^/]+)\/(.+)$/;
    return regex.test(path);
  }

  // Parse the path to extract database, storeName, and key
  static parseIndexedDBPath(path: string): { database: string, storeName: string, key: string } {
    const regex = /^idb:\/\/([^/]+)\/([^/]+)\/(.+)$/;
    const match = path.match(regex);

    if (!match) {
      throw new Error('Invalid IndexedDB path format');
    }

    return {
      database: match[1],
      storeName: match[2],
      key: match[3],
    };
  }
}
