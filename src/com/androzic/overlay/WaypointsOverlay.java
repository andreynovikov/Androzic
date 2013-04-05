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

import java.util.List;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.MapView;
import com.androzic.data.Waypoint;

public class WaypointsOverlay extends MapObjectsOverlay
{
	private List<Waypoint> waypoints;

	public WaypointsOverlay(final Activity mapActivity)
	{
		super(mapActivity);
		enabled = true;
	}

	public void setWaypoints(final List<Waypoint> wpt)
	{
		waypoints = wpt;
		clearBitmapCache();
	}

	@Override
	public boolean onSingleTap(MotionEvent e, Rect mapTap, MapView mapView)
	{
		Androzic application = Androzic.getApplication();

		synchronized (waypoints)
		{
			for (int i = waypoints.size() - 1; i >= 0; i--)
			{
				Waypoint wpt = waypoints.get(i);
				int[] pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
				if (mapTap.contains(pointXY[0], pointXY[1]) && context instanceof MapActivity)
				{
					return ((MapActivity) context).waypointTapped(wpt, (int) e.getX(), (int) e.getY());
				}
			}
		}
		return false;
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

		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				drawMapObject(c, wpt, application, cxy);
			}
		}
	}
}
