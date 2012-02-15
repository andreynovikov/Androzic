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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.androzic.MapActivity;
import com.androzic.R;

public class LocationService extends Service implements LocationListener, NmeaListener, GpsStatus.Listener, SensorEventListener, OnSharedPreferenceChangeListener
{
	private static final String TAG = "Location";
	private static final int NOTIFICATION_ID = 24161;
	
	public static final String ENABLE_LOCATIONS = "enableLocations";
	public static final String DISABLE_LOCATIONS = "disableLocations";

	public static final String BROADCAST_LOCATING_STATUS = "com.androzic.locatingStatusChanged";

	public static final int GPS_OFF = 1;
	public static final int GPS_SEARCHING = 2;
	public static final int GPS_OK = 3;

	private boolean locationsEnabled = false;
	private boolean useCompass = false;
	private boolean useNetwork = true;
	private int gpsLocationTimeout = 120000;

	private LocationManager locationManager = null;
	private SensorManager sensorManager = null;

	private Notification notification;
	private PendingIntent contentIntent;

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
	private float nmeaGeoidHeight = Float.NaN;
	private float HDOP = Float.NaN;
	private float VDOP = Float.NaN;

	private final Binder binder = new LocalBinder();
	private final Set<ILocationListener> callbacks = new HashSet<ILocationListener>();

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
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager != null)
		{
			Log.e(TAG, "Sensor manager");

			Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			if (acc != null && mag != null)
			{
				sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
				sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL);
				Log.d(TAG, "Sensor listener set");
			}
		}

		notification = new Notification();
		notification.when = 0;
		contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		notification.icon = R.drawable.ic_stat_location;
		notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_loc_short), getText(R.string.notif_loc_started), contentIntent);

		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && intent.getAction() != null)
		{
			if (intent.getAction().equals(ENABLE_LOCATIONS) && ! locationsEnabled)
			{
				locationsEnabled = true;
				connect();
				startForeground(NOTIFICATION_ID, notification);
				sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
			}
			if (intent.getAction().equals(DISABLE_LOCATIONS) && locationsEnabled)
			{
				locationsEnabled = false;
				stopForeground(true);
				disconnect();
				updateProvider(LocationManager.GPS_PROVIDER, false);
				updateProvider(LocationManager.NETWORK_PROVIDER, false);
				sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
			}
		}
		return START_REDELIVER_INTENT | START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		if (sensorManager != null)
		{
			sensorManager.unregisterListener(this);
			sensorManager = null;
		}
		disconnect();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
		Log.i(TAG, "Service stopped");
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

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
				try
				{
					locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
					Log.d(TAG, "Network provider set");
				}
				catch (IllegalArgumentException e)
				{
					Toast.makeText(this, getString(R.string.err_no_network_provider), Toast.LENGTH_LONG).show();
				}
			}
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			locationManager.addNmeaListener(this);
			Log.d(TAG, "Gps provider set");
		}
	}

	private void disconnect()
	{
		if (locationManager != null)
		{
			locationManager.removeNmeaListener(this);
			locationManager.removeUpdates(this);
			locationManager.removeGpsStatusListener(this);
			locationManager = null;
		}
	}

	private void setNotification(int status)
	{/*
		if (status != gpsStatus)
		{
		    switch (status)
		    {
		    	case LocationService.GPS_OK:
		    		notification.icon = R.drawable.status_icon_ok;
		    	    notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_ongoing_short), getText(R.string.notif_ongoing_ok), contentIntent);
		    		break;
		    	case LocationService.GPS_SEARCHING:
		    		notification.icon = R.drawable.status_icon_searching;
		    	    notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_ongoing_short), getText(R.string.notif_ongoing_searching), contentIntent);
		    		break;
		    	case LocationService.GPS_OFF:
		    		notification.icon = R.drawable.status_icon_off;
		    	    notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_ongoing_short), getText(R.string.notif_ongoing_off), contentIntent);
		    }
		    notificationManager.notify(ANDROZIC_NOTIFICATION_ID, notification);
		    gpsStatus = status;
		}*/
	}

	void updateLocation()
	{
		final Location location = lastKnownLocation;
		final boolean continous = isContinous;
		final boolean geoid = ! Float.isNaN(nmeaGeoidHeight);
		final float smoothspeed = smoothSpeed;
		final float avgspeed = avgSpeed;

		final Handler handler = new Handler();
		for (final ILocationListener callback : callbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed);
				}
			});
		}
		Log.d(TAG, "Location dispatched: " + callbacks.size());
	}

	void updateLocation(final ILocationListener callback)
	{
		if (!"unknown".equals(lastKnownLocation.getProvider()))
			callback.onLocationChanged(lastKnownLocation, isContinous, !Float.isNaN(nmeaGeoidHeight), smoothSpeed, avgSpeed);
	}

	void updateSensor()
	{
		final float a = azimuth;
		final float p = pitch;
		final float r = roll;

		final Handler handler = new Handler();
		for (final ILocationListener callback : callbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					callback.onSensorChanged(a, p, r);
				}
			});
		}
	}

	void updateSensor(final ILocationListener callback)
	{
		callback.onSensorChanged(azimuth, pitch, roll);
	}

	void updateProvider(final String provider, final boolean enabled)
	{
		final Handler handler = new Handler();
		for (final ILocationListener callback : callbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					if (enabled)
						callback.onProviderEnabled(provider);
					else
						callback.onProviderDisabled(provider);
				}
			});
		}
		Log.d(TAG, "Provider status dispatched: " + callbacks.size());
	}

	void updateProvider(final ILocationListener callback)
	{
		if (gpsStatus == GPS_OFF)
			callback.onProviderDisabled(LocationManager.GPS_PROVIDER);
		else
			callback.onProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	void updateGpsStatus(final int status, final int fsats, final int tsats)
	{
		gpsStatus = status;
	    setNotification(status);
		final Handler handler = new Handler();
		for (final ILocationListener callback : callbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, status, fsats, tsats);
				}
			});
		}
		Log.d(TAG, "GPS status dispatched: " + callbacks.size());
	}

	@Override
	public void onLocationChanged(final Location location)
	{
		tics++;

		boolean fromGps = false;
		boolean sendUpdate = false;

		long time = SystemClock.elapsedRealtime();

		// Log.i(TAG, "Location arrived: "+location.toString());

		if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider()))
		{
			if (useNetwork && (gpsStatus == GPS_OFF || (gpsStatus == GPS_SEARCHING && time > lastLocationMillis + gpsLocationTimeout)))
			{
				Log.d(TAG, "New location");
				lastKnownLocation = location;
				lastLocationMillis = time;
				isContinous = false;
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

			Log.d(TAG, "Fix arrived");

			long prevLocationMillis = lastLocationMillis;
			float prevSpeed = lastKnownLocation.getSpeed();

			lastKnownLocation = location;
			lastLocationMillis = time;
			sendUpdate = true;

			if (! Float.isNaN(nmeaGeoidHeight))
			{
				location.setAltitude(location.getAltitude() + nmeaGeoidHeight);
			}

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
		 * lastKnownLocation.setSpeed(20); lastKnownLocation.setBearing(55);
		 * lastKnownLocation.setAltitude(169);
		 * lastKnownLocation.setLatitude(55.852527);
		 * lastKnownLocation.setLongitude(29.451150);
		 */

		 lastKnownLocation.setSpeed(20); lastKnownLocation.setBearing(340);
		 lastKnownLocation.setAltitude(169);
		 lastKnownLocation.setLatitude(55.852527);
		 lastKnownLocation.setLongitude(29.451150);
		 

		if (useCompass && lastKnownLocation.getSpeed() == 0)
		{
			location.setBearing(azimuth);
		}

		if (sendUpdate)
			updateLocation();

		isContinous = fromGps;
	}

	@Override
	public void onNmeaReceived(long timestamp, String nmea)
	{
        if (nmea.indexOf('\n') == 0)
        	return;
        if (nmea.indexOf('\n') > 0)
        {
        	nmea = nmea.substring(0, nmea.indexOf('\n') - 1);
        }
        int len = nmea.length();
        if (len < 9)
        {
            return;
        }
        if (nmea.charAt(len - 3) == '*')
        {
        	nmea = nmea.substring(0, len - 3);
        }
        String[] tokens = nmea.split(",");
        String sentenceId = tokens[0].substring(3, 6);

        try
        {
            if (sentenceId.equals("GGA"))
            {
                //String time = tokens[1];
                //String latitude = tokens[2];
                //String latitudeHemi = tokens[3];
                //String longitude = tokens[4];
                //String longitudeHemi = tokens[5];
                //String fixQuality = tokens[6];
                //String numSatellites = tokens[7];
                //String horizontalDilutionOfPrecision = tokens[8];
                //String altitude = tokens[9];
                //String altitudeUnits = tokens[10];
                String heightOfGeoid = tokens[11];
                if (! "".equals(heightOfGeoid))
                	nmeaGeoidHeight = Float.parseFloat(heightOfGeoid);
                //String heightOfGeoidUnits = tokens[12];
                //String timeSinceLastDgpsUpdate = tokens[13];
            }
            else if (sentenceId.equals("GSA"))
            {
                //String selectionMode = tokens[1]; // m=manual, a=auto 2d/3d
                //String mode = tokens[2]; // 1=no fix, 2=2d, 3=3d
                String pdop = tokens[15];
                String hdop = tokens[16];
                String vdop = tokens[17];
                Log.e(TAG, "PDOP: "+pdop+" HDOP: "+hdop+" VDOP: "+vdop);
                if (! "".equals(hdop))
                	HDOP = Float.parseFloat(hdop);
                if (! "".equals(vdop))
                	VDOP = Float.parseFloat(vdop);                
			}
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "NFE", e);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            Log.e(TAG, "AIOOBE", e);
		}
	}

	@Override
	public void onProviderDisabled(String provider)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider))
			setNotification(GPS_OFF);
		updateProvider(provider, false);
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider))
			setNotification(GPS_SEARCHING);
		updateProvider(provider, true);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider))
		{
			switch (status)
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
		if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
			return;

		switch (event.sensor.getType())
		{
			case Sensor.TYPE_MAGNETIC_FIELD:
				magneticValues = event.values.clone();
				break;
			case Sensor.TYPE_ACCELEROMETER:
				accelerometerValues = event.values.clone();
				break;
		}

		if (magneticValues != null && accelerometerValues != null)
		{
			float R[] = new float[16];
			float I[] = new float[16];
			boolean success = SensorManager.getRotationMatrix(R, I, accelerometerValues, magneticValues);
			if (success)
			{
				float[] orientation = new float[3];
				SensorManager.getOrientation(R, orientation);

				orientation[0] = (float) Math.toDegrees(orientation[0]);
				orientation[1] = (float) Math.toDegrees(orientation[1]);
				orientation[2] = (float) Math.toDegrees(orientation[2]);
				/*
				 * if (orientation[0] >= 360) orientation[0] -= 360; if
				 * (orientation[0] < 0) orientation[0] = 360 - orientation[0];
				 * if (orientation[1] >= 360) orientation[1] -= 360; if
				 * (orientation[1] < 0) orientation[1] = 360 - orientation[1];
				 * if (orientation[2] >= 360) orientation[2] -= 360; if
				 * (orientation[2] < 0) orientation[2] = 360 - orientation[2];
				 */

				azimuth = orientation[0];
				pitch = orientation[1];
				roll = orientation[2];

				updateSensor();
			}
		}
	}

	public class LocalBinder extends Binder implements ILocationService
	{
		@Override
		public void registerCallback(ILocationListener callback)
		{
			updateProvider(callback);
			updateLocation(callback);
			updateSensor(callback);
			callbacks.add(callback);
		}

		@Override
		public void unregisterCallback(ILocationListener callback)
		{
			callbacks.remove(callback);
		}

		@Override
		public boolean isLocating()
		{
			return locationsEnabled;
		}

		@Override
		public float getHDOP()
		{
			return HDOP;
		}

		@Override
		public float getVDOP()
		{
			return VDOP;
		}
	}
}