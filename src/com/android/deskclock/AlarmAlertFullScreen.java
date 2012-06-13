/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.deskclock;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Alarm Clock alarm alert: pops visible indicator and plays alarm tone. This
 * activity is the full screen version which shows over the lock screen with the
 * wallpaper as the background.
 */
public class AlarmAlertFullScreen extends Activity implements
		SensorEventListener {

	// These defaults must match the values in res/xml/settings.xml
	private static final String DEFAULT_SNOOZE = "10";
	private static final String DEFAULT_VOLUME_BEHAVIOR = "0";
	protected static final String SCREEN_OFF = "screen_off";

	private static final String KEY_DUAL_MODE_BUTTON = "use_dual_mode_button";

	protected Alarm mAlarm;
	private int mVolumeBehavior;

	// Receives the ALARM_KILLED action from the AlarmKlaxon,
	// and also ALARM_SNOOZE_ACTION / ALARM_DISMISS_ACTION from other
	// applications
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Alarms.ALARM_SNOOZE_ACTION)) {
				snooze();
			} else if (action.equals(Alarms.ALARM_DISMISS_ACTION)) {
				dismiss(false);
			} else {
				Alarm alarm = intent
						.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
				if (alarm != null && mAlarm.id == alarm.id) {
					dismiss(true);
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		mAlarm = getIntent().getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

		// Get the volume/camera button behavior setting
		final String vol = PreferenceManager.getDefaultSharedPreferences(this)
				.getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
						DEFAULT_VOLUME_BEHAVIOR);
		mVolumeBehavior = Integer.parseInt(vol);

		requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

		final Window win = getWindow();
		win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		// Turn on the screen unless we are being launched from the AlarmAlert
		// subclass.
		if (!getIntent().getBooleanExtra(SCREEN_OFF, false)) {
			win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
					| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
					| WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
		}

		updateLayout();

		// Register to get the alarm killed/snooze/dismiss intent.
		IntentFilter filter = new IntentFilter(Alarms.ALARM_KILLED);
		filter.addAction(Alarms.ALARM_SNOOZE_ACTION);
		filter.addAction(Alarms.ALARM_DISMISS_ACTION);
		registerReceiver(mReceiver, filter);

		msensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		rotationMatrix = new float[16];
		accelData = new float[3];
		magnetData = new float[3];
		OrientationData = new float[3];
		msensorManager.registerListener(this,
				msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_UI);
		msensorManager.registerListener(this,
				msensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_UI);
	}

	private void setTitle() {
		String label = mAlarm.getLabelOrDefault(this);
		TextView title = (TextView) findViewById(R.id.alertTitle);
		title.setText(label);
	}

	private void updateLayout() {
		LayoutInflater inflater = LayoutInflater.from(this);

		View contentView = inflater.inflate(R.layout.alarm_alert, null);
		setContentView(contentView);

		/*
		 * snooze behavior: pop a snooze confirmation view, kick alarm manager.
		 */
		Button snooze = (Button) findViewById(R.id.snooze);
		snooze.requestFocus();
		snooze.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				snooze();
			}
		});

		boolean dualModeButtonEnabled = PreferenceManager
				.getDefaultSharedPreferences(this).getBoolean(
						KEY_DUAL_MODE_BUTTON, false);

		View dismiss = findViewById(R.id.dismiss);

		if (dualModeButtonEnabled) {
			snooze.setOnLongClickListener(new Button.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					dismiss(false);
					v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
					return true;
				}
			});
			snooze.setText(R.string.alarm_alert_snooze_text_dual_mode);
			dismiss.setVisibility(View.GONE);
			findViewById(R.id.spacer).setVisibility(View.GONE);
		} else {
			/* dismiss button: close notification */
			dismiss.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v) {
					dismiss(false);
				}
			});
		}

		/* Set the title from the passed in alarm */
		setTitle();
	}

	// Attempt to snooze this alert.
	private void snooze() {
		// Do not snooze if the snooze button is disabled.
		if (!findViewById(R.id.snooze).isEnabled()) {
			dismiss(false);
			return;
		}
		final String snooze = PreferenceManager.getDefaultSharedPreferences(
				this).getString(SettingsActivity.KEY_ALARM_SNOOZE,
				DEFAULT_SNOOZE);
		int snoozeMinutes = Integer.parseInt(snooze);

		final long snoozeTime = System.currentTimeMillis()
				+ (1000 * 60 * snoozeMinutes);
		Alarms.saveSnoozeAlert(AlarmAlertFullScreen.this, mAlarm.id, snoozeTime);

		// Get the display time for the snooze and update the notification.
		final Calendar c = Calendar.getInstance();
		c.setTimeInMillis(snoozeTime);

		// Append (snoozed) to the label.
		String label = mAlarm.getLabelOrDefault(this);
		label = getString(R.string.alarm_notify_snooze_label, label);

		// Notify the user that the alarm has been snoozed.
		Intent cancelSnooze = new Intent(this, AlarmReceiver.class);
		cancelSnooze.setAction(Alarms.CANCEL_SNOOZE);
		cancelSnooze.putExtra(Alarms.ALARM_ID, mAlarm.id);
		PendingIntent broadcast = PendingIntent.getBroadcast(this, mAlarm.id,
				cancelSnooze, 0);
		NotificationManager nm = getNotificationManager();
		Notification n = new Notification(R.drawable.stat_notify_alarm, label,
				0);
		n.setLatestEventInfo(
				this,
				label,
				getString(R.string.alarm_notify_snooze_text,
						Alarms.formatTime(this, c)), broadcast);
		n.flags |= Notification.FLAG_AUTO_CANCEL
				| Notification.FLAG_ONGOING_EVENT;
		nm.notify(mAlarm.id, n);

		String displayTime = getString(R.string.alarm_alert_snooze_set,
				snoozeMinutes);
		// Intentionally log the snooze time for debugging.
		Log.v(displayTime);

		// Display the snooze minutes in a toast.
		Toast.makeText(AlarmAlertFullScreen.this, displayTime,
				Toast.LENGTH_LONG).show();
		stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
		finish();
	}

	private NotificationManager getNotificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	// Dismiss the alarm.
	private void dismiss(boolean killed) {
		Log.i(killed ? "Alarm killed" : "Alarm dismissed by user");
		// The service told us that the alarm has been killed, do not modify
		// the notification or stop the service.
		if (!killed) {
			// Cancel the notification and stop playing the alarm
			NotificationManager nm = getNotificationManager();
			nm.cancel(mAlarm.id);
			stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
		}
		finish();
	}

	/**
	 * this is called when a second alarm is triggered while a previous alert
	 * window is still active.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (Log.LOGV)
			Log.v("AlarmAlert.OnNewIntent()");

		mAlarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

		setTitle();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// If the alarm was deleted at some point, disable snooze.
		if (Alarms.getAlarm(getContentResolver(), mAlarm.id) == null) {
			Button snooze = (Button) findViewById(R.id.snooze);
			snooze.setEnabled(false);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Log.LOGV)
			Log.v("AlarmAlert.onDestroy()");
		// No longer care about the alarm being killed.
		unregisterReceiver(mReceiver);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Do this on key down to handle a few of the system keys.
		boolean up = event.getAction() == KeyEvent.ACTION_UP;
		switch (event.getKeyCode()) {
		// Volume keys and camera keys dismiss the alarm
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_CAMERA:
		case KeyEvent.KEYCODE_FOCUS:
			if (up) {
				switch (mVolumeBehavior) {
				case 1:
					snooze();
					break;

				case 2:
					dismiss(false);
					break;

				default:
					break;
				}
			}
			return true;
		default:
			break;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onBackPressed() {
		// Don't allow back to dismiss. This method is overriden by AlarmAlert
		// so that the dialog is dismissed.
		return;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	private SensorManager msensorManager;

	private float[] rotationMatrix;
	private float[] accelData;
	private float[] magnetData;
	private float[] OrientationData;

	private int BUFFER = 8;
	private int SENSITIVE = 40;
	private int value[] = new int[2];
	private int average[] = new int[2];
	private int num = 0;

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		loadNewSensorData(event);
		SensorManager.getRotationMatrix(rotationMatrix, null, accelData,
				magnetData);
		SensorManager.getOrientation(rotationMatrix, OrientationData);

		if (num < BUFFER) {
			value[1] = (int) Math.round(Math.abs(Math
					.toDegrees(OrientationData[0]))
					+ Math.abs(Math.toDegrees(OrientationData[1]))
					+ Math.abs(Math.toDegrees(OrientationData[2])));
			if (num > 0)
				average[0] += Math.abs(value[0] - value[1]);
			value[0] = value[1];
			num += 1;
		} else {
			average[0] /= BUFFER;
			if ((Math.abs(average[0] - average[1]) > SENSITIVE)
					& average[1] != 0)
				dismiss(false);
			average[1] = average[0];
			num = 0;
			value[1] = (int) Math.round(Math.abs(Math
					.toDegrees(OrientationData[0]))
					+ Math.abs(Math.toDegrees(OrientationData[1]))
					+ Math.abs(Math.toDegrees(OrientationData[2])));
		}
	}

	private void loadNewSensorData(SensorEvent event) {

		final int type = event.sensor.getType();

		if (type == Sensor.TYPE_ACCELEROMETER) {
			accelData = event.values.clone();
		}

		if (type == Sensor.TYPE_MAGNETIC_FIELD) {
			magnetData = event.values.clone();
		}
	}
}
