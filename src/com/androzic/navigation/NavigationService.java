package com.androzic.navigation;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.util.Geo;

public class NavigationService extends Service implements OnSharedPreferenceChangeListener
{
    private static final String TAG = "Navigation";
	private static final int NOTIFICATION_ID = 24163;
	
    public static final String NAVIGATE_WAYPOINT = "navigateWaypoint";
	public static final String NAVIGATE_ROUTE = "navigateRoute";
	
	public static final String BROADCAST_NAVIGATION_STATUS = "com.androzic.navigationStatusChanged";
	public static final String BROADCAST_NAVIGATION_STATE = "com.androzic.navigationStateChanged";
	
	public static final int STATE_STARTED = 1;
	public static final int STATE_NEXTWPT = 2;
	public static final int STATE_REACHED = 3;
	public static final int STATE_STOPED = 4;
	
	public static final int DIRECTION_FORWARD =  1;
	public static final int DIRECTION_REVERSE = -1;

	private Androzic application;
	
	private ILocationService locationService = null;
//	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();
	protected Location lastKnownLocation;
	
	private Notification notification;
	private PendingIntent contentIntent;
	
	public int routeProximity = 200;
	private boolean useTraverse = true;

	public Waypoint navWaypoint = null;
	public Waypoint prevWaypoint = null;
	public Route navRoute = null;

	public int navDirection = 0;
	public int navCurrentRoutePoint = -1;
	private double navRouteDistance = -1;

	public double navDistance = 0.0;
	public double navBearing = 0.0;
	public long navTurn = 0;
	public double navVMG = 0.0;
	public int navETE = 0;
	public double navCourse = 0.0;
	public double navXTK = Double.NEGATIVE_INFINITY;

	private long tics = 0;
	private float[] vmgav = null;
	private double avvmg = 0.0;

