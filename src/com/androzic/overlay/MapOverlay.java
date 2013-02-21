/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.androzic.MapView;

public abstract class MapOverlay
{
	Activity context;
	boolean enabled;
	
	MapOverlay(final Activity activity)
	{
		context = activity;
		enabled = false;
	}
	
	public void setMapContext(final Activity activity)
	{
		context = activity;
	}
	
	/**
	 * Called when application preferences where changed
	 * @param settings <code>SharedPreferences</code> containing current preferences
	 */
	public abstract void onPreferencesChanged(final SharedPreferences settings);
	
	/**
	 * Managed Draw calls gives Overlays the possibility to first draw manually and after 
	 * that do a final draw. This is very useful, i sth. to be drawn needs to be <b>topmost</b>.
	 */
	public void onManagedDraw(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		if (enabled)
		{
			onDraw(c, mapView, centerX, centerY);
			onDrawFinished(c, mapView, centerX, centerY);
		}
	}

	protected abstract void onDraw(final Canvas c, final MapView mapView, final int centerX, final int centerY);
		
	protected abstract void onDrawFinished(final Canvas c, final MapView mapView, final int centerX, final int centerY);
	
	public void onBeforeDestroy()
	{
		enabled = false;
	}

	public void onMapChanged()
	{
	}

	public boolean setEnabled(boolean enabled)
	{
		boolean r = this.enabled;
		this.enabled = enabled;
		return r;
	}

	public boolean onKeyDown(final int keyCode, KeyEvent event, final MapView mapView)
	{
		return false;
	}
		
	public boolean onKeyUp(final int keyCode, KeyEvent event, final MapView mapView)
	{
		return false;
	}
		
	public boolean onTouchEvent(final MotionEvent event, final MapView mapView)
	{
		return false;
	}
	
	public boolean onTrackballEvent(final MotionEvent event, final MapView mapView)
	{
		return false;
	}

	public boolean onSingleTap(MotionEvent e, Rect mapTap, MapView mapView)
	{
		return false;
	}

	public boolean onLongPress(MotionEvent e, MapView mapView)
	{
		return false;
	}
}
