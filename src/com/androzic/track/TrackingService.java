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

package com.androzic.track;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.R;

import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.TDateTime;

public class TrackingService extends Service implements OnSharedPreferenceChangeListener
{
    private static final String TAG = "Tracking";
    private static final int NOTIFICATION_ID = 24162;

	public static final String ENABLE_TRACK = "enableTrack";
	public static final String DISABLE_TRACK = "disableTrack";
	
	public static final String BROADCAST_TRACKING_STATUS = "com.androzic.trackingStatusChanged";

    private final RemoteCallbackList<ITrackingCallback> remoteCallbacks = new RemoteCallbackList<ITrackingCallback>();
	private final Set<ITrackingListener> callbacks = new HashSet<ITrackingListener>();

	private final static DecimalFormat coordFormat = new DecimalFormat("* ###0.000000", new DecimalFormatSymbols(Locale.ENGLISH));

	private ILocationService locationService = null;
	private final Binder binder = new LocalBinder();

	private BufferedWriter trackWriter = null;
	private boolean needsHeader = false;
	private boolean errorState = false;
	private boolean trackingEnabled = false;
	private boolean isSuspended = false;

	private Notification notification;
	private PendingIntent contentIntent;
	
	private Location lastWritenLocation = null;
	private Location lastLocation = null;
	private double distanceFromLastWriting = 0;
	private long timeFromLastWriting = 0;
	private boolean isContinous;

	private long minTime = 2000; // 2 seconds (default)
	private long maxTime = 300000; // 5 minutes
	private int minDistance = 3; // 3 meters (default)
	private int color = Color.RED;
	private int fileInterval;

