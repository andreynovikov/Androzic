/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license
 * 
 * Redesigned, fixed bugs and made customizable by Andrey Novikov
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.androzic.ui;

import java.text.DecimalFormat;

import com.androzic.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.preference.DialogPreference;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;

/**
 * SeekbarPreference class implements seekbar {@link android.preference.DialogPreference} edit.
 * <p>
 * Attributes supported:<br/>
 * <code>android:text</code> - current value display suffix (not required)<br/>
 * <code>android:dialogMessage</code> - dialog title (not required)<br/>
 * <p>
 * Styled attributes supported:<br/>
 * <code>min</code> - minimum value, integer, default 0<br/>
 * <code>max</code> - maximum value, integer, default 100<br/>
 * <code>defaultValue</code> - default value, integer, default 0<br/>
 * <code>multiplier</code> - multiplier used for value display (note that it will not affect persisted value), default 1<br/>
 * <code>format</code> - format of value display, suitable for {@link java.text.DecimalFormat}, default "0"
 * 
 * @author Andrey Novikov
 */
public class SeekbarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener
{
	private static final String androidns = "http://schemas.android.com/apk/res/android";

	private SeekBar mSeekBar;
	private TextView mSplashText;
	private TextView mValueText;
	private Context mContext;

	private String mDialogMessage, mSuffix;
	private int mDefault, mMin, mMax, mValue = 0;
	private float mMultiplier = 1.0f;
	private DecimalFormat format;

	public SeekbarPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mContext = context;

		mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
		mSuffix = attrs.getAttributeValue(androidns, "text");
		
		// max and default values are in private namespace because values from integer resource where
		// incorrectly processed when specified in android namespace
		TypedArray sattrs = context.obtainStyledAttributes(attrs, R.styleable.SeekbarPreference);
		mDefault = sattrs.getInt(R.styleable.SeekbarPreference_defaultValue, 0);
		mMin = sattrs.getInt(R.styleable.SeekbarPreference_min, 0);
		mMax = sattrs.getInt(R.styleable.SeekbarPreference_max, 100);
		mMultiplier = sattrs.getFloat(R.styleable.SeekbarPreference_multiplier, 1);
		String fmt = sattrs.getString(R.styleable.SeekbarPreference_format);
		if (fmt == null)
			fmt = "0";
		format = new DecimalFormat(fmt);
	}

	@Override
	protected View onCreateDialogView()
	{
		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(mContext);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(6, 6, 6, 6);

		if (mDialogMessage != null) {
			mSplashText = new TextView(mContext);
			mSplashText.setText(mDialogMessage);
			layout.addView(mSplashText);
		}

		if (mContext != null) {
			mValueText = new TextView(mContext);
			mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
			mValueText.setTextSize(26);
			params = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.FILL_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			layout.addView(mValueText, params);
		}

		mSeekBar = new SeekBar(mContext);
		layout.addView(mSeekBar, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		if (shouldPersist())
			mValue = getPersistedInt(mDefault);

		mSeekBar.setMax(mMax - mMin);
		setProgress(mValue - mMin);
		mSeekBar.setOnSeekBarChangeListener(this);
		return layout;
	}

	@Override
	protected void onBindDialogView(View v)
	{
		super.onBindDialogView(v);
		mSeekBar.setMax(mMax - mMin);
		setProgress(mValue - mMin);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue)
	{
		super.onSetInitialValue(restore, defaultValue);
		if (restore)
			mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
		else
			mValue = (Integer) defaultValue;
		mValue -= mMin;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		if (positiveResult)
		{
			if (callChangeListener(new Integer(mValue)) && shouldPersist())
				persistInt(mValue);
		}
	}

	/**
	 * Called when user changes progress value, stores value in persistent storage if applicable
	 */
	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch)
	{
		mValue = value + mMin;
		if (mValueText != null)
		{
			String t = format.format(mValue * mMultiplier);
			mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
		}
	}

	public void onStartTrackingTouch(SeekBar seek)
	{
	}

	public void onStopTrackingTouch(SeekBar seek)
	{
	}

	/**
	 * Sets real maximum possible value
	 * @param max new maximum
	 */
	public void setMax(int max)
	{
		mMax = max;
	}

	/**
	 * Returns real maximum possible value
	 * @return maximum value
	 */
	public int getMax()
	{
		return mMax;
	}

	/**
	 * Sets real minimum possible value
	 * @param min new minimum
	 */
	public void setMin(int min)
	{
		mMin = min;
	}

	/**
	 * Returns real minimum possible value
	 * @return minimum value
	 */
	public int getMin()
	{
		return mMin;
	}

	/**
	 * Sets fake progress (for internal use)
	 * @param progress fake progress
	 */
	public void setProgress(int progress)
	{
		int value = progress+mMin;
		if (mSeekBar != null)
			mSeekBar.setProgress(progress);
		if (mValueText != null)
		{
			String t = format.format(value * mMultiplier);
			mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
		}
	}

	/**
	 * Returns fake progress for internal use
	 * @return progress
	 */
	public int getProgress()
	{
		return mValue-mMin;
	}
}
