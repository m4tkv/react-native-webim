package ru.vvdev.webim;

import android.app.Activity;
import android.text.TextUtils;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.webkit.MimeTypeMap;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.webimapp.android.sdk.FatalErrorHandler;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.NotFatalErrorHandler;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimError;
import com.webimapp.android.sdk.WebimSession;
import com.webimapp.android.sdk.ProvidedAuthorizationTokenStateListener;

import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentUris;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.BufferedOutputStream;

@SuppressWarnings("unused")
public class WebimModule extends ReactContextBaseJavaModule implements MessageListener, ProvidedAuthorizationTokenStateListener, FatalErrorHandler, NotFatalErrorHandler {
    private static final int FILE_SELECT_CODE = 0;
    private static final String REACT_CLASS = "RNWebim";
    private static ReactApplicationContext reactContext = null;
    public static final String DOCUMENTS_DIR = "documents";

    private Callback fileCbSuccess;
    private Callback fileCbFailure;
    private MessageTracker tracker;
    private WebimSession session;

    private String parsedName;
    private String parsedUri;

    private Callback failureCbEx;

    WebimModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
        // todo: чистить cb
        ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                if (requestCode == FILE_SELECT_CODE) {
                    if (resultCode == Activity.RESULT_OK) {
                        Uri uri = data.getData();
                        parsedUri = getPath(getContext(),uri);
                        Activity _activity = getContext().getCurrentActivity();
                        if (_activity != null && uri != null) {
                            String mime = _activity.getContentResolver().getType(uri);
                            String extension = mime == null
                                    ? null
                                    : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                            String name = extension == null
                                    ? null
                                    : uri.getLastPathSegment();
                            if (fileCbSuccess != null) {
                                WritableMap _data = Arguments.createMap();
                                _data.putString("uri", uri.toString());
                                _data.putString("name", name);
                                _data.putString("mime", mime);
                                _data.putString("extension", extension);
                                fileCbSuccess.invoke(_data);
                            }
                        } else {
                            fileCbFailure.invoke(getSimpleMap("message", "unknown1"));
                        }
                        clearAttachCallbacks();
                        return;
                    }
                    if (resultCode != Activity.RESULT_CANCELED) {
                        if (fileCbFailure != null) {
                            fileCbFailure.invoke(getSimpleMap("message", "canceled"));
                        }
                        clearAttachCallbacks();
                    }
                }
            }
        };
        reactContext.addActivityEventListener(mActivityEventListener);
    }

    private ReactApplicationContext getContext() {
        return reactContext;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public Map<String, Object> getConstants() {
        return new HashMap<>();
    }

    private void init(String accountName, String location, @Nullable String accountJSON, @Nullable String providedAuthorizationToken, @Nullable String appVersion, @Nullable Boolean clearVisitorData, @Nullable Boolean storeHistoryLocally, @Nullable String title, @Nullable String pushToken) {
        Webim.SessionBuilder builder = Webim.newSessionBuilder()
                .setContext(reactContext)
                .setAccountName(accountName)
                .setLocation(location)
                .setErrorHandler(this)
                .setNotFatalErrorHandler(this)
                .setPushSystem(Webim.PushSystem.FCM);
        if (pushToken != null) {
            builder.setPushToken(pushToken);
        }
        if (accountJSON != null) {
            builder.setVisitorFieldsJson(accountJSON);
        }
        if (appVersion != null) {
            builder.setAppVersion(appVersion);
        }
        if (clearVisitorData != null) {
            builder.setClearVisitorData(clearVisitorData);
        }
        if (storeHistoryLocally != null) {
            builder.setStoreHistoryLocally(storeHistoryLocally);
        }
        if (title != null) {
            builder.setTitle(title);
        }
        if (providedAuthorizationToken != null) {
            builder.setProvidedAuthorizationTokenStateListener(this, providedAuthorizationToken);
        }
        session = builder.build();
    }

    @ReactMethod
    public void resumeSession(ReadableMap builderData, final Callback errorCallback, final Callback successCallback) {
        String accountName = builderData.getString("accountName");
        String location = builderData.getString("location");

        // optional
        String accountJSON = builderData.hasKey("accountJSON") ? builderData.getString("accountJSON") : null;
        String providedAuthorizationToken = builderData.hasKey("providedAuthorizationToken") ? builderData.getString("providedAuthorizationToken") : null;
        String appVersion = builderData.hasKey("appVersion") ? builderData.getString("appVersion") : null;
        Boolean clearVisitorData = builderData.hasKey("clearVisitorData") ? builderData.getBoolean("clearVisitorData") : null;
        Boolean storeHistoryLocally = builderData.hasKey("storeHistoryLocally") ? builderData.getBoolean("storeHistoryLocally") : null;
        String title = builderData.hasKey("title") ? builderData.getString("title") : null;
        String pushToken = builderData.hasKey("pushToken") ? builderData.getString("pushToken") : null;

        if (session == null) {
            init(accountName, location, accountJSON, providedAuthorizationToken, appVersion, clearVisitorData, storeHistoryLocally, title, pushToken);
        }

        if (session == null) {
            errorCallback.invoke(getSimpleMap("message", "resume null session"));
        }
        session.resume();
        session.getStream().startChat();
        session.getStream().setChatRead();
        tracker = session.getStream().newMessageTracker(this);
        successCallback.invoke(Arguments.createMap());
    }

    @ReactMethod
    public void destroySession(Boolean clearData, final Callback errorCallback, final Callback successCallback) {
        if (session != null) {
            session.getStream().closeChat();
            tracker.destroy();
            if (clearData) {
             session.destroyWithClearVisitorData();
            } else {
                session.destroy();
            }
            session = null;
        }
        successCallback.invoke(Arguments.createMap());
    }

    private WritableMap messagesToJson(@NonNull List<? extends Message> messages) {
        WritableMap response = Arguments.createMap();
        WritableArray jsonMessages = Arguments.createArray();
        for (Message message : messages) {
            jsonMessages.pushMap(messageToJson(message));
        }
        response.putArray("messages", jsonMessages);
        return response;
    }

    private MessageTracker.GetMessagesCallback getMessagesCallback(final Callback successCallback) {
        return new MessageTracker.GetMessagesCallback() {
            @Override
            public void receive(@NonNull List<? extends Message> messages) {
                WritableMap response = messagesToJson(messages);
                successCallback.invoke(response);
            }
        };
    }

    @ReactMethod
    public void getLastMessages(int limit, final Callback errorCallback, final Callback successCallback) {
        tracker.getLastMessages(limit, getMessagesCallback(successCallback));
    }

    @ReactMethod
    public void getNextMessages(int limit, final Callback errorCallback, final Callback successCallback) {
        tracker.getNextMessages(limit, getMessagesCallback(successCallback));
    }

    @ReactMethod
    public void getAllMessages(final Callback errorCallback, final Callback successCallback) {
        tracker.getAllMessages(getMessagesCallback(successCallback));
    }

    @ReactMethod
    public void rateOperator(int rate, final Callback errorCallback, final Callback successCallback) {
        Operator operator = session.getStream().getCurrentOperator();
        if (operator != null) {
            session.getStream().rateOperator(operator.getId(), rate, new MessageStream.RateOperatorCallback() {
                @Override
                public void onSuccess() {
                    successCallback.invoke(Arguments.createMap());
                }

                @Override
                public void onFailure(@NonNull WebimError<RateOperatorError> rateOperatorError) {
                    errorCallback.invoke(getSimpleMap("message", rateOperatorError.getErrorString()));
                }
            });
        } else {
            errorCallback.invoke(getSimpleMap("message", "no operator"));
        }
    }

    @ReactMethod
    public void tryAttachFile(Callback failureCb, Callback successCb) {
        fileCbFailure = failureCb;
        fileCbSuccess = successCb;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Activity activity = reactContext.getCurrentActivity();
        if (activity != null) {
            activity.startActivityForResult(Intent.createChooser(intent, "Выбор файла"), FILE_SELECT_CODE);
        } else {
            failureCb.invoke("pick error");
            fileCbFailure = null;
            fileCbSuccess = null;
        }
    }

    @ReactMethod
    public void sendFile(String uri, String name, String mime, String extension, final Callback failureCb, final Callback successCb) {
        File file = null;
        try {
            Activity activity = getContext().getCurrentActivity();
            if (activity == null) {
                failureCb.invoke("");
                return;
            }
            InputStream inp = activity.getContentResolver().openInputStream(Uri.parse(uri));
            if (inp != null) {
                file = File.createTempFile("webim", extension, activity.getCacheDir());
                writeFully(file, inp);
            }
        } catch (IOException e) {
            if (file != null) {
                file.delete();
            }
            failureCb.invoke(getSimpleMap("message", "unknown2"));
            return;
        }
        if (file != null && name != null) {
            final File fileToUpload = file;
            if (parsedUri != null){
                String [] parsedParts = parsedUri.split("/");
                parsedName = parsedParts[parsedParts.length - 1];
            }else if (name != null){
            String [] parsedParts = name.split("/");
             parsedName = parsedParts[parsedParts.length - 1] + "." + extension;
            }else{
             parsedName = "unknown.txt";
            }
            session.getStream().sendFile(fileToUpload, parsedName, mime, new MessageStream.SendFileCallback() {
                @Override
                public void onProgress(@NonNull Message.Id id, long sentBytes) {
                }

                @Override
                public void onSuccess(@NonNull Message.Id id) {
                    fileToUpload.delete();
                    successCb.invoke(getSimpleMap("id", id.toString()));
                }

                @Override
                public void onFailure(@NonNull Message.Id id,
                                      @NonNull WebimError<SendFileError> error) {
                    fileToUpload.delete();
                    String msg;
                    switch (error.getErrorType()) {
                        case FILE_TYPE_NOT_ALLOWED:
                            msg = "type not allowed";
                            break;
                        case FILE_SIZE_EXCEEDED:
                            msg = "file size exceeded";
                            break;
                        case FILE_NAME_INCORRECT:
                            msg = "FILE_NAME_INCORRECT";
                            break;
                        case UNAUTHORIZED:
                            msg = "UNAUTHORIZED";
                            break;
                        case UPLOADED_FILE_NOT_FOUND:
                            msg = "UPLOADED_FILE_NOT_FOUND";
                            break;
                        default:
                            msg = "unknown3";
                    }

                    failureCb.invoke(getSimpleMap("message", msg));
                }
            });
        } else {
            failureCb.invoke(getSimpleMap("message", "no file"));
        }
    }

    @ReactMethod
    public void send(String message, final Callback failureCb, final Callback successCb) {
        session.getStream().sendMessage(message);
        successCb.invoke();
    }

    @Override
    public void messageAdded(@Nullable Message before, @NonNull Message message) {
        final WritableMap msg = Arguments.createMap();
        msg.putMap("msg", messageToJson(message));
        emitDeviceEvent("newMessage", msg);
    }

    @Override
    public void messageChanged(@NonNull Message from, @NonNull Message to) {
        final WritableMap map = Arguments.createMap();
        map.putMap("to", messageToJson(to));
        map.putMap("from", messageToJson(from));
        emitDeviceEvent("changedMessage", map);
    }

    @Override
    public void allMessagesRemoved() {
        final WritableMap map = Arguments.createMap();
        emitDeviceEvent("allMessagesRemoved", map);
    }

    @Override
    public void messageRemoved(@NonNull Message message) {
        final WritableMap msg = Arguments.createMap();
        msg.putMap("msg", messageToJson(message));
        emitDeviceEvent("removeMessage", msg);
    }

    private static void emitDeviceEvent(String eventName, @Nullable WritableMap eventData) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, eventData);
    }

    private WritableMap messageToJson(Message msg) {
        final WritableMap map = Arguments.createMap();
        map.putString("id", msg.getId().toString());
        map.putDouble("time", msg.getTime());
        map.putString("type", msg.getType().toString());
        map.putString("text", msg.getText());
        map.putString("name", msg.getSenderName());
        map.putString("status", msg.getSendStatus().toString());
        map.putString("avatar", msg.getSenderAvatarUrl());
        map.putBoolean("read", msg.isReadByOperator());
        map.putBoolean("canEdit", msg.canBeEdited());

        Message.Attachment attach1 = msg.getAttachment();

        if (attach1 != null) {
            Message.FileInfo attach = attach1.getFileInfo();
            if (attach != null){
                 WritableMap _att = Arguments.createMap();
                             _att.putString("contentType", attach.getContentType());
                             _att.putString("name", attach.getFileName());
                             _att.putString("info", "attach.getImageInfo().toString()");
                             _att.putDouble("size", attach.getSize());
                             _att.putString("url", attach.getUrl());
                             Message.ImageInfo attachImage = attach.getImageInfo();
                             if (attachImage != null){
                                _att.putString("url", attachImage.getThumbUrl());
                             }else{
                                _att.putString("url", attach.getUrl());
                             }
                             map.putMap("attachment", _att);
            }
         }

        return map;
    }

    private static void writeFully(@NonNull File to, @NonNull InputStream from) throws IOException {
        byte[] buffer = new byte[4096];
        OutputStream out = null;
        try {
            out = new FileOutputStream(to);
            for (int read; (read = from.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
        } finally {
            from.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private void clearAttachCallbacks() {
        fileCbFailure = null;
        fileCbSuccess = null;
    }

    private WritableMap getSimpleMap(String key, String value) {
        WritableMap map = Arguments.createMap();
        map.putString(key, value);
        return map;
    }

    @Override
    public void updateProvidedAuthorizationToken(@NonNull String providedAuthorizationToken) {
        emitDeviceEvent("tokenUpdated", getSimpleMap("token", providedAuthorizationToken));
    }

    @Override
    public void onError(@NonNull WebimError<FatalErrorType> error) {
        emitDeviceEvent("error", getSimpleMap("message", error.getErrorString()));
    }

    @Override
    public void onNotFatalError(@NonNull WebimError<NotFatalErrorType> error) {
        emitDeviceEvent("error", getSimpleMap("message", error.getErrorString()));
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public String getPath(final ReactApplicationContext context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                        final String id = DocumentsContract.getDocumentId(uri);
                        if (!TextUtils.isEmpty(id)) {
                        if (id.startsWith("raw:")) {
                            return id.replaceFirst("raw:", "");
                        }

                         String[] contentUriPrefixesToTry = new String[]{
                                                "content://downloads/public_downloads",
                                                "content://downloads/my_downloads",
                                                "content://downloads/all_downloads"
                                        };

                         for (String contentUriPrefix : contentUriPrefixesToTry) {
                             try {
                                 final Uri contentUri = ContentUris.withAppendedId(
                                            Uri.parse(contentUriPrefix), Long.valueOf(id));
                                 String path = getDataColumn(context, contentUri, null, null);
                                 if (path != null) {
                                    return path;
                                 }
                             } catch (NumberFormatException e) {

                             }
                         }

                         // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                         String fileName = getFileName(context,uri);
                         File cacheDir = getDocumentCacheDir(context);
                         File file = generateFileName(fileName, cacheDir);
                         String destinationPath = null;
                         if (file != null) {
                            destinationPath = file.getAbsolutePath();
                            saveFileFromUri(context, uri, destinationPath);
                         }

                         return destinationPath;


                        }
                    }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
         * Get the value of the data column for this Uri. This is useful for
         * MediaStore Uris, and other file-based ContentProviders.
         *
         * @param context       The context.
         * @param uri           The Uri to query.
         * @param selection     (Optional) Filter used in the query.
         * @param selectionArgs (Optional) Selection arguments used in the query.
         * @return The value of the _data column, which is typically a file path.
         */
        public static String getDataColumn(ReactApplicationContext context, Uri uri, String selection,
                                           String[] selectionArgs) {

            Cursor cursor = null;
            final String column = MediaStore.Files.FileColumns.DATA;
            final String[] projection = {
                    column
            };

            try {
                cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                        null);
                if (cursor != null && cursor.moveToFirst()) {

                    final int column_index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(column_index);
                }
            }  catch (Exception e) {

            } finally {
                if (cursor != null)
                    cursor.close();
            }
            return null;
        }



    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

     public String getFileName(@NonNull ReactApplicationContext context, Uri uri) {
            String mimeType = context.getContentResolver().getType(uri);
            String filename = null;

            if (mimeType == null && context != null) {
                String path = getPath1(context, uri);
                if (path == null) {
                    filename = getName1(uri.toString());
                } else {
                    File file = new File(path);
                    filename = file.getName();
                }
            } else {
                Cursor returnCursor = context.getContentResolver().query(uri, null,
                        null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    returnCursor.moveToFirst();
                    filename = returnCursor.getString(nameIndex);
                    returnCursor.close();
                }
            }

            return filename;
        }

        public String getPath1(final ReactApplicationContext context, final Uri uri) {
                String absolutePath = getPath(context, uri);
                return absolutePath != null ? absolutePath : uri.toString();
            }

    public File getDocumentCacheDir(@NonNull ReactApplicationContext context) {
            File dir = new File(context.getCacheDir(), DOCUMENTS_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            return dir;
        }

    @Nullable
    public File generateFileName(@Nullable String name, File directory) {
            if (name == null) {
                return null;
            }

            File file = new File(directory, name);

            if (file.exists()) {
                String fileName = name;
                String extension = "";
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileName = name.substring(0, dotIndex);
                    extension = name.substring(dotIndex);
                }

                int index = 0;

                while (file.exists()) {
                    index++;
                    name = fileName + '(' + index + ')' + extension;
                    file = new File(directory, name);
                }
            }

            try {
                if (!file.createNewFile()) {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }


            return file;
    }

    private void saveFileFromUri(ReactApplicationContext context, Uri uri, String destinationPath) {
            InputStream is = null;
            BufferedOutputStream bos = null;
            try {
                is = context.getContentResolver().openInputStream(uri);
                bos = new BufferedOutputStream(new FileOutputStream(destinationPath, false));
                byte[] buf = new byte[1024];
                is.read(buf);
                do {
                    bos.write(buf);
                } while (is.read(buf) != -1);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null) is.close();
                    if (bos != null) bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    }

    public String getName1(String filename) {
            if (filename == null) {
                return null;
            }
            int index = filename.lastIndexOf('/');
            return filename.substring(index + 1);
        }
}
