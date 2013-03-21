/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.location;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
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
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.R;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.TDateTime;

public class LocationService extends BaseLocationService implements LocationListener, NmeaListener, GpsStatus.Listener, OnSharedPreferenceChangeListener
{
	private static final String TAG = "Location";
	private static final int NOTIFICATION_ID = 24161;

	/**
	 * Intent action to enable locating
	 */
	public static final String ENABLE_LOCATIONS = "enableLocations";
	/**
	 * Intent action to disable locating
	 */
	public static final String DISABLE_LOCATIONS = "disableLocations";

	public static final String ENABLE_TRACK = "enableTrack";
	public static final String DISABLE_TRACK = "disableTrack";

	public static final String BROADCAST_TRACKING_STATUS = "com.androzic.trackingStatusChanged";

	private boolean locationsEnabled = false;
	private boolean useNetwork = true;
	private int gpsLocationTimeout = 120000;

	private LocationManager locationManager = null;

	private int gpsStatus = GPS_OFF;

	private float[] speed = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private float[] speedav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	private float[] speedavex = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private long lastLocationMillis = 0;
	private long tics = 0;
	private int pause = 1;

	private Location lastKnownLocation = null;
	private boolean isContinous = false;
	private boolean justStarted = true;
	private float smoothSpeed = 0.0f;
	private float avgSpeed = 0.0f;
	private float nmeaGeoidHeight = Float.NaN;
	private float HDOP = Float.NaN;
	private float VDOP = Float.NaN;

	private BufferedWriter trackWriter = null;
	private boolean needsHeader = false;
	private boolean trackingEnabled = false;
	private long errorTime = 0;

	private Location lastWritenLocation = null;
	private Location lastLocation = null;
	private double distanceFromLastWriting = 0;
	private long timeFromLastWriting = 0;

	private long minTime = 2000; // 2 seconds (default)
	private long maxTime = 300000; // 5 minutes
	private int minDistance = 3; // 3 meters (default)
	private int color = Color.RED;
	private int fileInterval;

	private final Binder binder = new LocalBinder();
	private final RemoteCallbackList<ILocationCallback> locationRemoteCallbacks = new RemoteCallbackList<ILocationCallback>();
	private final Set<ILocationListener> locationCallbacks = new HashSet<ILocationListener>();
	private final RemoteCallbackList<ITrackingCallback> trackingRemoteCallbacks = new RemoteCallbackList<ITrackingCallback>();
	private final Set<ITrackingListener> trackingCallbacks = new HashSet<ITrackingListener>();

	private final static DecimalFormat coordFormat = new DecimalFormat("* ###0.000000", new DecimalFormatSymbols(Locale.ENGLISH));

