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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;

import com.androzic.R;
import com.androzic.ui.Viewport;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class DistanceOverlay extends MapOverlay
{
	Paint linePaint;
	Paint circlePaint;
	Paint textPaint;
	Paint textFillPaint;
	
	double[] ancor;
	int [] ancorXY;

    public DistanceOverlay()
    {
        super();

		Resources resources = application.getResources();

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(5);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(resources.getColor(R.color.distanceline));
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setStrokeWidth(1);
        circlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        circlePaint.setColor(resources.getColor(R.color.distanceline));
        circlePaint.setAlpha(255);
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(2);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Align.LEFT);
        textPaint.setTextSize(10);
        textPaint.setTypeface(Typeface.SANS_SERIF);
        textPaint.setColor(resources.getColor(R.color.waypointtext));
        textFillPaint = new Paint();
        textFillPaint.setAntiAlias(false);
        textFillPaint.setStrokeWidth(1);
        textFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textFillPaint.setColor(resources.getColor(R.color.distanceline));

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(application));
        
        enabled = false;
    }
    
    public void setAncor(double[] ancor)
    {
    	this.ancor = ancor;
        ancorXY = application.getXYbyLatLon(this.ancor[0], this.ancor[1]);
    }

	@Override
	public void onMapChanged()
	{
		super.onMapChanged();
		if (ancor == null)
			return;
		ancorXY = application.getXYbyLatLon(this.ancor[0], this.ancor[1]);
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		if (ancor == null)
			return;
		
		final double[] loc = viewport.mapCenter;
		final int[] cxy = viewport.mapCenterXY;

        int sx = ancorXY[0] - cxy[0] + viewport.width / 2;
        int sy = ancorXY[1] - cxy[1] + viewport.height / 2;
        
        if (ancorXY[0] != cxy[0] || ancorXY[1] != cxy[1])
        {
	        if (sx >= 0 && sy >= 0 && sx <= viewport.width && sy <= viewport.height)
	        {
	        	c.drawLine(0, 0, ancorXY[0]-cxy[0], ancorXY[1]-cxy[1], linePaint);
	        	c.drawCircle(ancorXY[0]-cxy[0], ancorXY[1]-cxy[1], linePaint.getStrokeWidth(), circlePaint);
	        }
	        else
	        {
	        	double bearing = Geo.bearing(loc[0], loc[1], ancor[0], ancor[1]);
	        	c.save();
	        	c.rotate((float) bearing, 0, 0);
	        	c.drawLine(0, 0, 0, - viewport.height / 2 - viewport.width / 2, linePaint);
	        	c.restore();
	        }
        }
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
		if (ancor == null)
			return;
		
		final double[] loc = viewport.mapCenter;
		
		double dist = Geo.distance(loc[0], loc[1], ancor[0], ancor[1]);
		double bearing = Geo.bearing(loc[0], loc[1], ancor[0], ancor[1]);
		if (dist > 0)
		{
			String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.angleH(bearing);
	
			Rect rect = new Rect();
	    	textPaint.getTextBounds(distance, 0, distance.length(), rect);
	    	int half = rect.width() / 2;
	    	int dy = bearing > 90 && bearing < 270 ? -60 : 60+rect.height()/2;
	        rect.offset(-half, +dy);
	        rect.inset(-4, -4);
	        c.drawRect(rect, textFillPaint);
	    	c.drawText(distance, -half, +dy, textPaint);
		}
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		Resources resources = application.getResources();
        linePaint.setStrokeWidth(settings.getInt(application.getString(R.string.pref_navigation_linewidth), resources.getInteger(R.integer.def_navigation_linewidth)));
        int textSize = settings.getInt(application.getString(R.string.pref_waypoint_width), resources.getInteger(R.integer.def_waypoint_width));
        textPaint.setTextSize(textSize * 2);
	}
}
