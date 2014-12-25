package com.androzic.location;

import android.location.Location;

public interface ILocationListener
{
	void onLocationChanged(Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed);
	void onProviderChanged(String provider);
	void onProviderDisabled(String provider);
	void onProviderEnabled(String provider);
	void onGpsStatusChanged(String provider, int status, int fsats, int tsats);
}
