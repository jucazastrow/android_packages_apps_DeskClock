/*
 * Copyright (C) 2012 AChep@xda 
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

import java.util.Random;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AlarmMath extends Activity implements OnClickListener,
		OnLongClickListener {

	// Math
	private String mTextAnswer = "", mTextMathQ;
	private int mTrueIntAnswer;

	// Buttons
	private Button[] mMathButton = new Button[10];
	private Button mMathDoneButton, mMathResetButton;

	// Title texts
	private TextView mMathTitleText;

	private void initMathButton(int i, int id) {
		mMathButton[i] = (Button) findViewById(id);
		mMathButton[i].setOnClickListener(this);
		mMathButton[i].setText(i + "");
	}

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.alarm_math);

		// Get title text
		mMathTitleText = (TextView) findViewById(R.id.math_text);

		// Get buttons from layout
		initMathButton(0, R.id.math_0);
		for (int i = 0; i < 9; i++)
			initMathButton(i + 1, R.id.math_1 + i);
		mMathDoneButton = (Button) findViewById(R.id.math_done);
		mMathDoneButton.setOnClickListener(this);
		mMathDoneButton.setText("NEXT");

		mMathResetButton = (Button) findViewById(R.id.math_reset);
		mMathResetButton.setOnClickListener(this);
		mMathResetButton.setOnLongClickListener(this);
		mMathResetButton.setText("<");

		setNewMathQ();
		updateTitleText();
	}

	private void resetCurrentAnswer() {
		mTextAnswer = "";
		mMathDoneButton.setText("NEXT");
	}

	private void updateTitleText() {
		mMathTitleText.setText(mTextMathQ + "=" + mTextAnswer);
	}

	@Override
	public void onClick(View view) {
		if (view == mMathDoneButton) {
			if (!mTextAnswer.equals(""))
				if (Integer.parseInt(mTextAnswer) == mTrueIntAnswer) {

					// Cancel current alarm
					final Alarm alarm = getIntent().getParcelableExtra(
							Alarms.ALARM_INTENT_EXTRA);
					if (alarm != null) {
						((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
								.cancel(alarm.id);
						stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
					}

					// GO TO HEEELLL!!!
					finish();
				} else {
					setNewMathQ();
					resetCurrentAnswer();
					updateTitleText();
				}
		} else if (view == mMathResetButton) {
			if (!mTextAnswer.equals("")) {
				if (mTextAnswer.length() == 1) {
					resetCurrentAnswer();
				} else
					mTextAnswer = mTextAnswer.substring(0,
							mTextAnswer.length() - 1);
				updateTitleText();
			}
		} else if (mTextAnswer.length() < 6)
			for (int i = 0; i < 10; i++) {
				if (view == mMathButton[i]) {
					if (mTextAnswer.equals(""))
						mMathDoneButton.setText("OK");

					mTextAnswer += Integer.toString(i);
					updateTitleText();
					break;
				}
			}
	}

	@Override
	public boolean onLongClick(View view) {
		if (view == mMathResetButton) {
			resetCurrentAnswer();
			updateTitleText();
		}
		return false;
	}

	private int getRandom(int start, int end) {
		return new Random().nextInt(end - start + 1) + start;
	}

	private void setNewMathQ() {
		switch (getRandom(1, 8)) {
		case (1): {
			// a+b
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			mTextMathQ = a + "+" + b;
			mTrueIntAnswer = a + b;
			break;
		}
		case (2): {
			// a*b
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			mTextMathQ = a + "*" + b;
			mTrueIntAnswer = a * b;
			break;
		}
		case (3): {
			// a*b+c
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			int c = getRandom(1, 9);
			mTextMathQ = a + "*" + b + "+" + c;
			mTrueIntAnswer = a * b + c;
			break;
		}
		case (4): {
			// a^2
			int a = getRandom(0, 9);
			mTextMathQ = a + "\u00B2";
			mTrueIntAnswer = a * a;
		}
		case (5): {
			// a*b+cc
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			int c = getRandom(10, 99);
			mTextMathQ = a + "*" + b + "+" + c;
			mTrueIntAnswer = a * b + c;
			break;
		}
		case (6): {
			// aa+bb
			int a = getRandom(10, 99);
			int b = getRandom(10, 99);
			mTextMathQ = a + "+" + b;
			mTrueIntAnswer = a + b;
			break;
		}
		case (7): {
			// aa*b
			int a = getRandom(10, 99);
			int b = getRandom(0, 9);
			mTextMathQ = a + "*" + b;
			mTrueIntAnswer = a + b;
			break;
		}
		case (8): {
			// a^2+b
			int a = getRandom(0, 9);
			int b = getRandom(10, 99);
			mTextMathQ = a + "\u00B2+" + b;
			mTrueIntAnswer = a * a + b;
		}
		}

	}
}