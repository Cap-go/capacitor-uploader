package ee.forgr.capacitor.uploader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;
import org.json.JSONObject;

@CapacitorPlugin(name = "Uploader")
public class UploaderPlugin extends Plugin {

    private static final String CAPACITOR_FILE_PATH_PREFIX = "/_capacitor_file_";

    private static final String CAPACITOR_CONTENT_PATH_PREFIX = "/_capacitor_content_";

    private final String pluginVersion = "8.2.8";

    private Uploader implementation;

    private static final String CHANNEL_ID = "ee.forgr.capacitor.uploader.notification_channel_id";
    private static final String CHANNEL_NAME = "Uploader Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications for file uploads";

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(CHANNEL_DESCRIPTION);

            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void load() {
        createNotificationChannel();

        // Create a request observer for all uploads
        RequestObserver observer = new RequestObserver(
            getContext().getApplicationContext(),
            getActivity(),
            new RequestObserverDelegate() {
                @Override
                public void onProgress(Context context, UploadInfo uploadInfo) {
                    JSObject event = new JSObject();
                    event.put("name", "uploading");
                    JSObject payload = new JSObject();
                    payload.put("percent", uploadInfo.getProgressPercent());
                    event.put("payload", payload);
                    event.put("id", uploadInfo.getUploadId());
                    notifyListeners("events", event);
                }

                @Override
                public void onSuccess(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
                    JSObject event = new JSObject();
                    event.put("name", "completed");
                    JSObject payload = new JSObject();
                    payload.put("statusCode", serverResponse.getCode());
                    event.put("payload", payload);
                    event.put("id", uploadInfo.getUploadId());
                    notifyListeners("events", event);
                }

                @Override
                public void onError(Context context, UploadInfo uploadInfo, Throwable exception) {
                    JSObject event = new JSObject();
                    event.put("name", "failed");
                    JSObject payload = new JSObject();
                    payload.put("error", exception.getMessage());
                    event.put("payload", payload);
                    event.put("id", uploadInfo.getUploadId());
                    notifyListeners("events", event);
                }

                @Override
                public void onCompleted(Context context, UploadInfo uploadInfo) {
                    JSObject event = new JSObject();
                    event.put("name", "finished");
                    event.put("id", uploadInfo.getUploadId());
                    notifyListeners("events", event);
                }

                @Override
                public void onCompletedWhileNotObserving() {
                    // Handle completion while not observing if needed
                }
            }
        );

        implementation = new Uploader(getContext().getApplicationContext());
    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    @PluginMethod
    public void startUpload(PluginCall call) {
        String filePath = call.getString("filePath");
        JSArray filesArray = call.getArray("files");
        String serverUrl = call.getString("serverUrl");

        if (serverUrl == null || serverUrl.isEmpty()) {
            call.reject("Missing required parameter: serverUrl");
            return;
        }

        JSObject headersObj = call.getObject("headers", new JSObject());
        JSObject parametersObj = call.getObject("parameters", new JSObject());
        String httpMethod = call.getString("method", "POST");
        String notificationTitle = call.getString("notificationTitle", "File Upload");
        int maxRetries = call.getInt("maxRetries", 2);
        String uploadType = call.getString("uploadType");
        if (uploadType == null || uploadType.isEmpty()) {
            uploadType = "PUT".equalsIgnoreCase(httpMethod) ? "binary" : "multipart";
        }
        String fileField = call.getString("fileField", "file");

        Map<String, String> headers = JSObjectToMap(headersObj);
        Map<String, String> parameters = JSObjectToMap(parametersObj);

        try {
            ArrayList<Uploader.UploadFile> filesToUpload = new ArrayList<>();

            if (filesArray != null && filesArray.length() > 0) {
                for (int i = 0; i < filesArray.length(); i++) {
                    JSONObject fileObj = filesArray.getJSONObject(i);
                    String rawPath = fileObj.optString("filePath", null);
                    if (rawPath == null || rawPath.isEmpty()) {
                        call.reject("Missing required parameter: files[" + i + "].filePath");
                        return;
                    }

                    // Convert Capacitor web-accessible URLs to paths native code can open.
                    // Capacitor 8+ removed Bridge.getLocalUrl(String); mirror AndroidProtocolHandler logic.
                    String localPath = resolveCapacitorPath(rawPath);
                    String fieldName = fileObj.optString("fieldName", fileField);

                    String mimeType = null;
                    if (fileObj.has("mimeType")) {
                        mimeType = fileObj.optString("mimeType", null);
                    } else {
                        mimeType = call.getString("mimeType", null);
                    }
                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = getMimeType(localPath);
                    }

                    filesToUpload.add(new Uploader.UploadFile(localPath, fieldName, mimeType));
                }
            } else {
                if (filePath == null || filePath.isEmpty()) {
                    call.reject("Missing required parameter: filePath or files");
                    return;
                }
                String localFilePath = resolveCapacitorPath(filePath);
                String mimeType = call.getString("mimeType", getMimeType(localFilePath));
                filesToUpload.add(new Uploader.UploadFile(localFilePath, fileField, mimeType));
            }

            String id = implementation.startUpload(
                filesToUpload,
                serverUrl,
                headers,
                parameters,
                httpMethod,
                notificationTitle,
                maxRetries,
                uploadType
            );
            JSObject result = new JSObject();
            result.put("id", id);
            call.resolve(result);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void removeUpload(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.isEmpty()) {
            call.reject("Missing required parameter: id");
            return;
        }
        try {
            implementation.removeUpload(id);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /**
     * Maps WebView URLs (e.g. http(s)://localhost/_capacitor_file_/...) to filesystem or content
     * paths, matching {@link com.getcapacitor.AndroidProtocolHandler}. Plain absolute paths and
     * unrecognized URLs are returned unchanged.
     */
    private static String resolveCapacitorPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }
        Uri uri = Uri.parse(filePath);
        String path = uri.getPath();
        if (path != null) {
            if (path.startsWith(CAPACITOR_FILE_PATH_PREFIX)) {
                return path.substring(CAPACITOR_FILE_PATH_PREFIX.length());
            }
            if (path.startsWith(CAPACITOR_CONTENT_PATH_PREFIX)) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme != null && host != null) {
                    String baseUrl = scheme + "://" + host;
                    if (uri.getPort() != -1) {
                        baseUrl += ":" + uri.getPort();
                    }
                    return filePath.replace(baseUrl + CAPACITOR_CONTENT_PATH_PREFIX, "content:/");
                }
                return filePath.replace(CAPACITOR_CONTENT_PATH_PREFIX, "content:/");
            }
        }
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return uri.getPath();
        }
        return filePath;
    }

    private Map<String, String> JSObjectToMap(JSObject object) {
        Map<String, String> map = new HashMap<>();
        if (object != null) {
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = object.getString(key);
                // Only add non-null and non-empty values to prevent upload service errors
                if (value != null && !value.isEmpty()) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }
}
