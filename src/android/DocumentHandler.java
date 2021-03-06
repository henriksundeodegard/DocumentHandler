package ch.ti8m.phonegap.plugins;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;

import android.support.v4.content.FileProvider;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.List;


public class DocumentHandler extends CordovaPlugin {

	public static final String HANDLE_DOCUMENT_ACTION = "HandleDocumentWithURL";
	public static final int ERROR_NO_HANDLER_FOR_DATA_TYPE = 53;
	public static final int ERROR_FILE_NOT_FOUND = 2;
	public static final int ERROR_UNKNOWN_ERROR = 1;

	@Override
	public boolean execute(String action, JSONArray args,
												 final CallbackContext callbackContext) throws JSONException {
		if (HANDLE_DOCUMENT_ACTION.equals(action)) {

			// parse arguments
			final JSONObject arg_object = args.getJSONObject(0);
			final String url = arg_object.getString("url");
			System.out.println("Found: " + url);

			// start async download task
			new FileDownloaderAsyncTask(callbackContext, url).execute();

			return true;
		}
		return false;
	}

	// used for all downloaded files, so we can find and delete them again.
	private final static String FILE_PREFIX = "DH_";

	/**
	 * downloads a file from the given url to external storage.
	 *
	 * @param url
	 * @return
	 */
	private File downloadFile(String url, CallbackContext callbackContext) {

		try {
			// get an instance of a cookie manager since it has access to our
			// auth cookie
			CookieManager cookieManager = CookieManager.getInstance();

			// get the cookie string for the site.
			String auth = null;
			if (cookieManager.getCookie(url) != null) {
				auth = cookieManager.getCookie(url).toString();
			}

			URL url2 = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) url2.openConnection();
			if (auth != null) {
				conn.setRequestProperty("Cookie", auth);
			}

			Context context = cordova.getActivity().getApplicationContext();
			
			InputStream reader = conn.getInputStream();

			String extension = MimeTypeMap.getFileExtensionFromUrl(url);
			File f = File.createTempFile(FILE_PREFIX, "." + extension,
					context.getExternalFilesDir(null));
			
			// make sure the receiving app can read this file
			f.setReadable(true, false);
			FileOutputStream outStream = new FileOutputStream(f);

			byte[] buffer = new byte[1024];
			int readBytes = reader.read(buffer);
			while (readBytes > 0) {
				outStream.write(buffer, 0, readBytes);
				readBytes = reader.read(buffer);
			}
			reader.close();
			outStream.close();
			
			return f;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			callbackContext.error(ERROR_FILE_NOT_FOUND);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			callbackContext.error(ERROR_UNKNOWN_ERROR);
			return null;
		}
	}

	/**
	 * Returns the MIME Type of the file by looking at file name extension in
	 * the URL.
	 *
	 * @param url
	 * @return
	 */
	private static String getMimeType(String url) {
		String mimeType = null;

		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			MimeTypeMap mime = MimeTypeMap.getSingleton();
			mimeType = mime.getMimeTypeFromExtension(extension);
		}

		System.out.println("Mime Type: " + mimeType);

		return mimeType;
	}

	private class FileDownloaderAsyncTask extends AsyncTask<Void, Void, File> {

		private final CallbackContext callbackContext;
		private final String url;

		public FileDownloaderAsyncTask(CallbackContext callbackContext,
																	 String url) {
			super();
			this.callbackContext = callbackContext;
			this.url = url;
		}

		@Override
		protected File doInBackground(Void... arg0) {
			return downloadFile(url, callbackContext);
		}

		@Override
		protected void onPostExecute(File result) {
			if (result == null) {
				// case has already been handled
				return;
			}

			Context context = cordova.getActivity().getApplicationContext();

			// get mime type of file data
			String mimeType = getMimeType(url);
			if (mimeType == null) {
				callbackContext.error(ERROR_UNKNOWN_ERROR);
				return;
			}

			// start an intent with the file
			try {
				Uri uri = FileProvider.getUriForFile(context, "com.unipluss.unie24.provider", result);
				
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(uri, mimeType);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				
				List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
				for (ResolveInfo resolveInfo : resInfoList) {
				    String packageName = resolveInfo.activityInfo.packageName;
				    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
				}
				
				context.startActivity(intent);

				callbackContext.success(); // Thread-safe.
			} catch (ActivityNotFoundException e) {
				// happens when we start intent without something that can
				// handle it
				e.printStackTrace();
				callbackContext.error(ERROR_NO_HANDLER_FOR_DATA_TYPE);
			}
		}
	}
}