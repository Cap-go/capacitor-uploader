# @capgo/capacitor-uploader
  <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>
  <div align="center">
<h2><a href="https://capgo.app/">Check out: Capgo — Instant updates for capacitor</a></h2>
</div>

## Uploader Plugin

This plugin provides a flexible way to upload natively files to various servers, including S3 with presigned URLs.

WIP: this is a work in progress still not ready for use

## Install

```bash
npm install @capgo/capacitor-uploader
npx cap sync
```

## Android:

Add the following to your `AndroidManifest.xml` file:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## Exemple S3 upload:


## Example S3 upload:

```typescript
import { Uploader } from '@capgo/capacitor-uploader';

async function uploadToS3(filePath: string, presignedUrl: string, fields: Record<string, string>) {
  try {
    const { id } = await Uploader.startUpload({
      filePath: filePath,
      serverUrl: presignedUrl,
      method: 'PUT',
      parameters: fields,
      notificationTitle: 'Uploading to S3'
    });

    console.log('Upload started with ID:', id);

    // Listen for upload events
    Uploader.addListener('events', (event: UploadEvent) => {
      if (event.name === 'uploading') {
        console.log(`Upload progress: ${event.payload.percent}%`);
      } else if (event.name === 'completed') {
        console.log('Upload completed successfully');
      } else if (event.name === 'failed') {
        console.error('Upload failed:', event.payload.error);
      }
    });

  } catch (error) {
    console.error('Failed to start upload:', error);
  }
}

```

### Exemple upload to a custom server:

```typescript
import { Uploader } from '@capgo/capacitor-uploader';

async function uploadToCustomServer(filePath: string, serverUrl: string) {
  try {
    // Start the upload
    const { id } = await Uploader.startUpload({
      filePath: filePath,
      serverUrl: serverUrl,
      method: 'POST',
      headers: {
        'Authorization': 'Bearer your-auth-token-here'
      },
      parameters: {
        'user_id': '12345',
        'file_type': 'image'
      },
      notificationTitle: 'Uploading to Custom Server',
      maxRetries: 3
    });

    console.log('Upload started with ID:', id);

    // Listen for upload events
    Uploader.addListener('events', (event) => {
      switch (event.name) {
        case 'uploading':
          console.log(`Upload progress: ${event.payload.percent}%`);
          break;
        case 'completed':
          console.log('Upload completed successfully');
          console.log('Server response status code:', event.payload.statusCode);
          break;
        case 'failed':
          console.error('Upload failed:', event.payload.error);
          break;
      }
    });

    // Optional: Remove the upload if needed
    // await Uploader.removeUpload({ id: id });

  } catch (error) {
    console.error('Failed to start upload:', error);
  }
}

// Usage
const filePath = 'file:///path/to/your/file.jpg';
const serverUrl = 'https://your-custom-server.com/upload';
uploadToCustomServer(filePath, serverUrl);

```

## API

<docgen-index>

* [`startUpload(...)`](#startupload)
* [`removeUpload(...)`](#removeupload)
* [`addListener('events', ...)`](#addlistenerevents-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startUpload(...)

```typescript
startUpload(options: uploadOption) => Promise<{ id: string; }>
```

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#uploadoption">uploadOption</a></code> |

**Returns:** <code>Promise&lt;{ id: string; }&gt;</code>

--------------------


### removeUpload(...)

```typescript
removeUpload(options: { id: string; }) => Promise<void>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### addListener('events', ...)

```typescript
addListener(eventName: "events", listenerFunc: (state: UploadEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                    |
| ------------------ | ----------------------------------------------------------------------- |
| **`eventName`**    | <code>'events'</code>                                                   |
| **`listenerFunc`** | <code>(state: <a href="#uploadevent">UploadEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### uploadOption

| Prop                    | Type                                    | Default                  | Since |
| ----------------------- | --------------------------------------- | ------------------------ | ----- |
| **`filePath`**          | <code>string</code>                     |                          | 1.0.0 |
| **`serverUrl`**         | <code>string</code>                     |                          | 1.0.0 |
| **`notificationTitle`** | <code>number</code>                     | <code>'Uploading'</code> | 1.0.0 |
| **`headers`**           | <code>{ [key: string]: string; }</code> |                          | 1.0.0 |
| **`method`**            | <code>'PUT' \| 'POST'</code>            | <code>'POST'</code>      | 1.0.0 |
| **`mimeType`**          | <code>string</code>                     |                          | 1.0.0 |
| **`parameters`**        | <code>{ [key: string]: string; }</code> |                          | 1.0.0 |
| **`maxRetries`**        | <code>number or upload retry</code>     |                          | 1.0.0 |

#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### UploadEvent

| Prop          | Type                                                                    | Description                                  |
| ------------- | ----------------------------------------------------------------------- | -------------------------------------------- |
| **`name`**    | <code>'uploading' \| 'completed' \| 'failed'</code>                     | Current status of upload, between 0 and 100. |
| **`payload`** | <code>{ percent?: number; error?: string; statusCode?: number; }</code> |                                              |
| **`id`**      | <code>string</code>                                                     |                                              |

</docgen-api>

### Credits:

For the inspiration and the code on ios: https://github.com/Vydia/react-native-background-upload/tree/master
For the API definition: https://www.npmjs.com/package/cordova-plugin-background-upload-put-s3

