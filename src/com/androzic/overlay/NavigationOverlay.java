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
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.map.Map;
import com.androzic.navigation.NavigationService;

public class NavigationOverlay extends MapOverlay
{
	private Paint paint;
	
	private int proximity = 0;
	private double mpp;
	private boolean drawCircle = false;

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
    	mpp = 0;
        
        if (context instanceof MapActivity)
        {
            navigationService = ((MapActivity) context).navigationService;        	
        }

        enabled = true;
    }

	@Override
	public void setMapContext(final Activity activity)
	{
		super.setMapContext(activity);
        navigationService = ((MapActivity) context).navigationService;
	}

	@Override
	public synchronized void onMapChanged()
	{
		mpp = 0;
    	Androzic application = Androzic.getApplication();
    	Map map = application.getCurrentMap();
    	if (map == null)
    		return;
    	
    	mpp = map.mpp / map.getZoom();
	}
	
	@Override
	protected void onDraw(Canvas c, MapView mapView, int centerX, int centerY)
	{
		if (navigationService.navWaypoint == null)
			return;
		
		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		int[] xy = application.getXYbyLatLon(navigationService.navWaypoint.latitude, navigationService.navWaypoint.longitude);

        if (mapView.currentLocation != null)
        {
            final int[] lxy = mapView.currentLocationXY;
        	c.drawLine(lxy[0] - cxy[0], lxy[1] - cxy[1], xy[0] - cxy[0], xy[1] - cxy[1], paint);
        }
        if (drawCircle && navigationService.navRoute != null)
        {
	        List<Waypoint> waypoints = navigationService.navRoute.getWaypoints();
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
	protected void onDrawFinished(Canvas c, MapView mapView, int centerX, int centerY)
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