	@Override
	public void onCreate()
	{
		application = (Androzic) getApplication();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_proximity));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_traverse));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		notification = new Notification();
		notification.when = 0;
		contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		notification.icon = R.drawable.ic_stat_navigation;
		notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_nav_short), getText(R.string.notif_nav_started), contentIntent);

		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null)
		{
			Bundle extras = intent.getExtras();
			if (intent.getAction().equals(NAVIGATE_WAYPOINT))
			{
				int index = extras.getInt("index");
				navigateTo(index);
			}
			if (intent.getAction().equals(NAVIGATE_ROUTE))
			{
				int index = extras.getInt("index");
				int dir = extras.getInt("direction", DIRECTION_FORWARD);
				int start = extras.getInt("start", -1);
				navigateTo(application.getRoute(index), dir);
				if (start != -1)
					setRouteWaypoint(start);
			}
		}
		return START_STICKY;
	}
	
	@Override
	public void onDestroy()
	{
		disconnect();
		clearNavigation();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
		Log.i(TAG, "Service stopped");
	}

	private final IBinder binder = new LocalBinder();

	public class LocalBinder extends Binder
	{
		public NavigationService getService()
		{
			return NavigationService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_navigation_proximity).equals(key))
		{
			routeProximity = Integer.parseInt(sharedPreferences.getString(key, getString(R.string.def_navigation_proximity)));
		}
		if (getString(R.string.pref_navigation_traverse).equals(key))
		{
			useTraverse = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_navigation_traverse));
		}
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
	
	public void stopNavigation()
	{
		clearNavigation();
		updateNavigationState(STATE_STOPED);
		stopForeground(true);
		disconnect();
	}
	
	private void clearNavigation()
	{
		navWaypoint = null;
		prevWaypoint = null;
		navRoute = null;

		navDirection = 0;
		navCurrentRoutePoint = -1;		

		navDistance = 0.0;
		navBearing = 0.0;
		navTurn = 0;
		navVMG = 0.0;
		navETE = 0;
		navCourse = 0.0;
		navXTK = Double.NEGATIVE_INFINITY;

		vmgav = null;
		avvmg = 0.0;
	}

	public boolean isNavigating()
	{
		return navWaypoint != null;
	}

	public boolean isNavigatingViaRoute()
	{
		return navRoute != null;
	}

	public void navigateTo(final Waypoint waypoint)
	{
		clearNavigation();
		connect();
		startForeground(NOTIFICATION_ID, notification);

		vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		navWaypoint = waypoint;
		updateNavigationState(STATE_STARTED);
		if (lastKnownLocation != null)
			calculateNavigationStatus(lastKnownLocation, 0, 0);
	}

	public void navigateTo(final int index)
	{
		navigateTo(application.getWaypoints().get(index));
	}

	public void navigateTo(final Route route, final int direction)
	{
		clearNavigation();
		connect();
		startForeground(NOTIFICATION_ID, notification);

		vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		navRoute = route;
		navDirection = direction;
		navCurrentRoutePoint = navDirection == 1 ? 1 : navRoute.length()-2;

		navWaypoint = navRoute.getWaypoint(navCurrentRoutePoint);
		prevWaypoint = navRoute.getWaypoint(navCurrentRoutePoint - navDirection);
		navRouteDistance = -1;
		navCourse = Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
		updateNavigationState(STATE_STARTED);
		if (lastKnownLocation != null)
			calculateNavigationStatus(lastKnownLocation, 0, 0);
	}

	public void setRouteWaypoint(int waypoint)
	{
		navCurrentRoutePoint = waypoint;
		navWaypoint = navRoute.getWaypoint(navCurrentRoutePoint);
		int prev = navCurrentRoutePoint - navDirection;
		if (prev >= 0 && prev < navRoute.length())
			prevWaypoint = navRoute.getWaypoint(prev);
		else
			prevWaypoint = null;
		navRouteDistance = -1;
		navCourse = prevWaypoint == null ? 0.0 : Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
		updateNavigationState(STATE_NEXTWPT);
	}

	public Waypoint getNextRouteWaypoint()
	{
		try
		{
			return navRoute.getWaypoint(navCurrentRoutePoint + navDirection);
		}
		catch (IndexOutOfBoundsException e)
		{
			return null;
		}
	}

	public void nextRouteWaypoint() throws IndexOutOfBoundsException
	{
		navCurrentRoutePoint += navDirection;
		navWaypoint = navRoute.getWaypoint(navCurrentRoutePoint);
		prevWaypoint = navRoute.getWaypoint(navCurrentRoutePoint - navDirection);
		navRouteDistance = -1;
		navCourse = Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
		updateNavigationState(STATE_NEXTWPT);
	}

	public void prevRouteWaypoint() throws IndexOutOfBoundsException
	{
		navCurrentRoutePoint -= navDirection;
		navWaypoint = navRoute.getWaypoint(navCurrentRoutePoint);
		int prev = navCurrentRoutePoint - navDirection;
		if (prev >= 0 && prev < navRoute.length())
			prevWaypoint = navRoute.getWaypoint(prev);
		else
			prevWaypoint = null;
		navRouteDistance = -1;
		navCourse = prevWaypoint == null ? 0.0 : Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
		updateNavigationState(STATE_NEXTWPT);
	}

	public boolean hasNextRouteWaypoint()
	{
		if (navRoute == null)
			return false;
		boolean hasNext = false;
		if (navDirection == DIRECTION_FORWARD)
			hasNext = (navCurrentRoutePoint + navDirection) < navRoute.length();
		if (navDirection == DIRECTION_REVERSE)
			hasNext = (navCurrentRoutePoint + navDirection) >= 0;
		return hasNext;
	}

	public boolean hasPrevRouteWaypoint()
	{
		if (navRoute == null)
			return false;
		boolean hasPrev = false;
		if (navDirection == DIRECTION_FORWARD)
			hasPrev = (navCurrentRoutePoint - navDirection) >= 0;
		if (navDirection == DIRECTION_REVERSE)
			hasPrev = (navCurrentRoutePoint - navDirection) < navRoute.length();
		return hasPrev;
	}
	
	public double navRouteDistanceLeft()
	{
		if (! hasNextRouteWaypoint())
			return 0.0;
		if (navRouteDistance < 0)
		{
			if (navDirection == DIRECTION_FORWARD)
				navRouteDistance = navRoute.distanceBetween(navCurrentRoutePoint, navRoute.length() - 1);
			if (navDirection == DIRECTION_REVERSE)
				navRouteDistance = navRoute.distanceBetween(0, navCurrentRoutePoint);
		}
		return navRouteDistance;
	}
	
	private void calculateNavigationStatus(Location loc, float smoothspeed, float avgspeed)
	{
		double distance = Geo.distance(loc.getLatitude(), loc.getLongitude(), navWaypoint.latitude, navWaypoint.longitude);
		double bearing = Geo.bearing(loc.getLatitude(), loc.getLongitude(), navWaypoint.latitude, navWaypoint.longitude);
		double track = loc.getBearing();

		// turn
		long turn = Math.round(bearing - track);
		if (Math.abs(turn) > 180)
		{
			turn = turn - (long)(Math.signum(turn))*360;
		}
		
		// vmg
		double vmg = Geo.vmg(smoothspeed, Math.abs(turn));

		// ete
		float curavvmg = (float) Geo.vmg(avgspeed, Math.abs(turn));
		if (avvmg == 0.0 || tics % 10 == 0)
		{
			for (int i = vmgav.length - 1; i > 0; i--)
			{
				avvmg += vmgav[i];
				vmgav[i] = vmgav[i - 1];
			}
			avvmg += curavvmg;
			vmgav[0] = curavvmg;
			avvmg = avvmg / vmgav.length;
		}

		int ete;
		if (avvmg <= 0)
			ete = Integer.MAX_VALUE;
		else
			ete = (int) Math.round(distance / avvmg / 60);
		
		double xtk = Double.NEGATIVE_INFINITY;

		if (navRoute != null)
		{
			boolean hasNext = hasNextRouteWaypoint();
			if (distance < routeProximity)
			{
				if (hasNext)
				{
					nextRouteWaypoint();
					return;
				}
				else
				{
					updateNavigationState(STATE_REACHED);
					stopNavigation();
					return;
				}
			}

			if (prevWaypoint != null)
			{
				double dtk = Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
				xtk = Geo.xtk(distance, dtk, bearing);

				if (xtk == Double.NEGATIVE_INFINITY)
				{
					if (useTraverse && hasNext)
					{
						double cxtk2 = Double.NEGATIVE_INFINITY;
						Waypoint nextWpt = getNextRouteWaypoint();
						if (nextWpt != null)
						{
							double dtk2 = Geo.bearing(nextWpt.latitude, nextWpt.longitude, navWaypoint.latitude, navWaypoint.longitude);
							cxtk2 = Geo.xtk(0, dtk2, bearing);
						}

						if (cxtk2 != Double.NEGATIVE_INFINITY)
						{
							nextRouteWaypoint();
							return;
						}
					}
				}
			}
		}

		tics++;

		if (distance != navDistance || bearing != navBearing || turn != navTurn || vmg != navVMG || ete != navETE || xtk != navXTK)
		{
			navDistance = distance;
			navBearing = bearing;
			navTurn = turn;
			navVMG = vmg;
			navETE = ete;
			navXTK = xtk;
			updateNavigationStatus();
		}
	}
	
	private void updateNavigationState(final int state)
	{
		if (state != STATE_STOPED && state != STATE_REACHED)
		{
			notification.when = System.currentTimeMillis();
			String message = String.format((String) getText(R.string.notif_nav_to), navWaypoint.name);
			notification.setLatestEventInfo(getApplicationContext(), getText(R.string.notif_nav_short), message, contentIntent);
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_ID, notification);
		}
		sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state));
		Log.d(TAG, "State dispatched");
	}

	private void updateNavigationStatus()
	{
/*		executorThread.execute(new Runnable()
        {
			@Override
			public void run()
			{*/
				sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATUS));
/*			}
        });*/
		Log.d(TAG, "Status dispatched");
	}
	
	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			locationService = (ILocationService) service;
			locationService.registerCallback(locationListener);
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
		}

		@Override
		public void onLocationChanged(Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
		{
			Log.d(TAG, "Location arrived");
			lastKnownLocation = loc;
			
			if (navWaypoint != null)
				calculateNavigationStatus(loc, smoothspeed, avgspeed);			
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
}
