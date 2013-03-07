package com.redoceanred.unity.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.redoceanred.unity.android.pickimage.PickImageActivity;
import com.redoceanred.unity.android.pickimage.PickImageNativeActivity;
import com.redoceanred.unity.android.pickimage.R;
import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
import android.util.Log;

public class PickerImagePlugin {

	private static String TAG = PickerImagePlugin.class.getSimpleName();

	/**
	 * Unity層へのCallbackクラス.
	 */
	private PickerImagePluginCallback mCallBack;

	/**
	 * プラグインの内部状態.
	 */
	private enum PickerImageState {
		/**
		 * アイドル.
		 */
		Idle, PickImage, CaptureImage
	}

	/**
	 * プラグインの内部状態.
	 */
	private PickerImageState mPickerImageState = PickerImageState.Idle;

	/**
	 * プラグインの初期化を実行.
	 */
	public void initPlugin(String gameObject) {
		Log.d(TAG, "Called initPlugin " + gameObject);
		mCallBack = new PickerImagePluginCallback(gameObject);

		// ActivityにPluginのインスタンスを登録.
		if (UnityPlayer.currentActivity instanceof PickImageActivity) {
			PickImageActivity activity = (PickImageActivity) UnityPlayer.currentActivity;
			activity.setPickerImagePlugin(PickerImagePlugin.this);
		} else if (UnityPlayer.currentActivity instanceof PickImageNativeActivity) {
			PickImageNativeActivity activity = (PickImageNativeActivity) UnityPlayer.currentActivity;
			activity.setPickerImagePlugin(PickerImagePlugin.this);
		}
	}

	/**
	 * 画像取得を実行する.
	 */
	public boolean pickImage() {
		Log.d(TAG, "Called pickImage");
		if (!mPickerImageState.equals(PickerImageState.Idle)) {
			return false;
		}
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent = Intent.createChooser(intent, UnityPlayer.currentActivity.getString(R.string.pick_chooser));
		UnityPlayer.currentActivity.startActivityForResult(intent, PICK_FROM_FILE);

		mPickerImageState = PickerImageState.PickImage;
		return true;
	}

	/**
	 * 撮影する.
	 */
	public boolean captureImage() {
		Log.d(TAG, "Called captureImage");
		if (!mPickerImageState.equals(PickerImageState.Idle)) {
			return false;
		}
		captureCamera();
		mPickerImageState = PickerImageState.CaptureImage;
		return true;
	}

	public String getImagePath() {
		if (!mPickerImageState.equals(PickerImageState.Idle)) {
			return "";
		}
		return mImageUri.getPath();
	}

	/**
	 * リクエストコード.
	 */
	public static final int PICK_FROM_FILE = 1002;
	public static final int CAPTURE_CAMERA = 1003;

	/**
	 * TMPファイル名.
	 */
	private static final String TMP_CAMERA_FILE_NAME = "tmp_camera.jpg";
	private Uri mImageUri;

