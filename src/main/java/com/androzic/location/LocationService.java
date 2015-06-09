/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
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

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
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

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.Splash;
import com.androzic.data.Track;

public class LocationService extends BaseLocationService implements LocationListener, NmeaListener, GpsStatus.Listener, OnSharedPreferenceChangeListener
{
	private static final String TAG = "Location";
	private static final int NOTIFICATION_ID = 24161;
	private static final boolean DEBUG_ERRORS = false;

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
	//FIXME Use this to permanently loose location
	private int gpsLocationTimeout = 120000;
	// Used for test purposes
	private static final boolean enableMockLocations = false;
	private Handler mockCallback = new Handler();

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

	private SQLiteDatabase trackDB = null;
	private boolean trackingEnabled = false;
	private String errorMsg = "";
	private long errorTime = 0;

	private Location lastWritenLocation = null;
	private Location lastLocation = null;
	private double distanceFromLastWriting = 0;
	private long timeFromLastWriting = 0;

	private long minTime = 2000; // 2 seconds (default)
	private long maxTime = 300000; // 5 minutes
	private int minDistance = 3; // 3 meters (default)

	private final Binder binder = new LocalBinder();
	private final RemoteCallbackList<ILocationCallback> locationRemoteCallbacks = new RemoteCallbackList<ILocationCallback>();
	private final Set<ILocationListener> locationCallbacks = new HashSet<ILocationListener>();
	private final RemoteCallbackList<ITrackingCallback> trackingRemoteCallbacks = new RemoteCallbackList<ITrackingCallback>();
	private final Set<ITrackingListener> trackingCallbacks = new HashSet<ITrackingListener>();

