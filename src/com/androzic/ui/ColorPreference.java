package com.androzic.ui;

/*
 * Copyright (C) 2007 The Android Open Source Project
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

/*
 * Portions of this file have been derived from code originally licensed
 * under the Apache License, Version 2.0.
 * 
 * Changes made by Christopher McCurdy, 2009.
 * Fixes and enhancements by Andrey Novikov, 2010.
 */

import com.androzic.R;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Bitmap.Config;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ColorPreference extends DialogPreference
{
	private int mCurrentColor;
	private int mDefaultColor = 0;
	private float mDensity = 0;
	private ColorPickerView mCPView;
	private View mView;

	public ColorPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// default value is in private namespace because values from integer
		// resource where incorrectly processed when specified in android namespace
		TypedArray sattrs = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference);
		mDefaultColor = sattrs.getInt(R.styleable.ColorPreference_defaultColor, Color.RED);
		mDensity = getContext().getResources().getDisplayMetrics().density;
		sattrs.recycle();
	}

	public ColorPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);

		// default value is in private namespace because values from integer
		// resource where
		// incorrectly processed when specified in android namespace
		TypedArray sattrs = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference);
		mDefaultColor = sattrs.getInt(R.styleable.ColorPreference_defaultColor, Color.RED);
		mDensity = getContext().getResources().getDisplayMetrics().density;
	}

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        return a.getColor(index, Color.TRANSPARENT);
    }
    
	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		onColorChanged(restoreValue ? getValue() : (Integer) defaultValue);
	}

	@Override
	protected void onBindView(View view)
	{
		super.onBindView(view);
		mView = view;
		setPreviewColor();
	}

	private void setPreviewColor()
	{
		if (mView == null)
			return;
		ImageView iView = new ImageView(getContext());
		LinearLayout widgetFrameView = ((LinearLayout) mView.findViewById(android.R.id.widget_frame));
		if (widgetFrameView == null)
			return;
		widgetFrameView.setPadding(widgetFrameView.getPaddingLeft(), widgetFrameView.getPaddingTop(), (int) (mDensity * 8), widgetFrameView.getPaddingBottom());
		// remove already create preview image
		int count = widgetFrameView.getChildCount();
		if (count > 0)
		{
			widgetFrameView.removeViews(0, count);
		}
		widgetFrameView.addView(iView);
		iView.setBackgroundDrawable(new AlphaPatternDrawable((int) (5 * mDensity)));
		iView.setImageBitmap(getPreviewBitmap());
	}

	private Bitmap getPreviewBitmap()
	{
		int d = (int) (mDensity * 31); // 30dip
		int color = getValue();
		Bitmap bm = Bitmap.createBitmap(d, d, Config.ARGB_8888);
		int w = bm.getWidth();
		int h = bm.getHeight();
		int c = color;
		for (int i = 0; i < w; i++)
		{
			for (int j = i; j < h; j++)
			{
				c = (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) ? Color.GRAY : color;
				bm.setPixel(i, j, c);
				if (i != j)
				{
					bm.setPixel(j, i, c);
				}
			}
		}

		return bm;
	}

	public int getValue()
	{
		try
		{
			if (isPersistent())
			{
				mCurrentColor = getPersistedInt(mDefaultColor);
			}
		}
		catch (ClassCastException e)
		{
			mCurrentColor = mDefaultColor;
		}

		return mCurrentColor;
	}

	public void onColorChanged(int color)
	{
		if (callChangeListener(new Integer(color)) && shouldPersist())
			persistInt(color);
		mCurrentColor = color;
		setPreviewColor();
		try
		{
			getOnPreferenceChangeListener().onPreferenceChange(this, color);
		}
		catch (NullPointerException e)
		{

		}
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		if (positiveResult)
		{
			onColorChanged(mCPView.getColor());
		}
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder)
	{
		super.onPrepareDialogBuilder(builder);

		OnColorChangedListener l = new OnColorChangedListener() {
			public void colorChanged(int color)
			{
				onDialogClosed(true);
				getDialog().dismiss();
			}
		};

		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		int initialColor = prefs.getInt(getKey(), mDefaultColor);

		mCPView = new ColorPickerView(getContext(), l, initialColor);
		builder.setView(mCPView);
	}

}