	/**
	 * カメラアプリにて撮影を実行する.
	 */
	public void captureCamera() {
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		// SHの特殊機種判定.すべての機種で確認できていない.
		boolean isSh = false;
		String[] shModel = { "A01", "IS03", "SH-03C", "003SH", "DM009SH", "005SH", "IS05", "SH-12C", "006SH", "007SH", "IS11SH", "IS12SH" };
		for (String model : shModel) {
			if (Build.MODEL.contains(model)) {
				isSh = false;
				break;
			}
		}

		if (isSh) {
			// SHの一部機種ではcontent://形式ではカメラ撮影画像を取得できないためExternalCacheDirに一時的に格納する.
			mImageUri = Uri.fromFile(new File(UnityPlayer.currentActivity.getApplication().getExternalCacheDir(), TMP_CAMERA_FILE_NAME));
		} else {
			// getContentResolverを使用して事前にファイルを登録しておく.
			long currentTime = System.currentTimeMillis();
			ContentValues values = new ContentValues();
			values.put(MediaStore.Images.Media.TITLE, String.valueOf(currentTime));
			values.put(MediaStore.Images.Media.DATE_ADDED, currentTime);
			values.put(MediaStore.Images.Media.DATE_TAKEN, currentTime);
			mImageUri = UnityPlayer.currentActivity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
		}

		// 設定にファイルパスを格納しておく.mImageUriが削除されることがあるため.
		SharedPreferences pref = UnityPlayer.currentActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = pref.edit();
		String encodePath = mImageUri.toString();
		editor.putString("camera_file_name", encodePath);
		editor.commit();
		// 画像の出力先はファイル.
		intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, mImageUri);
		intent.putExtra("return-data", true);
		intent = Intent.createChooser(intent, UnityPlayer.currentActivity.getString(R.string.capture_chooser));
		try {
			UnityPlayer.currentActivity.startActivityForResult(intent, CAPTURE_CAMERA);
		} catch (ActivityNotFoundException e) {
			// カメラがない.
			e.printStackTrace();
		}
	}

	/**
	 * 各種アプリの起動結果.
	 */
	public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {

		mPickerImageState = PickerImageState.Idle;

		switch (requestCode) {
		case PICK_FROM_FILE:
			if (resultCode != Activity.RESULT_OK) {
				// キャンセル時は何もしない.
				mCallBack.pickMessage(false);
				return true;
			}
			mImageUri = data.getData();

			changeImageUriScheme();
			mCallBack.pickMessage(true);
			return true;
		case CAPTURE_CAMERA: {
			if (mImageUri == null) {
				SharedPreferences pref = UnityPlayer.currentActivity.getPreferences(Context.MODE_PRIVATE);
				String cameraFile = pref.getString("camera_file_name", "");
				mImageUri = Uri.parse(cameraFile);
			}

			if (resultCode != Activity.RESULT_OK) {
				// キャンセル時は先に登録したファイルの削除.
				if (!(mImageUri.getScheme().startsWith("file"))) {
					UnityPlayer.currentActivity.getContentResolver().delete(mImageUri, null, null);
				}
				mCallBack.captureMessage(false);
				return true;
			}

			String imageString = null;
			if (mImageUri.getScheme().startsWith("file")) {
				// SH特殊機種対応.
				/*
				 * imageString = mImageUri.getPath(); changeImageUriScheme();
				 * 
				 * Intent intent = new
				 * Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
				 * Uri.parse(imageString));
				 * mFragmentActivity.sendBroadcast(intent); try { //
				 * SCANが終わるまで一定時間sleep. Thread.sleep(500); } catch (Exception e)
				 * {}
				 */
				Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mImageUri);
				UnityPlayer.currentActivity.sendBroadcast(intent);

				try { // SCANが終わるまで一定時間sleep.
					Thread.sleep(500);
				} catch (Exception e) {
				}

				// ファイルパス形式のStringを取得.
				imageString = mImageUri.toString().substring("file://".length());

				// content形式のUriを取得する.
				Cursor cursor = UnityPlayer.currentActivity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.ImageColumns.DATA + " = ?",
						new String[] { imageString }, null);
				cursor.moveToFirst();
				String contentname = "content://media/external/images/media/" + cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
				cursor.close();
				mImageUri = Uri.parse(contentname);

			} else {
				// content形式のUriからファイルパスを取得.
				String[] proj = { MediaStore.Images.Media.DATA };

				CursorLoader cloader = new CursorLoader(UnityPlayer.currentActivity.getApplicationContext(), mImageUri, proj, null, null, null);
				Cursor cursor = cloader.loadInBackground();
				int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				cursor.moveToFirst();
				// ファイルパスを取得.
				imageString = cursor.getString(column_index);
				cursor.close();
			}

			// 一部機種にてカメラ画像が横向きとなるため画像データを変換する.
			ExifInterface ei = null;
			try {
				// EXIF情報の取得.
				ei = new ExifInterface(imageString);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			// EXIFからカメラの回転向きを取得.
			int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
			float deg = 0f;
			switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				deg = 90f;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				deg = 270f;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				deg = 180f;
				break;
			case ExifInterface.ORIENTATION_NORMAL:
			default:
				deg = 0f;
				break;
			}

			if (deg != 0f) {
				// NOMAL以外は画像の回転を実行.
				Bitmap photo = null;
				try {
					// BITMAP形式のデータを取得.
					photo = MediaStore.Images.Media.getBitmap(UnityPlayer.currentActivity.getContentResolver(), mImageUri);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (photo == null) {
					mCallBack.captureMessage(false);
					return false;
				}

				// MATRIXを使って画像を回転する.
				Matrix matrix = new Matrix();
				// 回転角度を設定.
				matrix.setRotate(deg);
				// 作成された画像を回転したBitmapを取得.
				Bitmap rotatePhoto = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, false);

				FileOutputStream out = null;
				try {
					// カメラから取得したファイルに上書きする.
					out = new FileOutputStream(imageString);
					rotatePhoto.compress(Bitmap.CompressFormat.JPEG, 100, out);
				} catch (Exception e) {
				} finally {
					try {
						if (out != null) {
							out.close();
						}
					} catch (IOException e) {
					}
				}
				// 使用したBitmapの破棄.
				recycleBitmap(photo);
				recycleBitmap(rotatePhoto);
			}
			mCallBack.captureMessage(true);
			mImageUri = Uri.parse(imageString);
			return true;
		}
		}
		return false;
	}

	private void changeImageUriScheme() {
		if (mImageUri.getScheme().equals("content")) {
			Cursor cursor = UnityPlayer.currentActivity.getContentResolver().query(mImageUri, new String[] { android.provider.MediaStore.Images.ImageColumns.DATA }, null, null, null);
			if (cursor.moveToFirst()) {
				mImageUri = Uri.parse(cursor.getString(0));
			}
			cursor.close();
		}
	}

	private void recycleBitmap(Bitmap bitmap) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			if (!bitmap.isRecycled()) {
				bitmap.recycle();
			}
		}
	}

}
