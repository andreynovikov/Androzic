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

import java.util.List;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;

import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.map.BaseMap;
import com.androzic.ui.Viewport;

public class NavigationOverlay extends MapOverlay
{
	private Paint paint;
	
	private int proximity = 0;
	private double mpp;
	private boolean drawCircle = false;

    public NavigationOverlay()
    {
        super();

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(application.getResources().getColor(R.color.navigationline));

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(application));
    	mpp = 0;
        
        enabled = true;
    }

	@Override
	public synchronized void onMapChanged()
	{
		mpp = 0;
    	BaseMap map = application.getCurrentMap();
    	if (map == null)
    		return;
    	
    	mpp = map.getMPP();
	}
	
	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		if (application.navigationService.navWaypoint == null)
			return;
		
		final int[] cxy = viewport.mapCenterXY;

		int[] xy = application.getXYbyLatLon(application.navigationService.navWaypoint.latitude, application.navigationService.navWaypoint.longitude);

        if (!Double.isNaN(viewport.location[0]))
        {
            final int[] lxy = viewport.locationXY;
        	c.drawLine(lxy[0] - cxy[0], lxy[1] - cxy[1], xy[0] - cxy[0], xy[1] - cxy[1], paint);
        }
        if (drawCircle && application.navigationService.navRoute != null)
        {
	        List<Waypoint> waypoints = application.navigationService.navRoute.getWaypoints();
	        synchronized (waypoints)
	        {  
		        for (Waypoint wpt : waypoints)
		        {
		        	int radius = wpt.proximity > 0 ? wpt.proximity : proximity;
		        	radius /= mpp;
		        	if (radius > 0)
		        	{
		        		xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
		        		c.drawCircle(xy[0] - cxy[0], xy[1] - cxy[1], radius, paint);
		        	}
		        }
	        }
        }
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		Resources resources = application.getResources();
		proximity = Integer.parseInt(settings.getString(application.getString(R.string.pref_navigation_proximity), application.getString(R.string.def_navigation_proximity)));
		drawCircle = settings.getBoolean(application.getString(R.string.pref_navigation_proximitycircle), resources.getBoolean(R.bool.def_navigation_proximitycircle));
		paint.setColor(settings.getInt(application.getString(R.string.pref_navigation_linecolor), resources.getColor(R.color.navigationline)));
        paint.setStrokeWidth(settings.getInt(application.getString(R.string.pref_navigation_linewidth), resources.getInteger(R.integer.def_navigation_linewidth)));
	}

}
