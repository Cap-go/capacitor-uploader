package ee.forgr.capacitor.uploader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.webkit.MimeTypeMap;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserver;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;

@CapacitorPlugin(name = "Uploader")
public class UploaderPlugin extends Plugin {

    private final String pluginVersion = "8.1.1";

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
        String serverUrl = call.getString("serverUrl");

        if (filePath == null || filePath.isEmpty()) {
            call.reject("Missing required parameter: filePath");
            return;
        }
        if (serverUrl == null || serverUrl.isEmpty()) {
            call.reject("Missing required parameter: serverUrl");
            return;
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
            String mimeType = call.getString("mimeType", getMimeType(filePath));

            String id = implementation.startUpload(
                filePath,
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
                map.put(key, object.getString(key));
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
