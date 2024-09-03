package ee.forgr.capacitor.uploader;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.network.ServerResponse;
import net.gotev.uploadservice.observer.request.RequestObserverDelegate;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

@CapacitorPlugin(name = "Uploader")
public class UploaderPlugin extends Plugin {

    private Uploader implementation;

    @Override
    public void load() {
        implementation = new Uploader(getContext(), new RequestObserverDelegate() {
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
                // This method is called after onSuccess or onError
            }

            @Override
            public void onCompletedWhileNotObserving() {
                // This method is called when the upload completes while the observer is not registered
            }
        });
    }

    @PluginMethod
    public void startUpload(PluginCall call) {
        String filePath = call.getString("filePath");
        String serverUrl = call.getString("serverUrl");
        JSObject headersObj = call.getObject("headers");
        Map<String, String> headers = JSObjectToMap(headersObj);
        String notificationTitle = call.getString("notificationTitle", "File Upload");

        try {
            String id = implementation.startUpload(filePath, serverUrl, headers, notificationTitle);
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
}
