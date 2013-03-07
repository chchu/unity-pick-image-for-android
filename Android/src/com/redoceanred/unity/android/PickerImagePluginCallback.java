package com.redoceanred.unity.android;

import com.unity3d.player.UnityPlayer;

public class PickerImagePluginCallback {
	
	private String mGameObject;
	
	private final String ACTION_PICK = "pick";
	private final String ACTION_CAPTURE = "capture";

	public PickerImagePluginCallback(String gameObject) {
		mGameObject = gameObject;
	}

	/**
	 * 画像取得の結果を通知する.
	 * @param success true 成功, false 失敗.
	 */
	public void pickMessage(boolean success) {
		sendMessage(ACTION_PICK, success);
	}
	
	/**
	 * 画像撮影の結果を通知する.
	 * @param success true 成功, false 失敗.
	 */
	public void captureMessage(boolean success) {
		sendMessage(ACTION_CAPTURE, success);
	}

	private void sendMessage(String action, boolean success) {
		UnityPlayer.UnitySendMessage(mGameObject, "NativeMessage", action + "," + Boolean.toString(success));
	}
}
