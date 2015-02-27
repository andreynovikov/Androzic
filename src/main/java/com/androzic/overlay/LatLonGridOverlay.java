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

import java.util.ArrayList;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import com.androzic.R;
import com.androzic.data.Bounds;
import com.androzic.map.BaseMap;
import com.androzic.map.ozf.Grid;
import com.androzic.ui.Viewport;

public class LatLonGridOverlay extends MapOverlay
{
	Paint linePaint;
//	Paint circlePaint;
	ArrayList<int[][]> grid = new ArrayList<int[][]>();
	Rect clip;
	double spacing;
	
	public LatLonGridOverlay()
	{
		super();

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(1);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(application.getResources().getColor(R.color.distanceline));
/*
        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setStrokeWidth(1);
        circlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        circlePaint.setColor(context.getResources().getColor(R.color.distanceline));
        circlePaint.setAlpha(255);
*/
	}

	public void setGrid(Grid grid)
	{
		spacing = grid.spacing;
		linePaint.setColor(grid.spacing >= 1 ? grid.color1 : grid.spacing >= 0.0166666666666667 ? grid.color2 : grid.color3);
		enabled = true;
	}

	@Override
	public synchronized void onMapChanged()
	{
		grid.clear();
    	BaseMap map = application.getCurrentMap();
    	if (map == null)
    		return;
    	clip = new Rect(0, 0, map.getScaledWidth(), map.getScaledHeight());
    	Bounds bounds  = map.getBounds();

    	double lat = bounds.minLat - (bounds.minLat % spacing);
    	double lon = bounds.minLon - (bounds.minLon % spacing);
    	double mlat = (bounds.minLat + bounds.maxLat) / 2;
    	double mlon = (bounds.minLon + bounds.maxLon) / 2;
    	
		int[] xy = new int[2];
    	while (lon <= bounds.maxLon)
    	{
    		int[][] curve = new int[3][2];
    		map.getXYByLatLon(bounds.minLat, lon, xy);
    		curve[0][0] = xy[0];
    		curve[0][1] = xy[1];
    		map.getXYByLatLon(mlat, lon, xy);
    		curve[1][0] = xy[0];
    		curve[1][1] = xy[1];
    		map.getXYByLatLon(bounds.maxLat, lon, xy);
    		curve[2][0] = xy[0];
    		curve[2][1] = xy[1];
    		int[] cp = interpolate(curve[0][0], curve[0][1], curve[1][0], curve[1][1], curve[2][0], curve[2][1], 0.5);
    		curve[1][0] = cp[0];
    		curve[1][1] = cp[1];
    		grid.add(curve);
    		lon += spacing;
    		if (lon >= 180) lon -= 180;
    	}
    	while (lat <= bounds.maxLat)
    	{
    		int[][] curve = new int[3][2];
    		map.getXYByLatLon(lat, bounds.minLon, xy);
    		curve[0][0] = xy[0];
    		curve[0][1] = xy[1];
    		map.getXYByLatLon(lat, mlon, xy);
    		curve[1][0] = xy[0];
    		curve[1][1] = xy[1];
    		map.getXYByLatLon(lat, bounds.maxLon, xy);
    		curve[2][0] = xy[0];
    		curve[2][1] = xy[1];
    		int[] cp = interpolate(curve[0][0], curve[0][1], curve[1][0], curve[1][1], curve[2][0], curve[2][1], 0.5);
    		curve[1][0] = cp[0];
    		curve[1][1] = cp[1];
    		grid.add(curve);
    		lat += spacing;
    	}
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		c.save();
		c.translate(-viewport.mapCenterXY[0], -viewport.mapCenterXY[1]);
		if (clip != null)
		{
			c.clipRect(clip);
		}
		for (int[][] curve : grid)
		{
			//c.drawCircle(curve[0][0], curve[0][1], 2, circlePaint);
			//c.drawCircle(curve[1][0], curve[1][1], 2, circlePaint);
			//c.drawCircle(curve[2][0], curve[2][1], 2, circlePaint);
	    	Path p = new Path();
	    	p.moveTo(curve[0][0], curve[0][1]);
	    	p.quadTo(curve[1][0], curve[1][1], curve[2][0], curve[2][1]);
	    	c.drawPath(p, linePaint);
		}
		c.restore();
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
	}

    // interpolate three points with second point at specified parameter value
    protected int[] interpolate(int x0, int y0, int x1, int y1, int x2, int y2, double t)
    {
    	double t1 = 1.0 -t;
    	double tSq = t * t;
    	double denom = 2.0 * t * t1;
    	
    	int cx = (int) ((x1 - t1 * t1 * x0 - tSq * x2) / denom);
    	int cy = (int) ((y1 - t1 * t1 * y0 - tSq * y2) / denom);
          	
    	return new int[] {cx, cy};
    }
}
