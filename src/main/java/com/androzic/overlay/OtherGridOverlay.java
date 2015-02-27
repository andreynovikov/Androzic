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
import android.util.Log;

import com.androzic.R;
import com.androzic.map.BaseMap;
import com.androzic.map.ozf.Grid;
import com.androzic.ui.Viewport;
import com.androzic.util.Geo;

public class OtherGridOverlay extends MapOverlay
{
	ArrayList<Path> paths = new ArrayList<>();
	Paint linePaint;
	Rect clip;
	int spacing = 100000;
	int maxMPP = 0;
	
	public OtherGridOverlay()
	{
		super();

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(1);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(application.getResources().getColor(R.color.distanceline));
	}

	public void setGrid(Grid grid)
	{
		spacing = (int) grid.spacing;
		maxMPP = grid.maxMPP;
		linePaint.setColor(grid.spacing >= 1000 ? grid.color1 : grid.color2);
		enabled = true;
	}

	@Override
	public synchronized void onMapChanged()
	{
		paths.clear();
    	BaseMap map = application.getCurrentMap();
    	if (map == null)
    		return;
    	
    	Log.e("GRID", "mpp: "+maxMPP+" "+(map.getMPP()));
		if (maxMPP > 0 && maxMPP < (map.getMPP()))
			return;

    	clip = new Rect(0, 0, map.getScaledWidth(), map.getScaledHeight());

    	ArrayList<int[]> points = new ArrayList<>();
    	ArrayList<double[]> refPoints = new ArrayList<>();

    	// find map center coordinates
    	double[] cll = new double[2];
		int[] cxy = new int[] { map.getScaledWidth() / 2,  map.getScaledHeight() / 2};
    	map.getLatLonByXY(cxy[0], cxy[1], cll);
    	
    	// build vertical reference path
    	int y = cxy[1];
    	double[] ll = new double[] { cll[0], cll[1] };
		refPoints.add(new double[] {cll[0], cll[1]});
		points.add(cxy);
    	while (y < map.getScaledHeight())
    	{
        	int[] pxy = new int[2];
        	double[] pll = Geo.projection(ll[0], ll[1], spacing * 3, 180);
	    	map.getXYByLatLon(pll[0], pll[1], pxy);
    		refPoints.add(pll);
    		points.add(pxy);
        	ll[0] = pll[0];
        	ll[1] = pll[1];
    		y = pxy[1];
    	}
    	ll = new double[] { cll[0], cll[1] };
    	while (y > 0)
    	{
        	int[] pxy = new int[2];
    		double[] pll = Geo.projection(ll[0], ll[1], spacing * 3, 0);
	    	map.getXYByLatLon(pll[0], pll[1], pxy);
    		refPoints.add(0, pll);
    		points.add(0, pxy);
        	ll[0] = pll[0];
        	ll[1] = pll[1];
    		y = pxy[1];
    	}
   		Path path = new Path();
   		for (int[] p : points)
   		{
   			if (path.isEmpty())
   			{
   				path.moveTo(p[0], p[1]);
   				path.lineTo(p[0], p[1]);
   			}
   			else
   			{
   				path.lineTo(p[0], p[1]);
   			}
   		}
   		paths.add(path);

   		// build vertical paths
   		int i = 1;
   		boolean onmap = true;
    	while (onmap)
    	{
    		points.clear();
    		onmap = false;
    		for (double[] pll : refPoints)
    		{
            	int[] pxy = new int[2];
        		double[] dll = Geo.projection(pll[0], pll[1], spacing * i, 90);
       	    	map.getXYByLatLon(dll[0], dll[1], pxy);
        	    points.add(pxy);
        	    onmap |= pxy[0] <= map.getScaledWidth();
    		}
    		path = new Path();
    		for (int[] p : points)
    		{
    			if (path.isEmpty())
    			{
    				path.moveTo(p[0], p[1]);
    				path.lineTo(p[0], p[1]);
    			}
    			else
    			{
    				path.lineTo(p[0], p[1]);
    			}
    		}
    		paths.add(path);
    		i++;
    	}
   		i = 1;
   		onmap = true;
    	while (onmap)
    	{
    		points.clear();
    		onmap = false;
    		for (double[] pll : refPoints)
    		{
            	int[] pxy = new int[2];
        		double[] dll = Geo.projection(pll[0], pll[1], spacing * i, 270);
       	    	map.getXYByLatLon(dll[0], dll[1], pxy);
        	    points.add(pxy);
        	    onmap |= pxy[0] >= 0;
    		}
    		path = new Path();
    		for (int[] p : points)
    		{
    			if (path.isEmpty())
    			{
    				path.moveTo(p[0], p[1]);
    				path.lineTo(p[0], p[1]);
    			}
    			else
    			{
    				path.lineTo(p[0], p[1]);
    			}
    		}
    		paths.add(path);
    		i++;
    	}
    	// build horizontal reference path
    	refPoints.clear();
    	points.clear();
    	int x = cxy[0];
    	ll = new double[] { cll[0], cll[1] };
		refPoints.add(new double[] {cll[0], cll[1]});
		points.add(cxy);
    	while (x < map.getScaledWidth())
    	{
        	int[] pxy = new int[2];
        	double[] pll = Geo.projection(ll[0], ll[1], spacing * 3, 90);
	    	map.getXYByLatLon(pll[0], pll[1], pxy);
    		refPoints.add(pll);
    		points.add(pxy);
        	ll[0] = pll[0];
        	ll[1] = pll[1];
    		x = pxy[0];
    	}
    	ll = new double[] { cll[0], cll[1] };
    	while (x > 0)
    	{
        	int[] pxy = new int[2];
    		double[] pll = Geo.projection(ll[0], ll[1], spacing * 3, 270);
	    	map.getXYByLatLon(pll[0], pll[1], pxy);
    		refPoints.add(0, pll);
    		points.add(0, pxy);
        	ll[0] = pll[0];
        	ll[1] = pll[1];
    		x = pxy[0];
    	}
   		path = new Path();
   		for (int[] p : points)
   		{
   			if (path.isEmpty())
   			{
   				path.moveTo(p[0], p[1]);
   				path.lineTo(p[0], p[1]);
   			}
   			else
   			{
   				path.lineTo(p[0], p[1]);
   			}
   		}
   		paths.add(path);

   		// build horizontal paths
   		i = 1;
   		onmap = true;
    	while (onmap)
    	{
    		points.clear();
    		onmap = false;
    		for (double[] pll : refPoints)
    		{
            	int[] pxy = new int[2];
        		double[] dll = Geo.projection(pll[0], pll[1], spacing * i, 180);
       	    	map.getXYByLatLon(dll[0], dll[1], pxy);
        	    points.add(pxy);
        	    onmap |= pxy[1] <= map.getScaledHeight();
    		}
    		path = new Path();
    		for (int[] p : points)
    		{
    			if (path.isEmpty())
    			{
    				path.moveTo(p[0], p[1]);
    				path.lineTo(p[0], p[1]);
    			}
    			else
    			{
    				path.lineTo(p[0], p[1]);
    			}
    		}
    		paths.add(path);
    		i++;
    	}
   		i = 1;
   		onmap = true;
    	while (onmap)
    	{
    		points.clear();
    		onmap = false;
    		for (double[] pll : refPoints)
    		{
            	int[] pxy = new int[2];
        		double[] dll = Geo.projection(pll[0], pll[1], spacing * i, 0);
       	    	map.getXYByLatLon(dll[0], dll[1], pxy);
        	    points.add(pxy);
        	    onmap |= pxy[1] >= 0;
    		}
    		path = new Path();
    		for (int[] p : points)
    		{
    			if (path.isEmpty())
    			{
    				path.moveTo(p[0], p[1]);
    				path.lineTo(p[0], p[1]);
    			}
    			else
    			{
    				path.lineTo(p[0], p[1]);
    			}
    		}
    		paths.add(path);
    		i++;
    	}
   	}
	
	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
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
		for (Path path : paths)
		{
			c.drawPath(path, linePaint);
		}
		c.restore();
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}
}
