package ee.forgr.capacitor.uploader;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Uploader")
public class UploaderPlugin extends Plugin {

    private Uploader implementation = new Uploader();

    @PluginMethod
    public void startUpload(PluginCall call) {
        String filePath = call.getString("filePath");
        String serverUrl = call.getString("serverUrl");
        Map<String, String> headers = call.getMap("headers");
        String notificationTitle = call.getString("notificationTitle");
        try {
            String id = implementation.startUpload(filePath, serverUrl, headers, notificationTitle,);
            call.resolve(id);
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
}