	@Override
	public void onCreate()
	{
		super.onCreate();

		lastKnownLocation = new Location("unknown");

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Location preferences
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_gpstimeout));
		// Tracking preferences
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance));

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent == null || intent.getAction() == null)
			return 0;

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
			sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
			if (trackingEnabled)
			{
				closeDatabase();
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
		}
		if (intent.getAction().equals(ENABLE_TRACK) && !trackingEnabled)
		{
			errorMsg = "";
			errorTime = 0;
			trackingEnabled = true;
			isContinous = false;
			openDatabase();
			sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
		}
		if (intent.getAction().equals(DISABLE_TRACK) && trackingEnabled)
		{
			trackingEnabled = false;
			closeDatabase();
			errorMsg = "";
			errorTime = 0;
			sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
		}
		updateNotification();

		return START_REDELIVER_INTENT | START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		disconnect();
		closeDatabase();
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
		if (getString(R.string.pref_loc_gpstimeout).equals(key))
		{
			gpsLocationTimeout = 1000 * sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_loc_gpstimeout));
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
		else if (getString(R.string.pref_folder_data).equals(key))
		{
			closeDatabase();
			openDatabase();
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
		if (enableMockLocations)
		{
			updateProvider(LocationManager.GPS_PROVIDER, true);
			mockCallback.post(sendMockLocation);
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
		if (enableMockLocations)
		{
			mockCallback.removeCallbacks(sendMockLocation);
			updateProvider(LocationManager.GPS_PROVIDER, false);
		}
	}

	@SuppressWarnings("unused")
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
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setComponent(new ComponentName(getApplicationContext(), Splash.class));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		builder.setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, intent, 0));
		builder.setContentTitle(getText(R.string.notif_loc_short));
		builder.setGroup("androzic");
		builder.setCategory(NotificationCompat.CATEGORY_SERVICE);
		builder.setPriority(NotificationCompat.PRIORITY_LOW);
		builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		builder.setColor(getResources().getColor(R.color.theme_accent_color));
		if (errorTime > 0 && DEBUG_ERRORS)
			builder.setContentText(errorMsg);
		else
			builder.setContentText(getText(msgId));
		builder.setOngoing(true);

		return builder.build();
	}

	private void updateNotification()
	{
		if (locationManager != null)
		{
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFICATION_ID, getNotification());
		}
	}

	private void openDatabase()
	{
		Androzic application = Androzic.getApplication();
		if (application.dataPath == null)
		{
			Log.e(TAG, "Data path is null");
			errorMsg = "Data path is null";
			errorTime = System.currentTimeMillis();
			updateNotification();
			return;
		}
		File dir = new File(application.dataPath);
		if (!dir.exists() && !dir.mkdirs())
		{
			Log.e(TAG, "Failed to create data folder");
			errorMsg = "Failed to create data folder";
			errorTime = System.currentTimeMillis();
			updateNotification();
			return;
		}
		File path = new File(dir, "myTrack.db");
		try
		{
			trackDB = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			Cursor cursor = trackDB.rawQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = 'track'", null);
			if (cursor.getCount() == 0)
			{
				trackDB.execSQL("CREATE TABLE track (_id INTEGER PRIMARY KEY, latitude REAL, longitude REAL, code INTEGER, elevation REAL, speed REAL, track REAL, accuracy REAL, datetime INTEGER)");
			}
			cursor.close();
		}
		catch (SQLiteException e)
		{
			trackDB = null;
			Log.e(TAG, "openDatabase", e);
			errorMsg = "Failed to open DB";
			errorTime = System.currentTimeMillis();
			updateNotification();
		}
	}

	private void closeDatabase()
	{
		if (trackDB != null)
		{
			trackDB.close();
			trackDB = null;
		}
	}

	public Track getTrack()
	{
		return getTrack(0);
	}
	
	public Track getTrack(long limit)
	{
		if (trackDB == null)
			openDatabase();
		Track track = new Track();
		if (trackDB == null)
			return track;
		String limitStr = limit > 0 ? " LIMIT " + limit : "";
		Cursor cursor = trackDB.rawQuery("SELECT * FROM track ORDER BY _id DESC" + limitStr, null);
		for (boolean hasItem = cursor.moveToLast(); hasItem; hasItem = cursor.moveToPrevious())
		{
			double latitude = cursor.getDouble(cursor.getColumnIndex("latitude"));
			double longitude = cursor.getDouble(cursor.getColumnIndex("longitude"));
			double elevation = cursor.getDouble(cursor.getColumnIndex("elevation"));
			double speed = cursor.getDouble(cursor.getColumnIndex("speed"));
			double bearing = cursor.getDouble(cursor.getColumnIndex("track"));
			double accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"));
			int code = cursor.getInt(cursor.getColumnIndex("code"));
			long time = cursor.getLong(cursor.getColumnIndex("datetime"));
			track.addPoint(code == 0, latitude, longitude, elevation, speed, bearing, accuracy, time);
		}
		cursor.close();
		return track;
	}

	public Track getTrack(long start, long end)
	{
		if (trackDB == null)
			openDatabase();
		Track track = new Track();
		if (trackDB == null)
			return track;
		Cursor cursor = trackDB.rawQuery("SELECT * FROM track WHERE datetime >= ? AND datetime <= ? ORDER BY _id DESC", new String[] {String.valueOf(start), String.valueOf(end)});
		for (boolean hasItem = cursor.moveToLast(); hasItem; hasItem = cursor.moveToPrevious())
		{
			double latitude = cursor.getDouble(cursor.getColumnIndex("latitude"));
			double longitude = cursor.getDouble(cursor.getColumnIndex("longitude"));
			double elevation = cursor.getDouble(cursor.getColumnIndex("elevation"));
			double speed = cursor.getDouble(cursor.getColumnIndex("speed"));
			double bearing = cursor.getDouble(cursor.getColumnIndex("track"));
			double accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"));
			int code = cursor.getInt(cursor.getColumnIndex("code"));
			long time = cursor.getLong(cursor.getColumnIndex("datetime"));
			track.addPoint(code == 0, latitude, longitude, elevation, speed, bearing, accuracy, time);
		}
		cursor.close();
		return track;
	}

	public long getTrackStartTime()
	{
		long res = Long.MIN_VALUE;
		if (trackDB == null)
			openDatabase();
		if (trackDB == null)
			return res;
		Cursor cursor = trackDB.rawQuery("SELECT MIN(datetime) FROM track WHERE datetime > 0", null);
		if (cursor.moveToFirst())
			res = cursor.getLong(0);
		cursor.close();
		return res;
	}

	public long getTrackEndTime()
	{
		long res = Long.MAX_VALUE;
		if (trackDB == null)
			openDatabase();
		if (trackDB == null)
			return res;
		Cursor cursor = trackDB.rawQuery("SELECT MAX(datetime) FROM track", null);
		if (cursor.moveToFirst())
			res = cursor.getLong(0);
		cursor.close();
		return res;
	}

	public void clearTrack()
	{
		if (trackDB == null)
			openDatabase();
		if (trackDB != null)
			trackDB.execSQL("DELETE FROM track");
	}

	public void addPoint(boolean continous, double latitude, double longitude, double elevation, float speed, float bearing, float accuracy, long time)
	{
		if (trackDB == null)
		{
			openDatabase();
			if (trackDB == null)
				return;
		}

		ContentValues values = new ContentValues();
		values.put("latitude", latitude);
		values.put("longitude", longitude);
		values.put("code", continous ? 0 : 1);
		values.put("elevation", elevation);
		values.put("speed", speed);
		values.put("track", bearing);
		values.put("accuracy", accuracy);
		values.put("datetime", time);

		try
		{
			trackDB.insertOrThrow("track", null, values);
		}
		catch (SQLException e)
		{
			Log.e(TAG, "addPoint", e);
			errorMsg = e.getMessage();
			errorTime = System.currentTimeMillis();
			updateNotification();
			closeDatabase();
		}
	}

	private void writeLocation(final Location loc, final boolean continous)
	{
		Log.d(TAG, "Fix needs writing");
		lastWritenLocation = loc;
		distanceFromLastWriting = 0;
		addPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());

		for (ITrackingListener callback : trackingCallbacks)
		{
			callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());
		}

		final int n = trackingRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++)
		{
			final ITrackingCallback callback = trackingRemoteCallbacks.getBroadcastItem(i);
			try
			{
				callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());
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

		if (lastLocation == null || lastWritenLocation == null || !continous || timeFromLastWriting > maxTime || distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)
		{
			needsWrite = true;
		}

		lastLocation = loc;

		if (needsWrite)
			writeLocation(loc, continous);
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
		if (enableMockLocations)
			return;
		
		tics++;

		boolean sendUpdate = false;

		long time = SystemClock.elapsedRealtime();

		// Log.i(TAG, "Location arrived: "+location.toString());

		Log.d(TAG, "Fix arrived");

		long prevLocationMillis = lastLocationMillis;
		float prevSpeed = lastKnownLocation.getSpeed();
		float prevTrack = lastKnownLocation.getBearing();

		lastKnownLocation = location;

		if (lastKnownLocation.getSpeed() == 0 && prevTrack != 0)
		{
			lastKnownLocation.setBearing(prevTrack);
		}

		lastLocationMillis = time;
		sendUpdate = true;

		if (!Float.isNaN(nmeaGeoidHeight))
		{
			lastKnownLocation.setAltitude(lastKnownLocation.getAltitude() + nmeaGeoidHeight);
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

		if (sendUpdate)
			updateLocation();

		isContinous = true;
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
		if (enableMockLocations)
			return;

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
		if (enableMockLocations)
			return;

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

		@Override
		public Track getTrack()
		{
			return LocationService.this.getTrack();
		}

		@Override
		public Track getTrack(long start, long end)
		{
			return LocationService.this.getTrack(start, end);
		}

		@Override
		public void clearTrack()
		{
			LocationService.this.clearTrack();
		}

		@Override
		public long getTrackStartTime()
		{
			return LocationService.this.getTrackStartTime();
		}

		@Override
		public long getTrackEndTime()
		{
			return LocationService.this.getTrackEndTime();
		}
	}
	
	/**
	 * Mock location generator used for application testing. Locations are generated
	 * by logic required for particular test.
	 */
	final private Runnable sendMockLocation = new Runnable() {
		public void run()
		{
			mockCallback.postDelayed(this, 1000);

			updateGpsStatus(GPS_OK, 5, 25);

			lastKnownLocation.setProvider(LocationManager.GPS_PROVIDER);
			lastKnownLocation.setTime(System.currentTimeMillis());
			lastKnownLocation.setSpeed(20);
			lastKnownLocation.setBearing(323);
			lastKnownLocation.setAltitude(39);
			lastKnownLocation.setLatitude(34.865792);
			lastKnownLocation.setLongitude(32.351646);
			lastKnownLocation.setBearing((System.currentTimeMillis() / 166) % 360);
			//lastKnownLocation.setAltitude(169);
			//lastKnownLocation.setLatitude(55.852527);
			//lastKnownLocation.setLongitude(29.451150);
			nmeaGeoidHeight = 0;
			smoothSpeed = 19;
			avgSpeed = 14;

			updateLocation();
			isContinous = true;
		}
	};
}
