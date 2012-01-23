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

package com.androzic.location;

import java.util.Iterator;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.androzic.R;

public class LocationService extends Service implements LocationListener, GpsStatus.Listener, SensorEventListener, OnSharedPreferenceChangeListener
{
	public static final int GPS_OFF = 1;
	public static final int GPS_SEARCHING = 2;
	public static final int GPS_OK = 3;

	private boolean useCompass = false;
	private boolean useNetwork = true;
	private int gpsLocationTimeout = 120000;
	
	private LocationManager locationManager = null;
	private SensorManager sensorManager = null;
	
	private int gpsStatus = GPS_OFF;

	private float[] speed = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private float[] speedav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private float[] speedavex = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private float[] magneticValues;
	private float[] accelerometerValues;

	private long lastLocationMillis = 0;
	private long tics = 0;
	private int pause = 1;
	
	private Location lastKnownLocation = null;
	private boolean isContinous = false;
	private float smoothSpeed = 0.0f;
	private float avgSpeed = 0.0f;
	private float azimuth = 0.0f;
	private float pitch = 0.0f;
	private float roll = 0.0f;

	private final RemoteCallbackList<ILocationCallback> callbacks = new RemoteCallbackList<ILocationCallback>();

    private final ILocationRemoteService.Stub binder = new ILocationRemoteService.Stub()
    {
        public void registerCallback(ILocationCallback cb)
        {
            if (cb != null)
            {
            	callbacks.register(cb);
       			updateProvider(cb);
       			updateLocation(cb);
       			updateSensor(cb);
            }
        }
        public void unregisterCallback(ILocationCallback cb)
        {
            if (cb != null)
           	{
            	callbacks.unregister(cb);
           	}
        }
    };

