import { WebPlugin } from '@capacitor/core';

import type { UploaderPlugin } from './definitions';

export class UploaderWeb extends WebPlugin implements UploaderPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
