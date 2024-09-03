# @capgo/capacitor-uploader

Upload file natively

WIP: this is a work in progress still not ready for use

## Install

```bash
npm install @capgo/capacitor-uploader
npx cap sync
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