    @Override
	public void onCreate()
	{
		super.onCreate();
		
		lastKnownLocation = new Location("unknown");
		
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_usecompass));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_usenetwork));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_gpstimeout));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		// we need this cause we run in separate thread
		registerReceiver(receiver, new IntentFilter("onSharedPreferenceChanged"));
		connect();
		Log.d("ANDROZIC", "LocationService: service started");
	}

    @Override
	public void onDestroy()
	{
    	disconnect();
    	unregisterReceiver(receiver);
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
		Log.d("ANDROZIC", "LocationService: service stopped");
	}
		
    @Override
    public IBinder onBind(Intent intent)
    {
        if ("com.androzic.location".equals(intent.getAction()) || ILocationRemoteService.class.getName().equals(intent.getAction()))
        {
            return binder;
        }
        else
        {
        	return null;
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("onSharedPreferenceChanged"))
            {
        		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocationService.this);
        		onSharedPreferenceChanged(sharedPreferences, intent.getExtras().getString("key"));
            }
        }
    };

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_usecompass).equals(key))
		{
			useCompass = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_usecompass));			
		}
		if (getString(R.string.pref_loc_usenetwork).equals(key))
		{
			useNetwork = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_loc_usenetwork));			
		}
		if (getString(R.string.pref_loc_gpstimeout).equals(key))
		{
			gpsLocationTimeout = 1000 * sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_loc_gpstimeout));
		}
	}

	private void connect()
	{
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager != null)
		{
			lastLocationMillis = 0;
			pause = 1;
			isContinous = false;
			smoothSpeed = 0.0f;
			avgSpeed = 0.0f;
			locationManager.addGpsStatusListener(this);
			if (useNetwork)
			{
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
				Log.d("ANDROZIC", "LocationService: network provider set");
			}
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			Log.d("ANDROZIC", "LocationService: gps provider set");
		}

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager != null)
		{
//			Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//			Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			Sensor orn = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
//			if (acc != null && mag != null)
			if (orn != null)
			{
//				sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
//				sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
				sensorManager.registerListener(this, orn, SensorManager.SENSOR_DELAY_UI);
				Log.d("ANDROZIC", "LocationService: sensor listener set");
			}
		}
	}

	private void disconnect()
	{
		if (locationManager != null)
		{
			locationManager.removeUpdates(this);
            locationManager.removeGpsStatusListener(this);
            locationManager = null;
		}
		if (sensorManager != null)
		{
			sensorManager.unregisterListener(this);
			sensorManager = null;
		}
	}

	void updateLocation()
	{
		final Location location = lastKnownLocation;
		final boolean continous = isContinous;
		final float smoothspeed = smoothSpeed;
		final float avgspeed = avgSpeed;
		
		final Handler handler = new Handler();
    	final int n = callbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
            final ILocationCallback callback = callbacks.getBroadcastItem(i);
            handler.post(new Runnable() {
				@Override
				public void run()
				{
		            try
		            {
						callback.onLocationChanged(location, continous, smoothspeed, avgspeed);
		            } 
		            catch (RemoteException e)
		            {
						Log.d("ANDROZIC", "LocationService: location broadcast error: "+e.toString());
						e.printStackTrace();
		            }
				}
            });
        }
		Log.d("ANDROZIC", "LocationService: location dispatched: "+n);
        callbacks.finishBroadcast();
	}

	void updateLocation(final ILocationCallback callback)
	{
		try
		{
			if (! "unknown".equals(lastKnownLocation.getProvider()))
				callback.onLocationChanged(lastKnownLocation, isContinous, smoothSpeed, avgSpeed);
		}
		catch (RemoteException e)
		{
			Log.d("ANDROZIC", "LocationService: location broadcast error: "+e.toString());
			e.printStackTrace();
		}
	}

	void updateSensor()
	{
		final float a = azimuth;
		final float p = pitch;
		final float r = roll;
		
		final Handler handler = new Handler();
    	final int n = callbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
            final ILocationCallback callback = callbacks.getBroadcastItem(i);
            handler.post(new Runnable() {
				@Override
				public void run()
				{
		            try
		            {
						callback.onSensorChanged(a, p, r);
		            } 
		            catch (RemoteException e)
		            {
						Log.d("ANDROZIC", "LocationService: sensor broadcast error: "+e.toString());
						e.printStackTrace();
		            }
				}
            });
        }
        callbacks.finishBroadcast();
	}

	void updateSensor(final ILocationCallback callback)
	{
		try
		{
			callback.onSensorChanged(azimuth, pitch, roll);
		}
		catch (RemoteException e)
		{
			Log.d("ANDROZIC", "LocationService: sensor broadcast error: "+e.toString());
			e.printStackTrace();
		}
	}

	void updateProvider(final String provider, final boolean enabled)
	{
		final Handler handler = new Handler();
    	final int n = callbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
            final ILocationCallback callback = callbacks.getBroadcastItem(i);
            handler.post(new Runnable() {
				@Override
				public void run()
				{
		            try
		            {
		            	if (enabled)
		            		callback.onProviderEnabled(provider);
		            	else
		            		callback.onProviderDisabled(provider);
		            } 
		            catch (RemoteException e)
		            {
						Log.d("ANDROZIC", "LocationService: provider status broadcast error: "+e.toString());
						e.printStackTrace();
		            }
				}
            });
        }
		Log.d("ANDROZIC", "LocationService: provider status dispatched: "+n);
        callbacks.finishBroadcast();
	}
	
	void updateProvider(final ILocationCallback callback)
	{
		try
		{
        	if (gpsStatus == GPS_OFF)
        		callback.onProviderDisabled(LocationManager.GPS_PROVIDER);
        	else
        		callback.onProviderEnabled(LocationManager.GPS_PROVIDER);
		}
		catch (RemoteException e)
		{
			Log.d("ANDROZIC", "LocationService: provider broadcast error: "+e.toString());
			e.printStackTrace();
		}
	}

	void updateGpsStatus(final int status, final int fsats, final int tsats)
	{
		gpsStatus = status;
		final Handler handler = new Handler();
    	final int n = callbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
            final ILocationCallback callback = callbacks.getBroadcastItem(i);
            handler.post(new Runnable() {
				@Override
				public void run()
				{
		            try
		            {
	            		callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, status, fsats, tsats);
		            } 
		            catch (RemoteException e)
		            {
						Log.d("ANDROZIC", "LocationService: GPS status broadcast error: "+e.toString());
						e.printStackTrace();
		            }
				}
            });
        }
		Log.d("ANDROZIC", "LocationService: GPS status dispatched: "+n);
        callbacks.finishBroadcast();
	}

	@Override
	public void onLocationChanged(final Location location)
	{
		tics++;

		boolean fromGps = false;
		boolean sendUpdate = false;
		
		long time = SystemClock.elapsedRealtime();
		
		//Log.e("ANDROIC", "Location arrived: "+location.toString());
		
		if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider()))
		{
			if (useNetwork && (gpsStatus == GPS_OFF || (gpsStatus == GPS_SEARCHING && time > lastLocationMillis + gpsLocationTimeout)))
			{
				Log.d("ANDROZIC", "LocationService: new location");
				lastKnownLocation = location;
				lastLocationMillis = time;
				sendUpdate = true;
			}
			else
			{
				return;
			}
		}
		else
		{
			fromGps = true;
			
			Log.d("ANDROZIC", "LocationService: fix arrived");
			
			long prevLocationMillis = lastLocationMillis;
			float prevSpeed = lastKnownLocation.getSpeed();
			
			lastKnownLocation = location;
			lastLocationMillis = time;
			sendUpdate = true;

			// filter speed outrages
			double a = 2 * 9.8 * (lastLocationMillis - prevLocationMillis) / 1000;
			if (Math.abs(lastKnownLocation.getSpeed() - prevSpeed) > a)
			{
				lastKnownLocation.setSpeed(prevSpeed);
			}
			
			// smooth speed
			float smoothspeed = 0;
			float curspeed = lastKnownLocation.getSpeed();
			for (int i = speed.length - 1; i > 1; i--)
			{
				smoothspeed += speed[i];
				speed[i] = speed[i - 1];
			}
			smoothspeed += speed[1];
			if (speed[1] < speed[0] && speed[0] > curspeed)
			{
				speed[0] = (speed[1] + curspeed) / 2;
			}
			smoothspeed += speed[0];
			speed[1] = speed[0];
			lastKnownLocation.setSpeed(speed[1]);
			speed[0] = curspeed;
			if (speed[0] == 0 && speed[1] == 0)
				smoothspeed = 0;
			else
				smoothspeed = smoothspeed / speed.length;
	
			// average speed
			float avspeed = 0;
			for (int i = speedav.length - 1; i >= 0; i--)
			{
				avspeed += speedav[i];
			}
			avspeed = avspeed / speedav.length;
			if (tics % pause == 0)
			{
				if (avspeed > 0)
				{
					float diff = curspeed / avspeed;
					if (0.95 < diff && diff < 1.05)
					{
						for (int i = speedav.length - 1; i > 0; i--)
						{
							speedav[i] = speedav[i - 1];
						}
						speedav[0] = curspeed;
					}
				}
				float fluct = 0;
				for (int i = speedavex.length - 1; i > 0; i--)
				{
					fluct += speedavex[i] / curspeed;
					speedavex[i] = speedavex[i - 1];
				}
				fluct += speedavex[0] / curspeed;
				speedavex[0] = curspeed;
				fluct = fluct / speedavex.length;
				if (0.95 < fluct && fluct < 1.05)
				{
					for (int i = speedav.length - 1; i >= 0; i--)
					{
						speedav[i] = speedavex[i];
					}
					if (pause < 5)
						pause++;
				}
			}
		
			smoothSpeed = smoothspeed;
			avgSpeed = avspeed;
		}

		/*
		 * lastKnownLocation.setSpeed(20);
		 * lastKnownLocation.setBearing(55);
		 * lastKnownLocation.setAltitude(169);
		 * lastKnownLocation.setLatitude(55.852527);
		 * lastKnownLocation.setLongitude(29.451150);
		 */

		if (useCompass && lastKnownLocation.getSpeed() == 0)
		{
			location.setBearing(azimuth);
		}

		if (sendUpdate)
			updateLocation();
		
		isContinous = fromGps;
	}

	@Override
	public void onProviderDisabled(String provider)
	{
		updateProvider(provider, false);
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		updateProvider(provider, true);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider))
		{
	        switch(status)
	        {
	            case LocationProvider.TEMPORARILY_UNAVAILABLE:
	            case LocationProvider.OUT_OF_SERVICE:
	        		isContinous = false;
	            	break;
	        }
		}
	}

	@Override
	public void onGpsStatusChanged(int event)
	{
        switch (event)
        {
        	case GpsStatus.GPS_EVENT_STARTED:
        		updateProvider(LocationManager.GPS_PROVIDER, true);
            	updateGpsStatus(GPS_SEARCHING, 0, 0);
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
        		isContinous = false;
            	break;
            case GpsStatus.GPS_EVENT_STOPPED:
        		isContinous = false;
            	updateGpsStatus(GPS_OFF, 0, 0);
        		updateProvider(LocationManager.GPS_PROVIDER, false);
            	break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
            	if (locationManager == null)
            		return;
				GpsStatus gpsStatus = locationManager.getGpsStatus(null);
				Iterator<GpsSatellite> it = gpsStatus.getSatellites().iterator();
				int tSats = 0;
				int fSats = 0;
				while (it.hasNext())
				{
					tSats++;
					GpsSatellite sat = (GpsSatellite) it.next();
					if (sat.usedInFix())
						fSats++;
				}
            	if (SystemClock.elapsedRealtime() - lastLocationMillis < 3000)
            	{
                	updateGpsStatus(GPS_OK, fSats, tSats);
            	}
            	else
                {
            		isContinous = false;
                	updateGpsStatus(GPS_SEARCHING, fSats, tSats);
                }
                break;
        }
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		boolean ready = false;
		
	    if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
	    	return;

		switch (event.sensor.getType())
		{
			case Sensor.TYPE_ORIENTATION:
				float[] orientation = event.values.clone();
	
//				orientation[0] = (float) Math.toDegrees(orientation[0]);
//				orientation[1] = (float) Math.toDegrees(orientation[1]);
//				orientation[2] = (float) Math.toDegrees(orientation[2]);
	
				azimuth = orientation[0];
				pitch = orientation[1];
				roll = orientation[2];
				
				updateSensor();
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				magneticValues = event.values.clone();
				break;
			case Sensor.TYPE_ACCELEROMETER:
				accelerometerValues = event.values.clone();
//				ready = true;
				break;
		}

		if (magneticValues != null && accelerometerValues != null && ready)
		{
			float[] R = new float[16];
			boolean success = SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticValues);
			if (success)
			{
				float[] orientation = new float[3];
				SensorManager.getOrientation(R, orientation);
	
				orientation[0] = (float) Math.toDegrees(orientation[0]);
				orientation[1] = (float) Math.toDegrees(orientation[1]);
				orientation[2] = (float) Math.toDegrees(orientation[2]);
	/*
				if (orientation[0] >= 360) orientation[0] -= 360;
				if (orientation[0] < 0) orientation[0] = 360 - orientation[0];
				if (orientation[1] >= 360) orientation[1] -= 360;
				if (orientation[1] < 0) orientation[1] = 360 - orientation[1];
				if (orientation[2] >= 360) orientation[2] -= 360;
				if (orientation[2] < 0) orientation[2] = 360 - orientation[2];
				*/
	
				azimuth = orientation[0];
				pitch = orientation[1];
				roll = orientation[2];
				
				updateSensor();
			}
		}
	}
}