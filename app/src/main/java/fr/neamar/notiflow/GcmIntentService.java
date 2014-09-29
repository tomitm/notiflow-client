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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.util.ArrayList;
import java.util.Date;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
	public static final String TAG = "Notiflow";
	NotificationCompat.Builder builder;
	private NotificationManager mNotificationManager;

	public GcmIntentService() {
		super("GcmIntentService");
	}

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
			 * GCM will be extended in the future with new message types, we
			 * ignore any message types we're not interested in.
			 */
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
				sendNotification("Notiflow", "Send error: " + extras.toString(), extras);
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
				sendNotification("Notiflow", "Deleted messages on server: " + extras.toString(), extras);
				// If it's a regular GCM message, do some work.
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
				// Post notification of received message.
				if (extras.containsKey("special")) {
					// Wrap content in <em> tag
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: <em>" + extras.getString("content") + "</em>", extras);
				} else if (extras.getString("content").startsWith("    ")) {
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: <tt>" + extras.getString("content") + "</tt>", extras);
				} else {
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: " + extras.getString("content"), extras);
				}
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	private PendingIntent createClickedIntent(String flow, Bundle extras) {
		Intent intent = new Intent(this, DismissNotification.class);
		intent.setAction("notification_clicked");
		intent.putExtra("flow", flow);
		if (extras.containsKey("flow_url")) {
			intent.putExtra("flow_url", extras.getString("flow_url"));
		}

		return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}

	private PendingIntent createDismissedIntent(String flow) {

		Intent intent = new Intent(this, DismissNotification.class);
		intent.setAction("notification_cancelled");
		intent.putExtra("flow", flow);

		return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}

	// Put the message into a notification and post it.
	private void sendNotification(String flow, String msg, Bundle extras) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

		Boolean notifyOwnMessages = prefs.getBoolean("prefNotifyOwnMessages", false);
		Boolean isOwnMessage = extras.getString("own", "false").equals("true");

		// notify on own messages
		if (isOwnMessage && !notifyOwnMessages) {
			Log.i(TAG, "Skipping message (user sent): " + extras.toString());

			mNotificationManager.cancel(NotificationHelper.getFlowId(extras.getString("flow")));
			NotificationHelper.cleanNotifications(extras.getString("flow"));

			return;
		}

		NotificationHelper.addNotification(flow, msg);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);

		Boolean silentMode = prefs.getBoolean("prefNotifySilent", false);

		if (!silentMode) {
			Date now = new Date();
			Date lastNotification = NotificationHelper.getLastNotificationDate(flow);
			Long timeSinceLastNotification = now.getTime() - lastNotification.getTime();

			Integer frequency = Integer.parseInt(prefs.getString("prefNotifyVibrationFrequency", "15")) * 1000;
			Boolean notifyWhenActive = prefs.getBoolean("prefNotifyWhenActive", false);
			Boolean isActive = extras.getString("active", "false").equals("true");

			if (timeSinceLastNotification <= frequency) {
				Log.i(TAG, "Skipping vibration -- cooldown in effect");

			} else if (isActive && !notifyWhenActive) {
				Log.i(TAG, "Skipping vibration -- user already active");

			} else {
				mBuilder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
			}
		}


		mBuilder
				.setSmallIcon(R.drawable.notification)
				.setContentTitle(flow)
				.setContentText(Html.fromHtml(msg))
				.setAutoCancel(true)
				.setContentIntent(createClickedIntent(flow, extras))
				.setDeleteIntent(createDismissedIntent(flow))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setTicker(Html.fromHtml(msg));

		Notification notification;

		ArrayList<String> prevMessages = NotificationHelper.getNotifications(flow);
		Integer pendingCount = prevMessages.size();

		if(pendingCount == 1) {
			// Only one notification : display using BigTextStyle for multiline.
			NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle()
					.bigText(Html.fromHtml(msg));

			mBuilder.setStyle(style);

			notification = mBuilder.build();
		}
		else {
			// More than one notification: use inbox style, displaying up to 5 messages
			NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

			for (int i = 0; i < Math.min(pendingCount, 5); i++) {
				style.addLine(Html.fromHtml(prevMessages.get(i)));
			}

			mBuilder
					.setStyle(style)
					.setContentInfo(Integer.toString(pendingCount))
					.setNumber(pendingCount);

			NotificationCompat.BigTextStyle pageStyle = new NotificationCompat.BigTextStyle();
			StringBuilder pageText = new StringBuilder();

			// And then add a second page for Wearables, displaying the whole pending conversation
			for (int i = pendingCount - 1; i >= 0; i--) {
				if(i < pendingCount - 1){
					pageText.append("<br /><br />");
				}
				pageText.append(prevMessages.get(i));
			}

			pageStyle.bigText(Html.fromHtml(pageText.toString()));

			Notification secondPage = new NotificationCompat.Builder(this)
					.setStyle(pageStyle)
					.build();

			notification = new NotificationCompat.WearableExtender()
					.addPage(secondPage)
					.extend(mBuilder)
					.build();

		}

		mNotificationManager.notify(NotificationHelper.getFlowId(flow), notification);
		Log.i(TAG, "Displaying message: " + extras.toString());
	}
}
