/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

public class ColorButton extends Button
{
	private float mDensity = 0;
	private int mColor = 0;
	private int mDefColor = 0;
	private int mAlpha;
	private OnColorChangedListener mColorChangedListener;

	public ColorButton(Context context)
	{
		super(context);
		init();
	}
	
	public ColorButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}
	
	public ColorButton(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init();
	}

	private void init()
	{
		mDensity = getContext().getResources().getDisplayMetrics().density;
		setCompoundDrawablePadding((int) (mDensity * 5));
		setCompoundDrawablesWithIntrinsicBounds(getPreviewBitmap(), null, null, null);
		setOnClickListener(onClickListener);
	}

	public void setOnColorChangeListener(OnColorChangedListener listener)
	{
		mColorChangedListener = listener;
	}
	
	public void setColor(int color, int defcolor)
	{
		mColor = color;
		mDefColor = defcolor;
		setCompoundDrawablesWithIntrinsicBounds(getPreviewBitmap(), null, null, null);
	}

	public int getColor()
	{
		return mColor;
	}

	private BitmapDrawable getPreviewBitmap()
	{
		int d = (int) (mDensity * 33);
		int color = mColor;
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

		Bitmap b = Bitmap.createBitmap(d, d, Config.ARGB_8888);
		Canvas bc = new Canvas(b);
		Drawable drw = new AlphaPatternDrawable((int) (5 * mDensity));
		drw.setBounds(0, 0, d, d);
		drw.draw(bc);
		bc.drawBitmap(bm, null, new Rect(0, 0, d, d), null);

		return new BitmapDrawable(getResources(), b);
	}
	
	private OnClickListener onClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
    		mAlpha = mColor | 0x00FFFFFF;
    		new ColorPickerDialog(getContext(), onColorChangedListener, mColor | 0xFF000000, mDefColor, true).show();
        }
    };

	private OnColorChangedListener onColorChangedListener = new OnColorChangedListener()
	{
		@Override
		public void colorChanged(int newColor)
		{
			newColor = newColor & mAlpha;
			setColor(newColor, mDefColor);
			if (mColorChangedListener != null)
				mColorChangedListener.colorChanged(newColor);
		}
		
	};



}
