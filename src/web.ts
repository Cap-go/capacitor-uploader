import { WebPlugin } from '@capacitor/core';

import type { UploaderPlugin, uploadOption } from './definitions';

export class UploaderWeb extends WebPlugin implements UploaderPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
  async startUpload(options: uploadOption): Promise<{ id: string }> {
    console.log('startUpload', options);
    this.unimplemented('startUpload');
    return { id: '123' };
  }
  async removeUpload(options: { id: string }): Promise<void> {
    console.log('removeUpload', options);
    this.unimplemented('removeUpload');
    return;
  }
}
