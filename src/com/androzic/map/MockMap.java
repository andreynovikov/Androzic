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

package com.androzic.map;

import android.graphics.Canvas;
import android.view.View;

public class MockMap extends Map
{
	private static final long serialVersionUID = 1L;
	
	private static MockMap currentMap;

	private int lat;
	private int lon;
	
	public MockMap(int lat, int lon)
	{
		super("//_mock_map_//");
		title = "-no map-";
		datum = "WGS84";
		mpp = 0.1;
		this.lat = lat;
		this.lon = lon;
	}

	@Override
	public void activate(View view, int pixels)
	{
	}

	@Override
	public void deactivate()
	{
	}

	@Override
	public boolean coversLatLon(double lat, double lon)
	{
		return false;
	}

	@Override
	public void drawMap(double[] loc, int[] lookAhead, int width, int height, Canvas c) throws OutOfMemoryError
	{
	}

	@Override
	public boolean getLatLonByXY(int x, int y, double[] ll)
	{
		ll[0] = 90 - (y * 1.0 / (50000 * scale) + lat);
		ll[1] = x * 1.0 / (50000 * scale) + lon - 180;
		return true;
	}

	@Override
	public boolean getXYByLatLon(double lat, double lon, int[] xy)
	{
		lat = 90 - lat;
		lon = 180 + lon;
		xy[1] = (int) ((lat - this.lat) * 50000 * scale);
		xy[0] = (int) ((lon - this.lon) * 50000 * scale);
		return true;
	}

	@Override
	public double getNextZoom()
	{
		if (scale >= 10)
			return 0.0;
		return scale + 0.1;
	}

	@Override
	public double getPrevZoom()
	{
		if (scale <= 0.001)
			return 0.0;
		return scale - 0.1;
	}

	@Override
	public double getZoom()
	{
		return scale;
	}

	@Override
	public void setZoom(double zoom)
	{
		scale = Math.floor(zoom * 1000) / 1000;
		if (scale > 10) scale = 10;
		if (scale < 0.001) scale = 0.001;
	}

	public static Map getMap(double lat, double lon)
	{
		int ilat = (int) (90 - lat);
		int ilon = (int) (180 + lon);
		if (currentMap == null)
		{
			currentMap = new MockMap(ilat, ilon);
		}
		else if (currentMap.lat != ilat || currentMap.lon != ilon)
		{
			double s = currentMap.scale; 
			currentMap = new MockMap(ilat, ilon);
			currentMap.scale = s;
		}
		return currentMap;
	}
}
