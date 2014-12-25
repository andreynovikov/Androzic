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

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.location.ILocationService;
import com.androzic.location.ITrackingListener;
import com.androzic.location.LocationService;

public class CurrentTrackOverlay extends TrackOverlay
{
	private ILocationService trackingService = null;
	private boolean isBound = false;

    public CurrentTrackOverlay()
    {
    	super();

    	onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(application));
    	
    	track.name = "Current Track";
    	track.show = true;

        isBound = application.bindService(new Intent(application, LocationService.class), trackingConnection, 0);
    }
    
    @Override
	public void setTrack(Track track)
	{
    	clear();
		this.track = track;
	}

	public void clear()
	{
		track.clear();
	}

	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		unbind();
    }
	
	private void unbind()
	{
    	if (isBound)
    	{
    		if (trackingService != null)
    		{
               	trackingService.unregisterTrackingCallback(trackingListener);
    		}

    		application.unbindService(trackingConnection);
    		isBound = false;
    	}
	}
	
	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		super.onPreferencesChanged(settings);
    	track.maxPoints = Integer.parseInt(settings.getString(application.getString(R.string.pref_tracking_currentlength), application.getString(R.string.def_tracking_currentlength)));
	}
    
    private ServiceConnection trackingConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            trackingService = (ILocationService) service;
            trackingService.registerTrackingCallback(trackingListener);
        }

        public void onServiceDisconnected(ComponentName className)
        {
            trackingService = null;
        }
    };
    
    private ITrackingListener trackingListener = new ITrackingListener()
    {
        public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double trk, double accuracy, long time)
        {
        	track.addPoint(continous, lat, lon, elev, speed, trk, accuracy, time);
        }
    };

}
