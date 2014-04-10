/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.neamar.notiflow;

import java.util.ArrayList;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
	private NotificationManager mNotificationManager;
	NotificationCompat.Builder builder;

	public GcmIntentService() {
		super("GcmIntentService");
	}

	public static final String TAG = "Notiflow";

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		// The getMessageType() intent parameter must be the intent you received
		// in your BroadcastReceiver.
		String messageType = gcm.getMessageType(intent);

		if (!extras.isEmpty()) { // has effect of unparcelling Bundle
			/*
			 * Filter messages based on message type. Since it is likely that
			 * GCM will be extended in the future with new message types, just
			 * ignore any message types you're not interested in, or that you
			 * don't recognize.
			 */
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
				sendNotification("Notiflow", "Send error: " + extras.toString());
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
				sendNotification("Notiflow", "Deleted messages on server: " + extras.toString());
				// If it's a regular GCM message, do some work.
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
				// Post notification of received message.
				sendNotification(extras.getString("flow"), extras.getString("content"));
				Log.i(TAG, "Received: " + extras.toString());
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	// Put the message into a notification and post it.
	// This is just one simple example of what you might choose to do with
	// a GCM message.
	private void sendNotification(String flow, String msg) {
		Intent flowdockIntent;
		flowdockIntent = this.getPackageManager().getLaunchIntentForPackage("com.flowdock.jorge");

		mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, flowdockIntent, 0);

		Intent intent = new Intent(this, DismissNotification.class);
		intent.setAction("notification_cancelled");
		intent.putExtra("flow", flow);
		PendingIntent dismissIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setSmallIcon(R.drawable.ic_launcher);
		mBuilder.setContentTitle(flow);
		
		
		SharedPreferences prefs = this.getSharedPreferences(DismissNotification.STORAGE_COLLECTION, Context.MODE_PRIVATE);
		String currentNotification = prefs.getString(DismissNotification.PROPERTY_NOTIFICATION, "");
		
		// We have a pending notification. We'll need to update it.
		if(!currentNotification.isEmpty()) {
			NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
			style.addLine(msg);
			
			// Read previous messages
			ArrayList<String> prevMessages = NotificationHelper.getNotifications(flow);
			
			for(int i = 0; i < prevMessages.size(); i++) {
				style.addLine(prevMessages.get(i));
			}
			
			// Overwrite previous messages
			NotificationHelper.addNotification(flow, msg);
			
			mBuilder.setStyle(style);
		}
		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DismissNotification.getPreviousMessageKey(0), msg);
		editor.putString(DismissNotification.PROPERTY_NOTIFICATION, "runnning");
		editor.commit();
		
		
		mBuilder.setContentText(msg);
		mBuilder.setAutoCancel(true);
		mBuilder.setContentIntent(contentIntent);
		mBuilder.setDeleteIntent(dismissIntent);
		mBuilder.setTicker(Html.fromHtml(msg));

		Notification notification = mBuilder.build();
		notification.defaults |= Notification.DEFAULT_VIBRATE;

		mNotificationManager.notify(NotificationHelper.getFlowId(flow), notification);
	}
}
