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

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.MapView;
import com.androzic.NavigationService;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.util.Geo;

public class NavigationOverlay extends MapOverlay
{
	Paint paint;
	
	int proximity = 0;
	int radius = 0;
	boolean drawCircle = false;

    private NavigationService navigationService;

    public NavigationOverlay(final Activity activity)
    {
        super(activity);

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(context.getResources().getColor(R.color.navigationline));

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
        calcRadius();
        
        if (context instanceof MapActivity)
        {
            navigationService = ((MapActivity) context).navigationService;        	
        }

        enabled = true;
    }

	public void setMapContext(final Activity activity)
	{
		super.setMapContext(activity);
        navigationService = ((MapActivity) context).navigationService;
	}

    private void calcRadius()
    {
		Androzic application = (Androzic) context.getApplication();
		double[] loc = application.getLocation();
		double[] prx = Geo.projection(loc[0], loc[1], proximity, 90);
		int[] cxy = application.getXYbyLatLon(loc[0], loc[1]);
		int[] pxy = application.getXYbyLatLon(prx[0], prx[1]);
		radius = (int) Math.hypot((pxy[0]-cxy[0]), (pxy[1]-cxy[1]));    	
    }
    
	@Override
	public void onMapChanged()
	{
		calcRadius();
	}

	@Override
	protected void onDraw(Canvas c, MapView mapView)
	{
		if (navigationService.navWaypoint == null)
			return;
		
		Androzic application = (Androzic) context.getApplication();

		int[] xy = application.getXYbyLatLon(navigationService.navWaypoint.latitude, navigationService.navWaypoint.longitude);

        final double[] loc = mapView.currentLocation;
        final int[] cxy = mapView.currentXY;

        int sx = xy[0] - cxy[0] + Math.round(mapView.getWidth() / 2);
        int sy = xy[1] - cxy[1] + Math.round(mapView.getHeight() / 2);
        
        if (sx >= 0 && sy >= 0 && sx <= mapView.getWidth() && sy <= mapView.getHeight())
        {
        	c.drawLine(cxy[0], cxy[1], xy[0], xy[1], paint);
        }
        else
        {
        	double bearing = Geo.bearing(loc[0], loc[1], navigationService.navWaypoint.latitude, navigationService.navWaypoint.longitude);
        	c.save();
        	c.rotate((float) bearing, cxy[0], cxy[1]);
        	c.drawLine(cxy[0], cxy[1], cxy[0], 0, paint);
        	c.restore();
        }
        if (drawCircle && navigationService.navRoute != null)
        {
	        List<Waypoint> waypoints = navigationService.navRoute.getWaypoints();
	        synchronized (waypoints)
	        {  
		        for (Waypoint wpt : waypoints)
		        {
		            xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
		            c.drawCircle(xy[0], xy[1], radius, paint);
		        }
	        }
        }
	}

	@Override
	protected void onDrawFinished(Canvas c, MapView mapView)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		proximity = Integer.parseInt(settings.getString(context.getString(R.string.pref_navigation_proximity), context.getString(R.string.def_navigation_proximity)));
		drawCircle = settings.getBoolean(context.getString(R.string.pref_navigation_proximitycircle), context.getResources().getBoolean(R.bool.def_navigation_proximitycircle));
        paint.setStrokeWidth(settings.getInt(context.getString(R.string.pref_navigation_linewidth), context.getResources().getInteger(R.integer.def_navigation_linewidth)));
	}

}
