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
import java.util.Locale;

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
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.androzic.MapActivity;
import com.androzic.R;
import com.androzic.location.ILocationCallback;
import com.androzic.location.ILocationRemoteService;
import com.androzic.location.LocationService;
import com.androzic.util.TDateTime;

public class TrackingService extends Service implements OnSharedPreferenceChangeListener
{
	private static final int ANDROZIC_NOTIFICATION_ID = 2416;
	
    final RemoteCallbackList<ITrackingCallback> callbacks = new RemoteCallbackList<ITrackingCallback>();

	final static DecimalFormat coordFormat = new DecimalFormat("* ###0.000000", new DecimalFormatSymbols(Locale.ENGLISH));

	private ILocationRemoteService remoteService = null;

	private BufferedWriter trackWriter = null;
	private boolean needsHeader = false;
	private int gpsStatus = -1;

	private NotificationManager notificationManager;
	private Notification notification;
	private PendingIntent contentIntent;
	
	private Location lastWritenLocation = null;
	private Location lastLocation = null;
	private double distanceFromLastWriting = 0;
	private long timeFromLastWriting = 0;

	private long minTime = 500; // half a second (default)
	private long maxTime = 300000; // 5 minutes
	private int minDistance = 3; // 3 meters (default)
	private String trackPath;
	private int fileInterval;

    private final ITrackingRemoteService.Stub binder = new ITrackingRemoteService.Stub()
    {
        public void registerCallback(ITrackingCallback cb)
        {
        	Log.d("ANDROZIC", "Register callback");
            if (cb != null) callbacks.register(cb);
        }
        public void unregisterCallback(ITrackingCallback cb)
        {
            if (cb != null) callbacks.unregister(cb);
        }
    };
    
    @Override
    public IBinder onBind(Intent intent)
    {
        if ("com.androzic.tracking".equals(intent.getAction()) || ITrackingRemoteService.class.getName().equals(intent.getAction()))
        {
            return binder;
        }
        else
        {
        	return null;
        }
    }
    
