package com.facebook.unity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.Session;
import com.unity3d.player.UnityPlayerActivity;

public class FBUnityPlayerActivity extends UnityPlayerActivity {
	static final String TAG = "FBUnityPlayerActivity";
	protected void onCreate(Bundle savedInstanceState) {

		// call UnityPlayerActivity.onCreate()
		super.onCreate(savedInstanceState);

		// print debug message to logcat
		Log.d(TAG, "!! FBUnityPlayerActivity:: onCreate called!");
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	  Log.d(TAG,"onActivityResult: requestCode: "+requestCode+", resultCode:"+resultCode+" intent data: "+data.toString());
	  super.onActivityResult(requestCode, resultCode, data);
	  Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
}
