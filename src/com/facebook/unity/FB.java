package com.facebook.unity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.facebook.*;
import com.facebook.model.*;
import com.facebook.widget.WebDialog;
import com.facebook.widget.WebDialog.OnCompleteListener;
import com.unity3d.player.UnityPlayer;

public class FB {
	private static boolean DEBUG_UI = true;
	private static final String TAG = "FBUnitySDK";
	// i.e. the game object that receives this message
	private static final String FB_UNITY_OBJECT = "Main Camera";
	private static Session session;
	
	// if we have a session it has been opened.
	private static void setSession(Session session) {
		FB.session = session;
	}
	
	public static class UnityMessage {
		private String methodName;
		private Map<String, Serializable> params = new HashMap<String, Serializable>();

		public UnityMessage(String methodName) {
			this.methodName = methodName;
		}
		
		public UnityMessage put(String name, Serializable value) {
			params.put(name, value);
			return this;
		}
		
		public UnityMessage putCancelled() {
			put("cancelled", true);
			return this;
		}
		
		public UnityMessage putID(String id) {
			put("id", id);
			return this;
		}
	
		public void sendNotLoggedInError() {
			sendError("not logged in");
		}
		
		public void sendError(String errorMsg) {
			this.put("error", errorMsg);
			send();
		}
		
		public void send() {
			assert methodName != null : "no method specified";
			String message = new JSONObject(this.params).toString();
			Log.d(TAG,"sending to Unity "+this.methodName+"("+message+")");
			UnityPlayer.UnitySendMessage(FB_UNITY_OBJECT, this.methodName, message);
		}
	}
		
	private static boolean isLoggedIn() {
		return Session.getActiveSession() != null && Session.getActiveSession().isOpened();
	}
	
	private static Activity getActivity() {
		return UnityPlayer.currentActivity;
	}
	
	private static void initAndLogin(String params, boolean show_login_dialog) {
		Log.d(TAG,"init called with params: "+params);
		final UnityMessage response = new UnityMessage("OnInitComplete");

		// start Facebook 
		//
		
		// TODO: real application id
		//Session session = new Session.Builder(FB.activity).setApplicationId(162960780530514);
        //setActiveSession(session);
        //session.openForRead(openRequest);

		Session.openActiveSession(getActivity(), true, new Session.StatusCallback() {
			// callback when session changes state
			@Override
			public void call(Session session, SessionState state, Exception exception) {
				Log.d(TAG, "Session state changed:"+state.toString());
				if (session.isOpened()) {
					Log.d(TAG,"Session Opened!!!!!!!");
					FB.setSession(session);
					response.put("opened", true).send();
				}
			}
		});
	}
	
	@UnityCallable
	public static void Init(String params) {
		// tries to log the user in if they've already TOS'd the app
		initAndLogin(params, /*show_login_dialog=*/false);
	}
	
	@UnityCallable
	public static void Login(String params) {
		initAndLogin(params, /*show_login_dialog=*/true);		
	}
	
	@UnityCallable
	public static void Logout(String params) {
		Session.getActiveSession().closeAndClearTokenInformation();
		new UnityMessage("OnLogoutComplete").send();
	}

