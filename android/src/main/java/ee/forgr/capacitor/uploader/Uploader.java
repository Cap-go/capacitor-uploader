package ee.forgr.capacitor.uploader;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.data.UploadNotificationStatusConfig;
import net.gotev.uploadservice.protocols.binary.BinaryUploadRequest;

public class Uploader {

    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    private final Context context;
    private final Map<String, File> tempMultipartBodies = new ConcurrentHashMap<>();

    public static class UploadFile {

        public final String filePath;
        public final String fieldName;
        public final String mimeType;

        public UploadFile(String filePath, String fieldName, String mimeType) {
            this.filePath = filePath;
            this.fieldName = fieldName;
            this.mimeType = mimeType;
        }
    }

    public Uploader(Context context) {
        this.context = context;
        initializeUploadService(context);
    }

    private void initializeUploadService(Context context) {
        Application application = getApplication(context);
        if (application != null) {
            UploadServiceConfig.initialize(application, "ee.forgr.capacitor.uploader.notification_channel_id", true);
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
        List<UploadFile> files,
        String serverUrl,
        Map<String, String> headers,
        Map<String, String> parameters,
        String httpMethod,
        String notificationTitle,
        int maxRetries,
        String uploadType
    ) throws Exception {
        UploadNotificationConfig notificationConfig = createNotificationConfig(notificationTitle);

        if ("multipart".equals(uploadType)) {
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("Missing required parameter: files");
            }
            return startMultipartUpload(files, serverUrl, headers, parameters, httpMethod, notificationConfig, maxRetries);
        } else {
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("Missing required parameter: filePath or files");
            }
            if (files.size() != 1) {
                throw new IllegalArgumentException("Binary uploads only support a single file");
            }
            UploadFile file = files.get(0);
            return startBinaryUpload(
                file.filePath,
                serverUrl,
                headers,
                parameters,
                httpMethod,
                notificationConfig,
                maxRetries,
                file.mimeType
            );
        }
    }

    private String startMultipartUpload(
        List<UploadFile> files,
        String serverUrl,
        Map<String, String> headers,
        Map<String, String> parameters,
        String httpMethod,
        UploadNotificationConfig notificationConfig,
        int maxRetries
    ) throws Exception {
        String boundary = UUID.randomUUID().toString();
        File tempBody = writeMultipartBodyToFile(files, parameters, boundary);

        try {
            BinaryUploadRequest request = new BinaryUploadRequest(context, serverUrl)
                .setMethod(httpMethod)
                .setFileToUpload(tempBody.getAbsolutePath())
                .setNotificationConfig((ctx, uploadId) -> notificationConfig)
                .setMaxRetries(maxRetries)
                .setUsesFixedLengthStreamingMode(true);

            request.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    request.addHeader(entry.getKey(), entry.getValue());
                }
            }

            String uploadId = request.startUpload();
            tempMultipartBodies.put(uploadId, tempBody);
            return uploadId;
        } catch (Exception e) {
            if (!tempBody.delete()) {
                tempBody.deleteOnExit();
            }
            throw e;
        }
    }

    private File writeMultipartBodyToFile(List<UploadFile> files, Map<String, String> parameters, String boundary)
        throws IOException {
        File tempFile = File.createTempFile("upload-", ".tmp", context.getCacheDir());

        try (FileOutputStream output = new FileOutputStream(tempFile)) {
            if (parameters != null) {
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    writeUtf8(output, "--" + boundary + "\r\n");
                    writeUtf8(output, "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n");
                    writeUtf8(output, entry.getValue() + "\r\n");
                }
            }

            for (UploadFile file : files) {
                if (file == null || file.filePath == null || file.filePath.isEmpty()) {
                    throw new IllegalArgumentException("Invalid file entry in files");
                }

                String fieldName = (file.fieldName == null || file.fieldName.isEmpty()) ? "file" : file.fieldName;
                String fileName = getFileNameFromUri(Uri.parse(file.filePath));
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "file";
                }
                String mimeType = (file.mimeType == null || file.mimeType.isEmpty())
                    ? "application/octet-stream"
                    : file.mimeType;

                writeUtf8(output, "--" + boundary + "\r\n");
                writeUtf8(
                    output,
                    "Content-Disposition: form-data; name=\"" +
                    fieldName +
                    "\"; filename=\"" +
                    fileName +
                    "\"\r\n"
                );
                writeUtf8(output, "Content-Type: " + mimeType + "\r\n\r\n");
                streamFileToOutput(file.filePath, output);
                writeUtf8(output, "\r\n");
            }

            writeUtf8(output, "--" + boundary + "--\r\n");
        } catch (IOException | RuntimeException e) {
            if (!tempFile.delete()) {
                tempFile.deleteOnExit();
            }
            throw e;
        }

        return tempFile;
    }

    private void writeUtf8(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private void streamFileToOutput(String filePath, OutputStream output) throws IOException {
        try (InputStream input = openInputStream(filePath)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private InputStream openInputStream(String filePath) throws IOException {
        if (filePath.startsWith("content://")) {
            InputStream stream = context.getContentResolver().openInputStream(Uri.parse(filePath));
            if (stream == null) {
                throw new FileNotFoundException("Cannot open content URI: " + filePath);
            }
            return stream;
        }

        if (filePath.startsWith("file://")) {
            String path = Uri.parse(filePath).getPath();
            if (path == null) {
                throw new FileNotFoundException("Invalid file URI: " + filePath);
            }
            return new FileInputStream(path);
        }

        return new FileInputStream(filePath);
    }

    public void clearTempMultipartBody(String uploadId) {
        File tempFile = tempMultipartBodies.remove(uploadId);
        if (tempFile != null && !tempFile.delete()) {
            tempFile.deleteOnExit();
        }
    }

    private String startBinaryUpload(
        String filePath,
        String serverUrl,
        Map<String, String> headers,
        Map<String, String> parameters,
        String httpMethod,
        UploadNotificationConfig notificationConfig,
        int maxRetries,
        String mimeType
    ) throws Exception {
        BinaryUploadRequest request = new BinaryUploadRequest(context, serverUrl)
            .setMethod(httpMethod)
            .setFileToUpload(filePath)
            .setNotificationConfig((ctx, uploadId) -> notificationConfig)
            .setMaxRetries(maxRetries);

        if (mimeType != null && !mimeType.isEmpty()) {
            request.addHeader("Content-Type", mimeType);
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                request.addParameter(entry.getKey(), entry.getValue());
            }
        }

        return request.startUpload();
    }

    public void removeUpload(String uploadId) {
        clearTempMultipartBody(uploadId);
        net.gotev.uploadservice.UploadService.stopUpload(uploadId);
    }

    private UploadNotificationConfig createNotificationConfig(String notificationTitle) {
        UploadNotificationStatusConfig progress = new UploadNotificationStatusConfig(
            notificationTitle,
            notificationTitle + " - In Progress"
        );
        UploadNotificationStatusConfig success = new UploadNotificationStatusConfig(notificationTitle, notificationTitle + " - Completed");
        UploadNotificationStatusConfig error = new UploadNotificationStatusConfig(notificationTitle, notificationTitle + " - Error");
        UploadNotificationStatusConfig cancelled = new UploadNotificationStatusConfig(
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
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
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
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                result = cut != -1 ? path.substring(cut + 1) : path;
            }
        }
        return result;
    }
}
