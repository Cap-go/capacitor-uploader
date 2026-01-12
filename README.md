# @capgo/capacitor-uploader
Upload files in the background with progress tracking, resumable uploads, and network-aware handling for Capacitor apps.


 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>
 
<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_uploader"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_uploader"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

## Uploader Plugin

This plugin provides a flexible way to upload natively files to various servers, including S3 with presigned URLs.

Can be used in combination with the [Capacitor Camera preview](https://github.com/Cap-go/camera-preview) To upload file in reliable manner instead of reading them in buffer of webview and then upload in JS.

On the web, file paths support IndexedDB (IDB) semantic paths using the following format:  
`idb://[database-name]/[collection-name]/[key]`  
This allows seamless integration with IndexedDB for storing and retrieving files.

## Documentation

The most complete doc is available here: https://capgo.app/docs/plugins/uploader/

## Install

```bash
npm install @capgo/capacitor-uploader
npx cap sync
```

## Android

Add the following to your `AndroidManifest.xml` file:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## Example S3 upload

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

### Example upload to a custom server

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

### Example with Capacitor Camera preview

Documentation for the [Capacitor Camera preview](https://github.com/Cap-go/camera-preview)

```typescript
  import { CameraPreview } from '@capgo/camera-preview'
  import { Uploader } from '@capgo/capacitor-uploader';


  async function record() {
    await CameraPreview.startRecordVideo({ storeToFile: true })
    await new Promise(resolve => setTimeout(resolve, 5000))
    const fileUrl = await CameraPreview.stopRecordVideo()
    console.log(fileUrl.videoFilePath)
    await uploadVideo(fileUrl.videoFilePath)
  }

  async function uploadVideo(filePath: string) {
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
    try {
      const result = await Uploader.startUpload({
        filePath,
        serverUrl: 'S#_PRESIGNED_URL',
        method: 'PUT',
        headers: {
          'Content-Type': 'video/mp4',
        },
        mimeType: 'video/mp4',
      });
      console.log('Video uploaded successfully:', result.id);
    } catch (error) {
      console.error('Error uploading video:', error);
      throw error;
    }
  }
```

## API

<docgen-index>

* [`startUpload(...)`](#startupload)
* [`removeUpload(...)`](#removeupload)
* [`addListener('events', ...)`](#addlistenerevents-)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Capacitor Uploader Plugin for uploading files with background support and progress tracking.

### startUpload(...)

```typescript
startUpload(options: uploadOption) => Promise<{ id: string; }>
```

Start uploading a file to a server.

The upload will continue in the background even if the app is closed or backgrounded.
Listen to upload events to track progress, completion, or failure.

| Param         | Type                                                  | Description                    |
| ------------- | ----------------------------------------------------- | ------------------------------ |
| **`options`** | <code><a href="#uploadoption">uploadOption</a></code> | - Configuration for the upload |

**Returns:** <code>Promise&lt;{ id: string; }&gt;</code>

**Since:** 0.0.1

--------------------


### removeUpload(...)

```typescript
removeUpload(options: { id: string; }) => Promise<void>
```

Cancel and remove an ongoing upload.

This will stop the upload if it's in progress and clean up resources.

| Param         | Type                         | Description                                 |
| ------------- | ---------------------------- | ------------------------------------------- |
| **`options`** | <code>{ id: string; }</code> | - Object containing the upload ID to remove |

**Since:** 0.0.1

--------------------


### addListener('events', ...)

```typescript
addListener(eventName: 'events', listenerFunc: (state: UploadEvent) => void) => Promise<PluginListenerHandle>
```

Listen for upload progress and status events.

Events are fired for:
- Upload progress updates (with percent)
- Upload completion (with statusCode)
- Upload failure (with error and statusCode)

| Param              | Type                                                                    | Description                                 |
| ------------------ | ----------------------------------------------------------------------- | ------------------------------------------- |
| **`eventName`**    | <code>'events'</code>                                                   | - Must be 'events'                          |
| **`listenerFunc`** | <code>(state: <a href="#uploadevent">UploadEvent</a>) =&gt; void</code> | - Callback function to handle upload events |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 0.0.1

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native Capacitor plugin version.

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

**Since:** 0.0.1

--------------------


### Interfaces


#### uploadOption

Configuration options for uploading a file.

| Prop                    | Type                                    | Description                                                                                                                                               | Default                  | Since |
| ----------------------- | --------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ | ----- |
| **`filePath`**          | <code>string</code>                     | The local file path of the file to upload. Can be a file:// URL or an absolute path.                                                                      |                          | 0.0.1 |
| **`serverUrl`**         | <code>string</code>                     | The server URL endpoint where the file should be uploaded.                                                                                                |                          | 0.0.1 |
| **`notificationTitle`** | <code>string</code>                     | The title of the upload notification shown to the user. Android only.                                                                                     | <code>'Uploading'</code> | 0.0.1 |
| **`headers`**           | <code>{ [key: string]: string; }</code> | HTTP headers to send with the upload request. Useful for authentication tokens, content types, etc.                                                       |                          | 0.0.1 |
| **`method`**            | <code>'PUT' \| 'POST'</code>            | The HTTP method to use for the upload request.                                                                                                            | <code>'POST'</code>      | 0.0.1 |
| **`mimeType`**          | <code>string</code>                     | The MIME type of the file being uploaded. If not specified, the plugin will attempt to determine it automatically.                                        |                          | 0.0.1 |
| **`parameters`**        | <code>{ [key: string]: string; }</code> | Additional form parameters to send with the upload request. These will be included as form data in multipart uploads.                                     |                          | 0.0.1 |
| **`maxRetries`**        | <code>number</code>                     | The maximum number of times to retry the upload if it fails.                                                                                              | <code>0</code>           | 0.0.1 |
| **`uploadType`**        | <code>'binary' \| 'multipart'</code>    | The type of upload to perform. - 'binary': Uploads the file as raw binary data in the request body - 'multipart': Uploads the file as multipart/form-data | <code>'binary'</code>    | 0.0.2 |
| **`fileField`**         | <code>string</code>                     | The form field name for the file when using multipart upload type. Only used when uploadType is 'multipart'.                                              | <code>'file'</code>      | 0.0.2 |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### UploadEvent

Event emitted during the upload lifecycle.

| Prop          | Type                                                                    | Description                                                                                                                                                | Since |
| ------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| **`name`**    | <code>'uploading' \| 'completed' \| 'failed'</code>                     | The current status of the upload. - 'uploading': Upload is in progress - 'completed': Upload finished successfully - 'failed': Upload encountered an error | 0.0.1 |
| **`payload`** | <code>{ percent?: number; error?: string; statusCode?: number; }</code> | Additional data about the upload event.                                                                                                                    | 0.0.1 |
| **`id`**      | <code>string</code>                                                     | Unique identifier for this upload task.                                                                                                                    | 0.0.1 |

</docgen-api>

### Credits:

For the inspiration and the code on ios: https://github.com/Vydia/react-native-background-upload/tree/master
For the API definition: https://www.npmjs.com/package/cordova-plugin-background-upload-put-s3

