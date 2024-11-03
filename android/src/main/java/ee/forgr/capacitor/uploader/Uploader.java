package ee.forgr.capacitor.uploader;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.util.Map;
import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.data.UploadNotificationStatusConfig;
import net.gotev.uploadservice.protocols.binary.BinaryUploadRequest;

public class Uploader {

  private final Context context;

  public Uploader(Context context) {
    this.context = context;
    initializeUploadService(context);
  }

  private void initializeUploadService(Context context) {
    Application application = getApplication(context);
    if (application != null) {
      UploadServiceConfig.initialize(
        application,
        "ee.forgr.capacitor.uploader.notification_channel_id",
        true
      );
    } else {
      throw new IllegalStateException("Unable to get Application instance");
    }
  }

  private Application getApplication(Context context) {
    if (context == null) {
      return null;
    } else if (context instanceof Application) {
      return (Application) context;
    } else {
      return getApplication(context.getApplicationContext());
    }
  }

  public String startUpload(
    String filePath,
    String serverUrl,
    Map<String, String> headers,
    Map<String, String> parameters,
    String httpMethod,
    String notificationTitle,
    int maxRetries,
    String mimeType
  ) throws Exception {
    UploadNotificationConfig notificationConfig = createNotificationConfig(
      notificationTitle
    );

    BinaryUploadRequest request = new BinaryUploadRequest(context, serverUrl)
      .setMethod(httpMethod)
      .setFileToUpload(filePath)
      .setNotificationConfig((ctx, uploadId) -> notificationConfig)
      .setMaxRetries(maxRetries);

    // Set the Content-Type header for the file
    request.addHeader("Content-Type", mimeType);

    // Add headers
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    // Add parameters
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      request.addParameter(entry.getKey(), entry.getValue());
    }

    // Set file name if it's a content URI
    if (filePath.startsWith("content://")) {
      Uri uri = Uri.parse(filePath);
      String fileName = getFileNameFromUri(uri);
      if (fileName != null) {
        request.addParameter("filename", fileName);
      }
    }

    // Start the upload
    return request.startUpload();
  }

  public void removeUpload(String uploadId) {
    net.gotev.uploadservice.UploadService.stopUpload(uploadId);
  }

  private UploadNotificationConfig createNotificationConfig(
    String notificationTitle
  ) {
    UploadNotificationStatusConfig progress =
      new UploadNotificationStatusConfig(
        notificationTitle,
        notificationTitle + " - In Progress"
      );
    UploadNotificationStatusConfig success = new UploadNotificationStatusConfig(
      notificationTitle,
      notificationTitle + " - Completed"
    );
    UploadNotificationStatusConfig error = new UploadNotificationStatusConfig(
      notificationTitle,
      notificationTitle + " - Error"
    );
    UploadNotificationStatusConfig cancelled =
      new UploadNotificationStatusConfig(
        notificationTitle,
        notificationTitle + " - Cancelled"
      );

    return new UploadNotificationConfig(
      "ee.forgr.capacitor.uploader.notification_channel_id",
      false,
      progress,
      success,
      error,
      cancelled
    );
  }

  private String getFileNameFromUri(Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      try (
        Cursor cursor = context
          .getContentResolver()
          .query(uri, null, null, null, null)
      ) {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (index != -1) {
            result = cursor.getString(index);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) {
        result = result.substring(cut + 1);
      }
    }
    return result;
  }
}