    @Override
	public void onCreate()
	{
		super.onCreate();

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_currentcolor));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_currentinterval));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		notification = new Notification();
	    contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK), 0);
	
		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_LOCATING_STATUS));
		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && intent.getAction() != null)
		{
			if (intent.getAction().equals(ENABLE_TRACK) && ! trackingEnabled && ! isSuspended)
			{
	    		prepareNormalNotification();
				trackingEnabled = true;
				isSuspended = true;
				isContinous = false;
				connect();
			}
			if (intent.getAction().equals(DISABLE_TRACK) && trackingEnabled)
			{
				trackingEnabled = false;
				isSuspended = false;
				stopForeground(true);
				disconnect();
				closeFile();
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
		}
		return START_REDELIVER_INTENT | START_STICKY;
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
				Log.e(TAG, "Ex", e);
				showErrorNotification();
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
			File dir = new File(application.trackPath);
			if (! dir.exists())
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
			File file = new File(dir, "myTrack"+addon+".plt");
			if (! file.exists())
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
					trackWriter.write("OziExplorer Track Point File Version 2.1\n" +
							  "WGS 84\n" +
							  "Altitude is in Feet\n" +
							  "Reserved 3\n" +
								// Field 1 : always zero (0)
								// Field 2 : width of track plot line on screen - 1 or 2 are usually the best
								// Field 3 : track color (RGB)
								// Field 4 : track description (no commas allowed)
								// Field 5 : track skip value - reduces number of track points plotted, usually set to 1
								// Field 6 : track type - 0 = normal , 10 = closed polygon , 20 = Alarm Zone
								// Field 7 : track fill style - 0 =bsSolid; 1 =bsClear; 2 =bsBdiagonal; 3 =bsFdiagonal; 4 =bsCross;
								// 5 =bsDiagCross; 6 =bsHorizontal; 7 =bsVertical;
								// Field 8 : track fill color (RGB)
							  "0,2," +
							  OziExplorerFiles.rgb2bgr(color) +
							  ",Androzic Current Track " + addon +
							  " ,0,0\n" +
							  "0\n");
					needsHeader = false;
				}
			}
			else
			{
				showErrorNotification();
				return;
			}
		}
		catch (IOException e)
		{
			Log.e(TAG, e.toString(), e);
			showErrorNotification();
			return;
		}
    }
    
    @Override
	public void onDestroy()
	{
		super.onDestroy();
		
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		closeFile();

		unregisterReceiver(broadcastReceiver);
		disconnect();
		stopForeground(true);
		
		notification = null;
	    contentIntent = null;
	    
	    Log.i(TAG, "Service stopped");
	}
    
    private void connect()
    {
		bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
    }
    
    private void disconnect()
    {
		if (locationService != null)
		{
			locationService.unregisterCallback(locationListener);
			unbindService(locationConnection);
			locationService = null;
		}
    }
    
    private void doStart()
    {
		if (isSuspended)
		{
			if (trackingEnabled)
			{
				startForeground(NOTIFICATION_ID, notification);
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
			isSuspended = false;
		}
    }

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.e(TAG, "Broadcast: " + action);
			if (action.equals(LocationService.BROADCAST_LOCATING_STATUS))
			{
				if (locationService != null && locationService.isLocating())
				{
					doStart();
				}
				else if (trackingEnabled)
				{
					stopForeground(true);
					closeFile();
					isSuspended = true;
				}
			}
		}
	};

    private void prepareNormalNotification()
    {
		notification.when = 0;
		notification.icon = R.drawable.ic_stat_track;
	    notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_trk_short), getText(R.string.notif_trk_started), contentIntent);
		errorState = false;
    }
    
    private void showErrorNotification()
    {
    	if (errorState)
    		return;
    	
		notification.when = System.currentTimeMillis();
		notification.defaults |= Notification.DEFAULT_SOUND;
		/*
		 * Red icon (white): saturation +100, lightness -40
		 * Red icon (grey): saturation +100, lightness 0
		 */
		notification.icon = R.drawable.ic_stat_track_error;
		notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_trk_short), getText(R.string.err_currentlog), contentIntent);
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, notification);
		
		errorState = true;
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
			//Field 1 : Latitude - decimal degrees.
			//Field 2 : Longitude - decimal degrees.
			//Field 3 : Code - 0 if normal, 1 if break in track line
			//Field 4 : Altitude in feet (-777 if not valid)
			//Field 5 : Date - see Date Format below, if blank a preset date will be used
			//Field 6 : Date as a string
			//Field 7 : Time as a string
			// Note that OziExplorer reads the Date/Time from field 5, the date and time in fields 6 & 7 are ignored.

			//-27.350436, 153.055540,1,-777,36169.6307194, 09-Jan-99, 3:08:14 
			
			trackWriter.write(coordFormat.format(latitude)+","+coordFormat.format(longitude)+",");
			if (continous)
				trackWriter.write("0");
			else
				trackWriter.write("1");
			trackWriter.write(","+String.valueOf(Math.round(altitude * 3.2808399)));
			trackWriter.write(","+String.valueOf(TDateTime.toDateTime(time)));
			trackWriter.write("\n");
		}
		catch (Exception e)
		{
			showErrorNotification();
			closeFile();
		}
	}
	
	private void writeLocation(final Location loc, final boolean continous)
	{
		Log.d(TAG, "Fix needs writing");
		lastWritenLocation = loc;
		distanceFromLastWriting = 0;
		addPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getTime());

		for (ITrackingListener callback : callbacks)
		{
			callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getTime());			
		}
		
    	final int n = remoteCallbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
            final ITrackingCallback callback = remoteCallbacks.getBroadcastItem(i);
            try
            {
				callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getTime());
            } 
            catch (RemoteException e)
            {
            	Log.e(TAG, "Point broadcast error", e);
            }
        }
        remoteCallbacks.finishBroadcast();
	}
	
	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			locationService = (ILocationService) service;
			locationService.registerCallback(locationListener);
	    	if (locationService.isLocating())
	    		doStart();
			Log.i(TAG, "Location service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			locationService = null;
			Log.i(TAG, "Location service disconnected");
		}
	};

	private ILocationListener locationListener = new ILocationListener()
	{
		@Override
		public void onGpsStatusChanged(String provider, int status, int fsats, int tsats)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				switch (status)
				{
					case LocationService.GPS_OFF:
					case LocationService.GPS_SEARCHING:
	        			if (lastLocation != null && (lastWritenLocation == null || ! lastLocation.toString().equals(lastWritenLocation.toString())))
	        				writeLocation(lastLocation, isContinous);
	        			isContinous = false;
				}
			}
		}

		@Override
		public void onLocationChanged(Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
		{
			Log.d(TAG, "Location arrived");

			boolean needsWrite = false;
			if (lastLocation != null)
			{
				distanceFromLastWriting += loc.distanceTo(lastLocation);
			}
			if (lastWritenLocation != null)
				timeFromLastWriting = loc.getTime() - lastWritenLocation.getTime();

			if (lastLocation == null ||
				lastWritenLocation == null ||
				! isContinous ||
				timeFromLastWriting > maxTime ||
				distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)
			{
					needsWrite = true;
			}

			lastLocation = loc;

			if (needsWrite && ! isSuspended)
			{
				writeLocation(loc, isContinous);
				isContinous = continous;
			}
		}

		@Override
		public void onProviderChanged(String provider)
		{
		}

		@Override
		public void onProviderDisabled(String provider)
		{
		}

		@Override
		public void onProviderEnabled(String provider)
		{
		}

		@Override
		public void onSensorChanged(float azimuth, float pitch, float roll)
		{
		}
	};

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_tracking_currentcolor).equals(key))
		{
			color = sharedPreferences.getInt(key, getResources().getColor(R.color.currenttrack));
		}
		else if (getString(R.string.pref_tracking_mintime).equals(key))
		{
			minTime = Integer.parseInt(sharedPreferences.getString(key, "500"));
		}
		else if (getString(R.string.pref_tracking_mindistance).equals(key))
		{
			minDistance = Integer.parseInt(sharedPreferences.getString(key, "5"));
		}
		else if (getString(R.string.pref_tracking_currentinterval).equals(key))
		{
			fileInterval = Integer.parseInt(sharedPreferences.getString(key, "0")) * 3600000;
			closeFile();
		}
		else if (getString(R.string.pref_folder_track).equals(key))
		{
			closeFile();
		}
	}

    private final ITrackingRemoteService.Stub remoteBinder = new ITrackingRemoteService.Stub()
    {
        public void registerCallback(ITrackingCallback cb)
        {
        	Log.i(TAG, "Register callback");
            if (cb != null) remoteCallbacks.register(cb);
        }
        public void unregisterCallback(ITrackingCallback cb)
        {
            if (cb != null) remoteCallbacks.unregister(cb);
        }
    };
    
    @Override
    public IBinder onBind(Intent intent)
    {
        if ("com.androzic.tracking".equals(intent.getAction()) || ITrackingRemoteService.class.getName().equals(intent.getAction()))
        {
            return remoteBinder;
        }
        else
        {
        	return binder;
        }
    }
    
	public class LocalBinder extends Binder implements ITrackingService
	{
		@Override
		public void registerCallback(ITrackingListener callback)
		{
			callbacks.add(callback);
		}

		@Override
		public void unregisterCallback(ITrackingListener callback)
		{
			callbacks.remove(callback);
		}

		@Override
		public boolean isTracking()
		{
			return trackingEnabled;
		}
	}
}
