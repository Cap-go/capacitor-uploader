package ee.forgr.capacitor.uploader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "Uploader")
public class UploaderPlugin extends Plugin {

    private final String pluginVersion = "8.1.11";

    private Uploader implementation;

    private static final String CHANNEL_ID = "ee.forgr.capacitor.uploader.notification_channel_id";
    private static final String CHANNEL_NAME = "Uploader Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications for file uploads";

    private static final String PREFS_NAME = "CapacitorUploaderPrefs";
    private static final String PENDING_EVENTS_KEY = "pending_events";
    private static final String TAG = "UploaderPlugin";

    private void saveEventToPrefs(String eventId, JSObject event) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingJson = prefs.getString(PENDING_EVENTS_KEY, "{}");
        try {
            JSONObject pendingEvents = new JSONObject(existingJson);
            pendingEvents.put(eventId, new JSONObject(event.toString()));
            prefs.edit().putString(PENDING_EVENTS_KEY, pendingEvents.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to persist upload event", e);
        }
    }

    private void removeEventFromPrefs(String eventId) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingJson = prefs.getString(PENDING_EVENTS_KEY, "{}");
        try {
            JSONObject pendingEvents = new JSONObject(existingJson);
            pendingEvents.remove(eventId);
            prefs.edit().putString(PENDING_EVENTS_KEY, pendingEvents.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to remove upload event from prefs", e);
        }
    }

    private void replayPendingEvents() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingJson = prefs.getString(PENDING_EVENTS_KEY, "{}");
        try {
            JSONObject pendingEvents = new JSONObject(existingJson);
            Iterator<String> keys = pendingEvents.keys();
            while (keys.hasNext()) {
                String eventId = keys.next();
                JSONObject eventJson = pendingEvents.getJSONObject(eventId);
                JSObject event = JSObject.fromJSONObject(eventJson);
                notifyListeners("events", event);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to replay pending upload events", e);
        }
    }

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
                    String eventId = UUID.randomUUID().toString();
                    event.put("eventId", eventId);
                    saveEventToPrefs(eventId, event);
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
                    String eventId = UUID.randomUUID().toString();
                    event.put("eventId", eventId);
                    saveEventToPrefs(eventId, event);
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
                    replayPendingEvents();
                }
            }
        );

        implementation = new Uploader(getContext().getApplicationContext());
        replayPendingEvents();
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
        String serverUrl = call.getString("serverUrl");

        if (filePath == null || filePath.isEmpty()) {
            call.reject("Missing required parameter: filePath");
            return;
        }
        if (serverUrl == null || serverUrl.isEmpty()) {
            call.reject("Missing required parameter: serverUrl");
            return;
        }

        // Convert Capacitor web-accessible URLs to local file paths.
        // Capacitor plugins (e.g., video-recorder) may provide file URLs using the web-accessible
        // scheme like "http://localhost/_capacitor_file_/storage/emulated/0/...".
        // We extract the actual file system path by stripping the CAPACITOR_FILE_START prefix.
        // For already-local paths (file:// or absolute paths), we use them as-is.
        String localFilePath = filePath;
        try {
            Uri uri = Uri.parse(filePath);
            String path = uri.getPath();
            if (path != null && path.startsWith(Bridge.CAPACITOR_FILE_START)) {
                localFilePath = path.replace(Bridge.CAPACITOR_FILE_START, "");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse filePath as URI, using original value: " + filePath);
        }

        JSObject headersObj = call.getObject("headers", new JSObject());
        JSObject parametersObj = call.getObject("parameters", new JSObject());
        String httpMethod = call.getString("method", "POST");
        String notificationTitle = call.getString("notificationTitle", "File Upload");
        int maxRetries = call.getInt("maxRetries", 2);
        String uploadType = call.getString("uploadType", "binary");
        String fileField = call.getString("fileField", "file");

        Map<String, String> headers = JSObjectToMap(headersObj);
        Map<String, String> parameters = JSObjectToMap(parametersObj);

        try {
            String mimeType = call.getString("mimeType", getMimeType(localFilePath));

            String id = implementation.startUpload(
                localFilePath,
                serverUrl,
                headers,
                parameters,
                httpMethod,
                notificationTitle,
                maxRetries,
                mimeType,
                uploadType,
                fileField
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
    public void acknowledgeEvent(PluginCall call) {
        String eventId = call.getString("eventId");
        if (eventId == null || eventId.isEmpty()) {
            call.reject("Missing required parameter: eventId");
            return;
        }
        removeEventFromPrefs(eventId);
        call.resolve();
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
