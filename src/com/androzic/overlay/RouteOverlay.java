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
import java.util.Map;
import java.util.WeakHashMap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;

public class RouteOverlay extends MapOverlay
{
	Paint linePaint;
	Paint borderPaint;
	Paint fillPaint;
	Paint textPaint;
	Paint textFillPaint;
	Route route;
	Map<Waypoint,Bitmap> bitmaps;

	int pointWidth = 10;
	int routeWidth = 2;
	boolean showNames;

    public RouteOverlay(final Activity mapActivity)
    {
        super(mapActivity);

       	route = new Route();
       	bitmaps = new WeakHashMap<Waypoint, Bitmap>();

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(routeWidth);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(context.getResources().getColor(R.color.routeline));
        fillPaint = new Paint();
        fillPaint.setAntiAlias(false);
        fillPaint.setStrokeWidth(1);
        fillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        fillPaint.setColor(context.getResources().getColor(R.color.routewaypoint));
        borderPaint = new Paint();
        borderPaint.setAntiAlias(false);
        borderPaint.setStrokeWidth(1);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(context.getResources().getColor(R.color.routeline));
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(2);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Align.LEFT);
        textPaint.setTextSize(pointWidth * 1.5f);
        textPaint.setTypeface(Typeface.SANS_SERIF);
        textPaint.setColor(context.getResources().getColor(R.color.routewaypointtext));
        textFillPaint = new Paint();
        textFillPaint.setAntiAlias(false);
        textFillPaint.setStrokeWidth(1);
        textFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textFillPaint.setColor(context.getResources().getColor(R.color.routewaypointwithalpha));

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));

		enabled = true;
    }

    public RouteOverlay(final Activity mapActivity, final Route aRoute)
    {
    	this(mapActivity);
    	
    	route = aRoute;
    	if (route.lineColor != -1)
    	{
	    	linePaint.setColor(route.lineColor);
	    	borderPaint.setColor(route.lineColor);
    	}
    	else
    	{
    		route.lineColor = linePaint.getColor();
    	}
    	onRoutePropertiesChanged();
    }
    
    public void onRoutePropertiesChanged()
    {
    	if (linePaint.getColor() != route.lineColor)
    	{
	    	linePaint.setColor(route.lineColor);
	    	borderPaint.setColor(route.lineColor);
    	}
        if (route.editing)
        {
        	linePaint.setPathEffect(new DashPathEffect(new float[] { 5, 2}, 0));
        	linePaint.setStrokeWidth(routeWidth*3);
        }
        else
        {
        	linePaint.setPathEffect(null);
        	linePaint.setStrokeWidth(routeWidth);
        }
		bitmaps.clear();
    }
    
	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		bitmaps.clear();
    }

	public Route getRoute()
	{
		return route;
	}

	@Override
	protected void onDraw(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		if (! route.show)
			return;
		
    	Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		final Path path = new Path();
        int i = 0;
        int lastX = 0, lastY = 0;
        List<Waypoint> waypoints = route.getWaypoints();
        synchronized (waypoints)
        {  
	        for (Waypoint wpt : waypoints)
	        {
	            int[] xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
	            
	            if (i == 0)
	            {
	            	path.setLastPoint(xy[0] - cxy[0], xy[1] - cxy[1]);
		            lastX = xy[0];
		            lastY = xy[1];
	            }
	            else
	            {
	            	if (Math.abs(lastX - xy[0]) > 2 || Math.abs(lastY - xy[1]) > 2)
	            	{
            			path.lineTo(xy[0] - cxy[0], xy[1] - cxy[1]);
	    	            lastX = xy[0];
	    	            lastY = xy[1];
	            	}
	            }
	            i++;
	        }
        }
        c.drawPath(path, linePaint);
	}

	@Override
	protected void onDrawFinished(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		if (! route.show)
			return;

		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

        final int half = Math.round(pointWidth / 2);

        List<Waypoint> waypoints = route.getWaypoints();
        
        synchronized (waypoints)
        {  
	        for (Waypoint wpt : waypoints)
	        {
	        	Bitmap bitmap = bitmaps.get(wpt);
	        	if (bitmap == null)
	        	{
	        		int width = pointWidth;
	        		int height = pointWidth+2;

		            if (showNames)
		            {
		            	Rect bounds = new Rect();
		            	textPaint.getTextBounds(wpt.name, 0, wpt.name.length(), bounds);
			            bounds.inset(-2, -4);
		            	width += 5 + bounds.width();
		            	if (height < bounds.height())
		            		height = bounds.height();
		            }

	        		bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	        		Canvas bc = new Canvas(bitmap);

	        		bc.translate(half, half);
	        		if (showNames)
		        		bc.translate(0, 2);
		            bc.drawCircle(0, 0, half, fillPaint);
		            bc.drawCircle(0, 0, half, borderPaint);
		            
		            if (showNames)
		            {
			    		Rect rect = new Rect();
		            	textPaint.getTextBounds(wpt.name, 0, wpt.name.length(), rect);
			            rect.inset(-2, -4);
			            rect.offset(+half+5, +half-2);
			            bc.drawRect(rect, textFillPaint);
		            	bc.drawText(wpt.name, +half+6, +half, textPaint);
		            }
		            bitmaps.put(wpt, bitmap);
	        	}
	            int[] xy = application.getXYbyLatLon(wpt.latitude,wpt.longitude);
	        	c.drawBitmap(bitmap, xy[0]-half-cxy[0], xy[1]-half-cxy[1], null);
	        }
        }
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
    	routeWidth = settings.getInt(context.getString(R.string.pref_route_linewidth), context.getResources().getInteger(R.integer.def_route_linewidth));
    	pointWidth = settings.getInt(context.getString(R.string.pref_route_pointwidth), context.getResources().getInteger(R.integer.def_route_pointwidth));
        showNames = settings.getBoolean(context.getString(R.string.pref_route_showname), true);

        if (! route.editing)
        {
        	linePaint.setStrokeWidth(routeWidth);
        }
        textPaint.setTextSize(pointWidth * 1.5f);
        bitmaps.clear();
	}
	
}