    @Override
	public void onCreate()
	{
		super.onCreate();

		String state = Environment.getExternalStorageState();
		if (! Environment.MEDIA_MOUNTED.equals(state))
		{
			Toast.makeText(getBaseContext(), getString(R.string.err_nosdcard), Toast.LENGTH_LONG).show();
			this.stopSelf();
			return;
		}

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_path));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_currentinterval));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		// we need this cause we run in separate thread
		registerReceiver(receiver, new IntentFilter("onSharedPreferenceChanged"));

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification();
		notification.when = 0;
	    notification.flags |= Notification.FLAG_ONGOING_EVENT;
	    contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK), 0);
	
		bindService(new Intent(ILocationRemoteService.class.getName()), connection, BIND_AUTO_CREATE);

	    Log.d("ANDROZIC", "TrackingService: service started");
	}

    private void closeFile()
    {
		if (trackWriter != null)
		{
			try
			{
				trackWriter.close();
			}
			catch (Exception ignore) { }
			trackWriter = null;
		}
    }

    private void createFile(long time)
    {
    	closeFile();
		try
		{
			File dir = new File(trackPath);
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
							  "0,2,16711680,Androzic Current Track   ,0,0\n" +
							  "0\n");
					needsHeader = false;
				}
			}
			else
			{
				Toast.makeText(getBaseContext(), getString(R.string.err_currentlog), Toast.LENGTH_LONG).show();
				stopSelf();
				return;
			}
		}
		catch (IOException e)
		{
			Log.e("ANDROZIC", e.toString(), e);
			Toast.makeText(getBaseContext(), getString(R.string.err_currentlog), Toast.LENGTH_LONG).show();
			stopSelf();
			return;
		}
    }
    
	private void setNotification(int status)
	{
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
		}
	}

    @Override
	public void onDestroy()
	{
		super.onDestroy();
		
    	unregisterReceiver(receiver);
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		if (remoteService != null)
		{
			try
			{
				remoteService.unregisterCallback(callback);
			}
			catch (RemoteException e)
			{
			}
			unbindService(connection);
		}

		if (notificationManager != null)
		{
			notificationManager.cancel(ANDROZIC_NOTIFICATION_ID);
		}

		closeFile();

        notificationManager = null;
		notification = null;
	    contentIntent = null;
	    
		Log.d("ANDROZIC", "TrackingService: service stopped");
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
			Toast.makeText(getBaseContext(), getString(R.string.err_currentlog), Toast.LENGTH_LONG).show();
			Log.e("ANDROZIC", e.toString(), e);
			closeFile();
		}
	}
	
	private void writeLocation(final Location loc, final boolean continous)
	{
		Log.d("ANDROZIC", "TrackingService: fix needs writing");
		lastWritenLocation = loc;
		distanceFromLastWriting = 0;
		addPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getTime());

    	final int n = callbacks.beginBroadcast();
        for (int i=0; i<n; i++)
        {
			Log.d("ANDROZIC", "TrackingService: fix dispatched");
            final ITrackingCallback callback = callbacks.getBroadcastItem(i);
            try
            {
				callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getTime());
            } 
            catch (RemoteException e)
            {
				Log.d("ANDROZIC", "TrackingService: fix broadcast error: "+e.toString());
				e.printStackTrace();
            }
        }
        callbacks.finishBroadcast();
	}
	
	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			remoteService = ILocationRemoteService.Stub.asInterface(service);

			try
			{
				remoteService.registerCallback(callback);
				Log.d("ANDROZIC", "TrackingService: location service connected");
			}
			catch (RemoteException e)
			{
			}
		}

		public void onServiceDisconnected(ComponentName className)
		{
			remoteService = null;
			Log.d("ANDROZIC", "TrackingService: location service disconnected");
		}
	};

	private ILocationCallback callback = new ILocationCallback.Stub() {

		@Override
		public void onGpsStatusChanged(String provider, int status, int fsats, int tsats) throws RemoteException
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
    		    setNotification(status);
				switch (status)
				{
					case LocationService.GPS_OFF:
					case LocationService.GPS_SEARCHING:
	        			if (! lastLocation.toString().equals(lastWritenLocation.toString()))
	        				writeLocation(lastLocation, true);
				}
			}
		}

		@Override
		public void onLocationChanged(Location loc, boolean continous, float smoothspeed, float avgspeed) throws RemoteException
		{
			Log.d("ANDROZIC", "TrackingService: location arrived");

			boolean needsWrite = false;
			if (lastLocation != null)
			{
				distanceFromLastWriting += loc.distanceTo(lastLocation);
			}
			if (lastWritenLocation != null)
				timeFromLastWriting = loc.getTime() - lastWritenLocation.getTime();

			if (lastLocation == null ||
				lastWritenLocation == null ||
				! continous ||
				timeFromLastWriting > maxTime ||
				distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)
			{
					needsWrite = true;
			}

			lastLocation = loc;

			if (needsWrite)
			{
				writeLocation(loc, continous);
			}
		}

		@Override
		public void onProviderChanged(String provider) throws RemoteException
		{
		}

		@Override
		public void onProviderDisabled(String provider) throws RemoteException
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
				setNotification(LocationService.GPS_OFF);
		}

		@Override
		public void onProviderEnabled(String provider) throws RemoteException
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
				setNotification(LocationService.GPS_SEARCHING);
		}

		@Override
		public void onSensorChanged(float azimuth, float pitch, float roll) throws RemoteException
		{
		}
	};

    private BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("onSharedPreferenceChanged"))
            {
        		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(TrackingService.this);
        		onSharedPreferenceChanged(sharedPreferences, intent.getExtras().getString("key"));
            }
        }
    };

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_tracking_mintime).equals(key))
		{
			minTime = Integer.parseInt(sharedPreferences.getString(key, "500"));
		}
		else if (getString(R.string.pref_tracking_mindistance).equals(key))
		{
			minDistance = Integer.parseInt(sharedPreferences.getString(key, "5"));
		}
		else if (getString(R.string.pref_tracking_path).equals(key))
		{
			trackPath = sharedPreferences.getString(key, "");
		}
		else if (getString(R.string.pref_tracking_currentinterval).equals(key))
		{
			fileInterval = Integer.parseInt(sharedPreferences.getString(key, "0")) * 3600000;
			closeFile();
		}
	}
}