	@UnityCallable
	public static void AppRequest(String caller_params) {
		Log.d(TAG, "sendRequestDialog("+caller_params+")");
		final Bundle params = new Bundle();
		final UnityMessage response = new UnityMessage("OnOpenRequestsDialogComplete");

		if (!isLoggedIn()) {
			response.sendNotLoggedInError();
			return;
		}
		
		params.putString("message", "Learn how to make your Android apps social");

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				WebDialog requestsDialog = (
						new WebDialog.RequestsDialogBuilder(getActivity(),
								Session.getActiveSession(),
								params))
								.setOnCompleteListener(new OnCompleteListener() {

									@Override
									public void onComplete(Bundle values,
											FacebookException error) {

										Log.d(TAG, "RequestDialogComplete");
										if (error != null) {
											response.sendError("error:"+error.toString());
										} else {
											final String requestID = values.getString("request");
											response.put("requestID", requestID).send();
										}

										// garbage to remove
										if (error != null) {
											if (error instanceof FacebookOperationCanceledException) {
												Toast.makeText(getActivity().getApplicationContext(), 
														"Request cancelled", 
														Toast.LENGTH_SHORT).show();
											} else {
												Toast.makeText(getActivity().getApplicationContext(), 
														"Network Error", 
														Toast.LENGTH_SHORT).show();
											}
										} else {
											final String requestId = values.getString("request");
											if (requestId != null) {
												Toast.makeText(getActivity().getApplicationContext(), 
														"Request sent",  
														Toast.LENGTH_SHORT).show();
											} else {
												Toast.makeText(getActivity().getApplicationContext(), 
														"Request cancelled", 
														Toast.LENGTH_SHORT).show();
											}
										}   
									}

								})
								.build();
				requestsDialog.show();

			}			
		});
	}
	
	@UnityCallable
	public static void FeedRequest(String params_str) {
		Log.d(TAG, "FeedRequest("+params_str+")");
		final UnityMessage response = new UnityMessage("OnFeedRequestComplete");
		final JSONObject unity_params; 
		try {
			unity_params = new JSONObject(params_str);
		} catch (JSONException e) {
			response.sendError("couldn't parse params: "+params_str);
			return;
		}
		
		if (!isLoggedIn()) {
			response.sendNotLoggedInError();
			return;
		}		
		
		getActivity().runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				Bundle params = new Bundle();
				Iterator<?> keys = unity_params.keys();
				while(keys.hasNext()) {
					String key = (String)keys.next();
					try {
						String value = unity_params.getString(key);
						if (value != null) {
							params.putString(key, value);
						}
					} catch (JSONException e) {
						response.sendError("error getting value for key "+key+": "+e.toString());
						return;
					}
				}

				WebDialog feedDialog = (
						new WebDialog.FeedDialogBuilder(getActivity(),
								Session.getActiveSession(),
								params))
								.setOnCompleteListener(new OnCompleteListener() {

									@Override
									public void onComplete(Bundle values,
											FacebookException error) {
										
										// response
										if (error == null) {
											final String postID = values.getString("post_id");
											if (postID != null) {
												response.putID(postID);
											} else {
												response.putCancelled();
											}
											response.send();
										} else if (error instanceof FacebookOperationCanceledException) {
											// User clicked the "x" button
											response.putCancelled();
											response.send();
										} else {
											// Generic, ex: network error
											response.sendError(error.toString());
										}
										
										// fluffy GUI stuff
										if (DEBUG_UI) {
											if (error == null) {
												// When the story is posted, echo the success
												// and the post Id.
												final String postId = values.getString("post_id");
												if (postId != null) {
													Toast.makeText(getActivity(),
															"Posted story, id: "+postId,
															Toast.LENGTH_SHORT).show();
												} else {
													// User clicked the Cancel button
													Toast.makeText(getActivity().getApplicationContext(), 
															"Publish cancelled", 
															Toast.LENGTH_SHORT).show();
												}
											} else if (error instanceof FacebookOperationCanceledException) {
												// User clicked the "x" button
												Toast.makeText(getActivity().getApplicationContext(), 
														"Publish cancelled", 
														Toast.LENGTH_SHORT).show();
											} else {
												// Generic, ex: network error
												Toast.makeText(getActivity().getApplicationContext(), 
														"Error posting story", 
														Toast.LENGTH_SHORT).show();
											}
										}
									}

								})
								.build();
				feedDialog.show();
			}
		});
	}
	
	public static void graphGetUser() {
		// make request to the /me API
		Request.executeMeRequestAsync(FB.session, new Request.GraphUserCallback() {

			// callback after Graph API response with user object
			@Override
			public void onCompleted(GraphUser user, Response response) {
				Log.d(TAG,"got /me info: "+user.getName()+"!!!!!");
				if (user != null) {
					// TODO send to Unity
					Log.d(TAG, "user: "+user.asMap().toString());
				}
			}
		});

	}
}
