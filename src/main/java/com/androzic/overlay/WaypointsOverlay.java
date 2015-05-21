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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.androzic.MapView;
import com.androzic.data.Waypoint;
import com.androzic.ui.Viewport;

public class WaypointsOverlay extends MapObjectsOverlay
{
	private List<Waypoint> waypoints;

	public WaypointsOverlay()
	{
		super();
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
		synchronized (waypoints)
		{
			for (int i = waypoints.size() - 1; i >= 0; i--)
			{
				Waypoint wpt = waypoints.get(i);
				int[] pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
				if (mapTap.contains(pointXY[0], pointXY[1]))
				{
					return application.getMapHolder().waypointTapped(wpt, (int) e.getX(), (int) e.getY());
				}
			}
		}
		return false;
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
		final int[] cxy = viewport.mapCenterXY;

		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				drawMapObject(viewport, c, wpt, application, cxy);
			}
		}
	}
}
