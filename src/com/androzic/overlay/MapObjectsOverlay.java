/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.overlay;

import java.util.Iterator;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.data.MapObject;

public class MapObjectsOverlay extends MapOverlay
{
	public MapObjectsOverlay(final Activity mapActivity)
	{
		super(mapActivity);
		onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
		enabled = true;
	}

	@Override
	protected void onDraw(final Canvas c, final MapView mapVie, int centerX, int centerYw)
	{
	}

	@Override
	protected void onDrawFinished(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		Iterator<MapObject> mapObjects = application.getMapObjects().iterator();
		while (mapObjects.hasNext())
		{
			MapObject mo = mapObjects.next();
			synchronized (mo)
			{
				int[] xy = application.getXYbyLatLon(mo.latitude, mo.longitude);
				int dx = mo.bitmap.getWidth() / 2;
				int dy = mo.bitmap.getHeight() / 2;
				c.drawBitmap(mo.bitmap, xy[0] - dx - cxy[0], xy[1] - dy - cxy[1], null);
			}
		}
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
	}

}
