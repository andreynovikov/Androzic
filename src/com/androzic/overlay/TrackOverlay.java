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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.data.Track.TrackPoint;

public class TrackOverlay extends MapOverlay
{
	Paint paint;
	Track track;
	Map<TrackPoint, int[]> points;
	
	private boolean privateProperties = false;

    public TrackOverlay(final Activity mapActivity)
    {
        super(mapActivity);

    	track = new Track();
    	points = new WeakHashMap<TrackPoint, int[]>();
    	
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(context.getResources().getColor(R.color.currenttrack));
        
    	onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
    	
		enabled = true;
    }

    public TrackOverlay(final Activity mapActivity, final Track aTrack)
    {
    	this(mapActivity);
    	
    	track = aTrack;
    	if (track.color != -1)
    	{
	    	paint.setColor(track.color);
	        privateProperties  = true;
    	}
    	else
    	{
    		track.color = paint.getColor();
    	}
    }
    
    public void onTrackPropertiesChanged()
	{
    	if (paint.getColor() != track.color)
    	{
	    	paint.setColor(track.color);
	        privateProperties  = true;
    	}
	}

	public void setTrack(Track track)
	{
		this.track = track;
		onTrackPropertiesChanged();
		points.clear();
	}

	public Track getTrack()
	{
		return track;
	}

	@Override
	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		points.clear();
	}

	@Override
	public void onMapChanged()
	{
		points.clear();
	}

	@Override
	protected void onDraw(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		if (! track.show)
			return;

		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		final Path path = new Path();
        int i = 0;
        int lastX = 0, lastY = 0;
        List<TrackPoint> trackPoints = track.getPoints();
        synchronized (trackPoints)
        {  
	        for (TrackPoint tp : trackPoints)
	        {
	        	int[] xy = null;
        		xy = points.get(tp);
	        	if (xy == null)
	        	{
	        		xy = application.getXYbyLatLon(tp.latitude,tp.longitude);
	        		points.put(tp, xy);
	        	}
	            
	            if (i == 0)
	            {
	            	path.setLastPoint(xy[0]-cxy[0], xy[1]-cxy[1]);
		            lastX = xy[0];
		            lastY = xy[1];
	            }
	            else
	            {
	            	if (Math.abs(lastX - xy[0]) > 2 || Math.abs(lastY - xy[1]) > 2)
	            	{
	
	            		if (tp.continous)
	            			path.lineTo(xy[0]-cxy[0], xy[1]-cxy[1]);
	            		else
	            			path.moveTo(xy[0]-cxy[0], xy[1]-cxy[1]);
	    	            lastX = xy[0];
	    	            lastY = xy[1];
	            	}
	            }
	            i++;
	        }
        }
        c.drawPath(path, paint);
	}

	@Override
	protected void onDrawFinished(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		// TODO has to go to preferences
		if (! privateProperties)
		{
	        paint.setStrokeWidth(Integer.parseInt(settings.getString(context.getString(R.string.pref_tracking_linewidth), "3")));
	        paint.setColor(settings.getInt(context.getString(R.string.pref_tracking_currentcolor), context.getResources().getColor(R.color.currenttrack)));
		}
	}
	
}