	@Override
	public void onCreate()
	{
		super.onCreate();

		lastKnownLocation = new Location("unknown");

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Location preferences
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_usenetwork));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_gpstimeout));
		// Tracking preferences
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_currentcolor));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_currentinterval));

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && intent.getAction() != null)
		{
			if (intent.getAction().equals(ENABLE_LOCATIONS) && !locationsEnabled)
			{
				locationsEnabled = true;
				connect();
				sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
				if (trackingEnabled)
				{
					sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
				}
			}
			if (intent.getAction().equals(DISABLE_LOCATIONS) && locationsEnabled)
			{
				locationsEnabled = false;
				disconnect();
				updateProvider(LocationManager.GPS_PROVIDER, false);
				updateProvider(LocationManager.NETWORK_PROVIDER, false);
				sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
				closeFile();
				if (trackingEnabled)
					sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
			if (intent.getAction().equals(ENABLE_TRACK) && !trackingEnabled)
			{
				errorTime = 0;
				trackingEnabled = true;
				isContinous = false;
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
				updateNotification();
			}
			if (intent.getAction().equals(DISABLE_TRACK) && trackingEnabled)
			{
				trackingEnabled = false;
				closeFile();
				errorTime = 0;
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
				updateNotification();
			}
		}
		return START_REDELIVER_INTENT | START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		disconnect();
		closeFile();
		super.onDestroy();
		Log.i(TAG, "Service stopped");
	}

	private final ILocationRemoteService.Stub locationRemoteBinder = new ILocationRemoteService.Stub() {
		public void registerCallback(ILocationCallback cb)
		{
			Log.i(TAG, "Register callback");
			if (cb != null)
				locationRemoteCallbacks.register(cb);
		}

		public void unregisterCallback(ILocationCallback cb)
		{
			if (cb != null)
				locationRemoteCallbacks.unregister(cb);
		}

		public boolean isLocating()
		{
			return locationsEnabled;
		}
	};

	private final ITrackingRemoteService.Stub trackingRemoteBinder = new ITrackingRemoteService.Stub() {
		public void registerCallback(ITrackingCallback cb)
		{
			Log.i(TAG, "Register callback");
			if (cb != null)
				trackingRemoteCallbacks.register(cb);
		}

		public void unregisterCallback(ITrackingCallback cb)
		{
			if (cb != null)
				trackingRemoteCallbacks.unregister(cb);
		}
	};

	@Override
	public IBinder onBind(Intent intent)
	{
		if (ANDROZIC_LOCATION_SERVICE.equals(intent.getAction()) || ILocationRemoteService.class.getName().equals(intent.getAction()))
		{
			return locationRemoteBinder;
		}
		if ("com.androzic.tracking".equals(intent.getAction()) || ITrackingRemoteService.class.getName().equals(intent.getAction()))
		{
			return trackingRemoteBinder;
		}
		else
		{
			return binder;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_loc_usenetwork).equals(key))
		{
			useNetwork = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_loc_usenetwork));
		}
		else if (getString(R.string.pref_loc_gpstimeout).equals(key))
		{
			gpsLocationTimeout = 1000 * sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_loc_gpstimeout));
		}
		else if (getString(R.string.pref_tracking_currentcolor).equals(key))
		{
			color = sharedPreferences.getInt(key, getResources().getColor(R.color.currenttrack));
		}
		else if (getString(R.string.pref_tracking_mintime).equals(key))
		{
			try
			{
				minTime = Integer.parseInt(sharedPreferences.getString(key, "500"));
			}
			catch (NumberFormatException e)
			{
			}
		}
		else if (getString(R.string.pref_tracking_mindistance).equals(key))
		{
			try
			{
				minDistance = Integer.parseInt(sharedPreferences.getString(key, "5"));
			}
			catch (NumberFormatException e)
			{
			}
		}
		else if (getString(R.string.pref_tracking_currentinterval).equals(key))
		{
			try
			{
				fileInterval = Integer.parseInt(sharedPreferences.getString(key, "0")) * 3600000;
			}
			catch (NumberFormatException e)
			{
				fileInterval = 0;
			}
			closeFile();
		}
		else if (getString(R.string.pref_folder_data).equals(key))
		{
			closeFile();
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
			justStarted = true;
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
			try
			{
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
				locationManager.addNmeaListener(this);
				Log.d(TAG, "Gps provider set");
			}
			catch (IllegalArgumentException e)
			{
				Log.d(TAG, "Cannot set gps provider, likely no gps on device");
			}
			startForeground(NOTIFICATION_ID, getNotification());
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
			stopForeground(true);
		}
	}

	private Notification getNotification()
	{
		int msgId = R.string.notif_loc_started;
		int ntfId = R.drawable.ic_stat_locating;
		if (trackingEnabled)
		{
			msgId = R.string.notif_trk_started;
			ntfId = R.drawable.ic_stat_tracking;
		}
		if (gpsStatus != LocationService.GPS_OK)
		{
			msgId = R.string.notif_loc_waiting;
			ntfId = R.drawable.ic_stat_waiting;
		}
		if (gpsStatus == LocationService.GPS_OFF)
		{
			ntfId = R.drawable.ic_stat_off;
		}
		if (errorTime > 0)
		{
			msgId = R.string.notif_trk_failure;
			ntfId = R.drawable.ic_stat_failure;
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setWhen(errorTime);
		builder.setSmallIcon(ntfId);
		PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, new Intent(this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		builder.setContentIntent(contentIntent);
		builder.setContentTitle(getText(R.string.notif_loc_short));
		builder.setContentText(getText(msgId));
		builder.setOngoing(true);

		Notification notification = builder.getNotification();
		return notification;
	}

	private void updateNotification()
	{
		if (locationManager != null)
		{
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFICATION_ID, getNotification());
		}
	}

	private void closeFile()
	{
		if (trackWriter != null)
		{
			try
			{
				trackWriter.close();
			}
			catch (Exception e)
			{
				Log.e(TAG, "closeFile", e);
				errorTime = System.currentTimeMillis();
				updateNotification();
			}
			trackWriter = null;
		}
	}

	private void createFile(long time)
	{
		closeFile();
		try
		{
			Androzic application = Androzic.getApplication();
			if (application.dataPath == null)
				return;
			File dir = new File(application.dataPath);
			if (!dir.exists())
				dir.mkdirs();
			String addon = "";
			SimpleDateFormat formatter = new SimpleDateFormat("_yyyy-MM-dd_");
			String dateString = formatter.format(new Date(time));
			if (fileInterval == 24 * 3600000)
			{
				addon = dateString + "daily";
			}
			else if (fileInterval == 168 * 3600000)
			{
				addon = dateString + "weekly";
			}
			File file = new File(dir, "myTrack" + addon + ".plt");
			if (!file.exists())
			{
				file.createNewFile();
				needsHeader = true;
			}
			if (file.canWrite())
			{
				Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
				editor.putString(getString(R.string.trk_current), file.getName());
				editor.commit();
				trackWriter = new BufferedWriter(new FileWriter(file, true));
				if (needsHeader)
				{
					trackWriter.write("OziExplorer Track Point File Version 2.1\n" + "WGS 84\n" + "Altitude is in Feet\n" + "Reserved 3\n" +
					// Field 1 : always zero (0)
					// Field 2 : width of track plot line on screen - 1 or 2 are usually the best
					// Field 3 : track color (RGB)
					// Field 4 : track description (no commas allowed)
					// Field 5 : track skip value - reduces number of track points plotted, usually set to 1
					// Field 6 : track type - 0 = normal , 10 = closed polygon , 20 = Alarm Zone
					// Field 7 : track fill style - 0 =bsSolid; 1 =bsClear; 2 =bsBdiagonal; 3 =bsFdiagonal; 4 =bsCross;
					// 5 =bsDiagCross; 6 =bsHorizontal; 7 =bsVertical;
					// Field 8 : track fill color (RGB)
							"0,2," + OziExplorerFiles.rgb2bgr(color) + ",Androzic Current Track " + addon + " ,0,0\n" + "0\n");
					needsHeader = false;
				}
			}
			else
			{
				errorTime = System.currentTimeMillis();
				updateNotification();
				return;
			}
		}
		catch (IOException e)
		{
			Log.e(TAG, "createFile", e);
			errorTime = System.currentTimeMillis();
			updateNotification();
			return;
		}
	}

	public void addPoint(boolean continous, double latitude, double longitude, double altitude, float speed, long time)
	{
		long interval = fileInterval > 0 ? time % fileInterval : -1;
		if (interval == 0 || trackWriter == null)
		{
			createFile(time);
		}
		try
		{
			// Field 1 : Latitude - decimal degrees.
			// Field 2 : Longitude - decimal degrees.
			// Field 3 : Code - 0 if normal, 1 if break in track line
			// Field 4 : Altitude in feet (-777 if not valid)
			// Field 5 : Date - see Date Format below, if blank a preset date will be used
			// Field 6 : Date as a string
			// Field 7 : Time as a string
			// Note that OziExplorer reads the Date/Time from field 5, the date and time in fields 6 & 7 are ignored.

			// -27.350436, 153.055540,1,-777,36169.6307194, 09-Jan-99, 3:08:14

			trackWriter.write(coordFormat.format(latitude) + "," + coordFormat.format(longitude) + ",");
			if (continous)
				trackWriter.write("0");
			else
				trackWriter.write("1");
			trackWriter.write("," + String.valueOf(Math.round(altitude * 3.2808399)));
			trackWriter.write("," + String.valueOf(TDateTime.toDateTime(time)));
			trackWriter.write("\n");
		}
		catch (Exception e)
		{
			Log.e(TAG, "addPoint", e);
			errorTime = System.currentTimeMillis();
			updateNotification();
			closeFile();
		}
	}

	private void writeLocation(final Location loc, final boolean continous)
	{
		Log.d(TAG, "Fix needs writing");
		lastWritenLocation = loc;
		distanceFromLastWriting = 0;
		addPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getTime());

		for (ITrackingListener callback : trackingCallbacks)
		{
			callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getTime());
		}

		final int n = trackingRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++)
		{
			final ITrackingCallback callback = trackingRemoteCallbacks.getBroadcastItem(i);
			try
			{
				callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getTime());
			}
			catch (RemoteException e)
			{
				Log.e(TAG, "Point broadcast error", e);
			}
		}
		trackingRemoteCallbacks.finishBroadcast();
	}

	private void writeTrack(Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
	{
		boolean needsWrite = false;
		if (lastLocation != null)
		{
			distanceFromLastWriting += loc.distanceTo(lastLocation);
		}
		if (lastWritenLocation != null)
			timeFromLastWriting = loc.getTime() - lastWritenLocation.getTime();

		if (lastLocation == null || lastWritenLocation == null || !isContinous || timeFromLastWriting > maxTime || distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)
		{
			needsWrite = true;
		}

		lastLocation = loc;

		if (needsWrite)
		{
			writeLocation(loc, isContinous);
			isContinous = continous;
		}
	}

	private void tearTrack()
	{
		if (lastLocation != null && (lastWritenLocation == null || !lastLocation.toString().equals(lastWritenLocation.toString())))
			writeLocation(lastLocation, isContinous);
		isContinous = false;
	}

	private void updateLocation()
	{
		final Location location = lastKnownLocation;
		final boolean continous = isContinous;
		final boolean geoid = !Float.isNaN(nmeaGeoidHeight);
		final float smoothspeed = smoothSpeed;
		final float avgspeed = avgSpeed;

		final Handler handler = new Handler();

		if (trackingEnabled)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					writeTrack(location, continous, geoid, smoothspeed, avgspeed);
				}
			});
		}
		for (final ILocationListener callback : locationCallbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed);
				}
			});
		}
		final int n = locationRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++)
		{
			final ILocationCallback callback = locationRemoteCallbacks.getBroadcastItem(i);
			try
			{
				callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed);
			}
			catch (RemoteException e)
			{
				Log.e(TAG, "Location broadcast error", e);
			}
		}
		locationRemoteCallbacks.finishBroadcast();
		Log.d(TAG, "Location dispatched: " + (locationCallbacks.size() + n));
	}

	private void updateLocation(final ILocationListener callback)
	{
		if (!"unknown".equals(lastKnownLocation.getProvider()))
			callback.onLocationChanged(lastKnownLocation, isContinous, !Float.isNaN(nmeaGeoidHeight), smoothSpeed, avgSpeed);
	}

	private void updateProvider(final String provider, final boolean enabled)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider))
			updateNotification();
		final Handler handler = new Handler();
		for (final ILocationListener callback : locationCallbacks)
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
		final int n = locationRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++)
		{
			final ILocationCallback callback = locationRemoteCallbacks.getBroadcastItem(i);
			try
			{
				if (enabled)
					callback.onProviderEnabled(provider);
				else
					callback.onProviderDisabled(provider);
			}
			catch (RemoteException e)
			{
				Log.e(TAG, "Provider broadcast error", e);
			}
		}
		locationRemoteCallbacks.finishBroadcast();
		Log.d(TAG, "Provider status dispatched: " + (locationCallbacks.size() + n));
	}

	private void updateProvider(final ILocationListener callback)
	{
		if (gpsStatus == GPS_OFF)
			callback.onProviderDisabled(LocationManager.GPS_PROVIDER);
		else
			callback.onProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	private void updateGpsStatus(final int status, final int fsats, final int tsats)
	{
		gpsStatus = status;
		updateNotification();
		final Handler handler = new Handler();
		for (final ILocationListener callback : locationCallbacks)
		{
			handler.post(new Runnable() {
				@Override
				public void run()
				{
					callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, status, fsats, tsats);
				}
			});
		}
		final int n = locationRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++)
		{
			final ILocationCallback callback = locationRemoteCallbacks.getBroadcastItem(i);
			try
			{
				callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, status, fsats, tsats);
			}
			catch (RemoteException e)
			{
				Log.e(TAG, "Status broadcast error", e);
			}
		}
		locationRemoteCallbacks.finishBroadcast();
		Log.d(TAG, "GPS status dispatched: " + (locationCallbacks.size() + n));
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

			if (!Float.isNaN(nmeaGeoidHeight))
			{
				location.setAltitude(location.getAltitude() + nmeaGeoidHeight);
			}

			if (justStarted)
			{
				justStarted = prevSpeed == 0;
			}
			else if (lastKnownLocation.getSpeed() > 0)
			{
				// filter speed outrages
				double a = 2 * 9.8 * (lastLocationMillis - prevLocationMillis) / 1000;
				if (Math.abs(lastKnownLocation.getSpeed() - prevSpeed) > a)
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
		String sentenceId = tokens[0].length() > 5 ? tokens[0].substring(3, 6) : "";

		try
		{
			if (sentenceId.equals("GGA") && tokens.length > 11)
			{
				// String time = tokens[1];
				// String latitude = tokens[2];
				// String latitudeHemi = tokens[3];
				// String longitude = tokens[4];
				// String longitudeHemi = tokens[5];
				// String fixQuality = tokens[6];
				// String numSatellites = tokens[7];
				// String horizontalDilutionOfPrecision = tokens[8];
				// String altitude = tokens[9];
				// String altitudeUnits = tokens[10];
				String heightOfGeoid = tokens[11];
				if (!"".equals(heightOfGeoid))
					nmeaGeoidHeight = Float.parseFloat(heightOfGeoid);
				// String heightOfGeoidUnits = tokens[12];
				// String timeSinceLastDgpsUpdate = tokens[13];
			}
			else if (sentenceId.equals("GSA") && tokens.length > 17)
			{
				// String selectionMode = tokens[1]; // m=manual, a=auto 2d/3d
				// String mode = tokens[2]; // 1=no fix, 2=2d, 3=3d
				@SuppressWarnings("unused")
				String pdop = tokens[15];
				String hdop = tokens[16];
				String vdop = tokens[17];
				if (!"".equals(hdop))
					HDOP = Float.parseFloat(hdop);
				if (!"".equals(vdop))
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
			switch (status)
			{
				case LocationProvider.TEMPORARILY_UNAVAILABLE:
				case LocationProvider.OUT_OF_SERVICE:
					tearTrack();
					updateNotification();
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
				tearTrack();
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
					tearTrack();
					updateGpsStatus(GPS_SEARCHING, fSats, tSats);
				}
				break;
		}
	}

	public class LocalBinder extends Binder implements ILocationService
	{
		@Override
		public void registerLocationCallback(ILocationListener callback)
		{
			updateProvider(callback);
			updateLocation(callback);
			locationCallbacks.add(callback);
		}

		@Override
		public void unregisterLocationCallback(ILocationListener callback)
		{
			locationCallbacks.remove(callback);
		}

		@Override
		public void registerTrackingCallback(com.androzic.location.ITrackingListener callback)
		{
			trackingCallbacks.add(callback);
		}

		@Override
		public void unregisterTrackingCallback(com.androzic.location.ITrackingListener callback)
		{
			trackingCallbacks.remove(callback);
		}

		@Override
		public boolean isLocating()
		{
			return locationsEnabled;
		}

		@Override
		public boolean isTracking()
		{
			return trackingEnabled;
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
