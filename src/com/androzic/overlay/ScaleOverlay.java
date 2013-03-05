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

package com.androzic.overlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.map.Map;
import com.androzic.util.StringFormatter;

public class ScaleOverlay extends MapOverlay
{
	private static final int SCALE_MOVE_DELAY = 2 * 1000000000;

	private Paint linePaint;
	private Paint textPaint;
	private double mpp;
	private long lastScaleMove;
	private int lastScalePos;

	public ScaleOverlay(Activity activity)
	{
		super(activity);
		linePaint = new Paint();
        linePaint.setAntiAlias(false);
        linePaint.setStrokeWidth(2);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(context.getResources().getColor(R.color.scalebar));
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(2);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTextSize(16);
        textPaint.setTypeface(Typeface.SANS_SERIF);
        textPaint.setColor(context.getResources().getColor(R.color.scalebar));
    	mpp = 0;
    	lastScaleMove = 0;
    	lastScalePos = 1;
    	onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
    	enabled = true;
	}

	@Override
	public synchronized void onMapChanged()
	{
    	Androzic application = (Androzic) context.getApplication();
    	Map map = application.getCurrentMap();
    	if (map == null)
    		return;
    	mpp = map.mpp / map.getZoom();
	}
	
	@Override
	protected void onDraw(Canvas c, MapView mapView, int centerX, int centerY)
	{
		if (mpp == 0)
			return;
		
		int w = mapView.getWidth();
		int m = (int) (mpp * w / 6);
		if (m < 40)
			m = m / 10 * 10;
		else if (m < 80)
			m = 50;
		else if (m < 130)
			m = 100;
		else if (m < 300)
			m = 200;
		else if (m < 700)
			m = 500;
		else if (m < 900)
			m = 800;
		else if (m < 1300)
			m = 1000;
		else if (m < 3000)
			m = 2000;
		else if (m < 7000)
			m = 5000;
		else if (m < 10000)
			m = 8000;
		else if (m < 80000)
			m = (int) (Math.ceil(m * 1. / 10000) * 10000);
		else
			m = (int) (Math.ceil(m * 1. / 100000) * 100000);
		
		int x = (int) (m / mpp);
		
		if (x > w / 4)
		{
			x /= 2;
			m /= 2;
		}
		
		final int x2 = x * 2;
		final int x3 = x * 3;
		final int xd2 = x / 2;
		final int xd4 = x / 4;
		
		int cx = - mapView.lookAheadXY[0] - centerX;
		int cy = - mapView.lookAheadXY[1] - centerY;
		int cty = -10;

		int pos;
		if (mapView.bearing >= 0 && mapView.bearing < 90)
			pos = 1;
		else if (mapView.bearing >= 90 && mapView.bearing < 180)
			pos = 2;
		else if (mapView.bearing >= 180 && mapView.bearing < 270)
			pos = 3;
		else
			pos = 4;

		if (pos != lastScalePos)
		{
			long now = System.nanoTime();
			if (lastScaleMove == 0)
			{
				pos = lastScalePos;
				lastScaleMove = now;
			}
			else if (now > lastScaleMove + SCALE_MOVE_DELAY)
			{
				lastScalePos = pos;
				lastScaleMove = 0;
			}
			else
			{
				pos = lastScalePos;
			}
		}

		if (pos == 1)
		{
			cx += 30;
			cy += mapView.viewArea.bottom - 30;
		}
		else if (pos == 2)
		{
			cx += 30;
			cy += mapView.viewArea.top + 10;
			cty = 30;
		}
		else if (pos == 3)
		{
			cx += mapView.viewArea.right - x3 - 40;
			cy += mapView.viewArea.top + 10;
			cty = 30;
		}
		else
		{
			cx += mapView.viewArea.right - x3 - 40;
			cy += mapView.viewArea.bottom - 30;
		}
		
		c.drawLine(cx, cy, cx+x3, cy, linePaint);
		c.drawLine(cx, cy+10, cx+x3, cy+10, linePaint);
		c.drawLine(cx, cy, cx, cy+10, linePaint);
		c.drawLine(cx+x3, cy, cx+x3, cy+10, linePaint);
		c.drawLine(cx+x, cy, cx+x, cy+10, linePaint);
		c.drawLine(cx+x2, cy, cx+x2, cy+10, linePaint);
		c.drawLine(cx+x, cy+5, cx+x2, cy+5, linePaint);
		c.drawLine(cx, cy+5, cx+xd4, cy+5, linePaint);
		c.drawLine(cx+xd2, cy+5, cx+xd2+xd4, cy+5, linePaint);
		c.drawLine(cx+xd4, cy, cx+xd4, cy+10, linePaint);
		c.drawLine(cx+xd2, cy, cx+xd2, cy+10, linePaint);
		c.drawLine(cx+xd2+xd4, cy, cx+xd2+xd4, cy+10, linePaint);

		c.drawText("0", cx+x, cy+cty, textPaint);
		int t = 2000;
		if (m <= t && m * 2 > t)
			t = m * 3;
		String[] d = StringFormatter.distanceC(m, t);
		c.drawText(d[0], cx+x2, cy+cty, textPaint);
		c.drawText(d[0], cx, cy+cty, textPaint);
		c.drawText(StringFormatter.distanceH(m*2, t), cx+x3, cy+cty, textPaint);
	}

	@Override
	protected void onDrawFinished(Canvas c, MapView mapView, int centerX, int centerY)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		int color = settings.getInt(context.getString(R.string.pref_scalebarcolor), context.getResources().getColor(R.color.scalebar));
		linePaint.setColor(color);
		textPaint.setColor(color);
	}
}
