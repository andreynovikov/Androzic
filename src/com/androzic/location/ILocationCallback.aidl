package com.androzic.location;

import android.location.Location;

oneway interface ILocationCallback
{
	void onLocationChanged(in Location loc, boolean continous, float smoothspeed, float avgspeed);
	void onSensorChanged(float azimuth, float pitch, float roll);
	void onProviderChanged(String provider);
	void onProviderDisabled(String provider);
	void onProviderEnabled(String provider);
	void onGpsStatusChanged(String provider, int status, int fsats, int tsats);
}
