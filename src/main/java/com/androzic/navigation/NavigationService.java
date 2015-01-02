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

package com.androzic.navigation;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.androzic.Androzic;
import com.androzic.HSIActivity;
import com.androzic.MainActivity;
import com.androzic.R;
import com.androzic.data.MapObject;
import com.androzic.data.Route;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.route.RouteDetails;
import com.androzic.util.Geo;

public class NavigationService extends BaseNavigationService implements OnSharedPreferenceChangeListener
{
    private static final String TAG = "Navigation";
	private static final int NOTIFICATION_ID = 24163;
	
	private Androzic application;
	
	private ILocationService locationService = null;
	protected Location lastKnownLocation;
	
	private PendingIntent contentIntent;
	
	private int routeProximity = 200;
	private boolean useTraverse = true;

	/**
	 * Active route waypoint
	 */
	public MapObject navWaypoint = null;
	/**
	 * Previous route waypoint
	 */
	public MapObject prevWaypoint = null;
	/**
	 * Active route
	 */
	public Route navRoute = null;

	public int navDirection = 0;
	/**
	 * Active route waypoint index
	 */
	public int navCurrentRoutePoint = -1;
	private double navRouteDistance = -1;

	/**
	 * Distance to active waypoint
	 */
	public int navProximity = 0;
	public double navDistance = 0.0;
	public double navBearing = 0.0;
	public long navTurn = 0;
	public double navVMG = 0.0;
	public int navETE = Integer.MAX_VALUE;
	public double navCourse = 0.0;
	public double navXTK = Double.NEGATIVE_INFINITY;

	private long tics = 0;
	private float[] vmgav = null;
	private double avvmg = 0.0;

	@Override
	public void onCreate()
	{
		application = Androzic.getApplication();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_proximity));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_traverse));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		Log.i(TAG, "Service started");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null)
		{
			Intent activity = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			String action = intent.getAction();
			if (action == null)
				return 0;
			Bundle extras = intent.getExtras();
			if (action.equals(NAVIGATE_MAPOBJECT))
			{
				MapObject mo = new MapObject();
				mo.name = extras.getString(EXTRA_NAME);
				mo.latitude = extras.getDouble(EXTRA_LATITUDE);
				mo.longitude = extras.getDouble(EXTRA_LONGITUDE);
				mo.proximity = extras.getInt(EXTRA_PROXIMITY);
				activity.putExtra(MainActivity.LAUNCH_ACTIVITY, HSIActivity.class);
				contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, activity, PendingIntent.FLAG_CANCEL_CURRENT);
				navigateTo(mo);
			}
			if (action.equals(NAVIGATE_MAPOBJECT_WITH_ID))
			{
				long id = extras.getLong(EXTRA_ID);
				MapObject mo = application.getMapObject(id);
				if (mo == null)
					return 0;
				activity.putExtra(MainActivity.LAUNCH_ACTIVITY, HSIActivity.class);
				contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, activity, PendingIntent.FLAG_CANCEL_CURRENT);
				navigateTo(mo);
			}
			if (action.equals(NAVIGATE_ROUTE))
			{
				int index = extras.getInt(EXTRA_ROUTE_INDEX);
				int dir = extras.getInt(EXTRA_ROUTE_DIRECTION, DIRECTION_FORWARD);
				int start = extras.getInt(EXTRA_ROUTE_START, -1);
				activity.putExtra(MainActivity.SHOW_FRAGMENT, RouteDetails.class);
				activity.putExtra("index", index);
				contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, activity, PendingIntent.FLAG_CANCEL_CURRENT);
				Route route = application.getRoute(index);
				if (route == null)
					return 0;
				navigateTo(route, dir);
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
			locationService.unregisterLocationCallback(locationListener);
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

		navProximity = routeProximity;
		navDistance = 0.0;
		navBearing = 0.0;
		navTurn = 0;
		navVMG = 0.0;
		navETE = Integer.MAX_VALUE;
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

	private void navigateTo(final MapObject waypoint)
	{
		clearNavigation();
		connect();
		startForeground(NOTIFICATION_ID, getNotification());

		vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		navWaypoint = waypoint;
		navProximity = navWaypoint.proximity > 0 ? navWaypoint.proximity : routeProximity;
		updateNavigationState(STATE_STARTED);
		if (lastKnownLocation != null)
			calculateNavigationStatus(lastKnownLocation, 0, 0);
	}

	private void navigateTo(final Route route, final int direction)
	{
		clearNavigation();
		connect();
		startForeground(NOTIFICATION_ID, getNotification());

		vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		navRoute = route;
		navDirection = direction;
		navCurrentRoutePoint = navDirection == 1 ? 1 : navRoute.length()-2;

		navWaypoint = navRoute.getWaypoint(navCurrentRoutePoint);
		prevWaypoint = navRoute.getWaypoint(navCurrentRoutePoint - navDirection);
		navProximity = navWaypoint.proximity > 0 ? navWaypoint.proximity : routeProximity;
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
		navProximity = navWaypoint.proximity > 0 ? navWaypoint.proximity : routeProximity;
		navRouteDistance = -1;
		navCourse = prevWaypoint == null ? 0.0 : Geo.bearing(prevWaypoint.latitude, prevWaypoint.longitude, navWaypoint.latitude, navWaypoint.longitude);
		updateNavigationState(STATE_NEXTWPT);
	}

	public MapObject getNextRouteWaypoint()
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
		navProximity = navWaypoint.proximity > 0 ? navWaypoint.proximity : routeProximity;
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
		navProximity = navWaypoint.proximity > 0 ? navWaypoint.proximity : routeProximity;
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
	
	public int navRouteCurrentIndex()
	{
		return navDirection == DIRECTION_FORWARD ? navCurrentRoutePoint : navRoute.length() - navCurrentRoutePoint - 1;
	}
	
	/**
	 * Calculates distance between current route waypoint and last route waypoint.
	 * @return distance left
	 */
	public double navRouteDistanceLeft()
	{
		if (navRouteDistance < 0)
		{
			navRouteDistance = navRouteDistanceLeftTo(navRoute.length() - 1);
		}
		return navRouteDistance;
	}

	/**
	 * Calculates distance between current route waypoint and route waypoint with specified index.
	 * Method honors navigation direction.
	 * @param index
	 * @return distance left
	 */
	public double navRouteDistanceLeftTo(int index)
	{
		int current = navRouteCurrentIndex();
		int progress = index - current;
		
		if (progress <= 0)
			return 0.0;
		
		double distance = 0.0;
		if (navDirection == DIRECTION_FORWARD)
			distance = navRoute.distanceBetween(navCurrentRoutePoint, index);
		if (navDirection == DIRECTION_REVERSE)
			distance = navRoute.distanceBetween(navRoute.length() - index - 1, navCurrentRoutePoint);

		return distance;
	}
	
	public int navRouteWaypointETE(int index)
	{
		if (index == 0)
			return 0;
		int ete = Integer.MAX_VALUE;
		if (avvmg > 0)
		{
			int i = navDirection == DIRECTION_FORWARD ? index : navRoute.length() - index - 1;
			int j = i - navDirection;
			MapObject w1 = navRoute.getWaypoint(i);
			MapObject w2 = navRoute.getWaypoint(j);
			double distance = Geo.distance(w1.latitude, w1.longitude, w2.latitude, w2.longitude);
			ete = (int) Math.round(distance / avvmg / 60);
		}
		return ete;
	}

	/**
	 * Calculates route ETE.
	 * @param distance route distance
	 * @return route ETE
	 */
	public int navRouteETE(double distance)
	{
		int eta = Integer.MAX_VALUE;
		if (avvmg > 0)
		{
			eta = (int) Math.round(distance / avvmg / 60);
		}
		return eta;
	}

	public int navRouteETETo(int index)
	{
		double distance = navRouteDistanceLeftTo(index);
		if (distance <= 0.0)
			return 0;

		return navRouteETE(distance);
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

		int ete = Integer.MAX_VALUE;
		if (avvmg > 0)
			ete = (int) Math.round(distance / avvmg / 60);
		
		double xtk = Double.NEGATIVE_INFINITY;

		if (navRoute != null)
		{
			boolean hasNext = hasNextRouteWaypoint();
			if (distance < navProximity)
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
						MapObject nextWpt = getNextRouteWaypoint();
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
	
	private Notification getNotification()
	{
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setContentIntent(contentIntent);
		builder.setSmallIcon(R.drawable.ic_stat_navigation);
		builder.setContentTitle(getText(R.string.notif_nav_short));
		if (navWaypoint != null)
		{
			builder.setWhen(System.currentTimeMillis());
			builder.setContentText(String.format((String) getText(R.string.notif_nav_to), navWaypoint.name));
		}
		else
		{
			builder.setWhen(0);
			builder.setContentText(getText(R.string.notif_nav_started));
		}
		builder.setGroup("androzic");
		builder.setCategory(NotificationCompat.CATEGORY_SERVICE);
		builder.setPriority(NotificationCompat.PRIORITY_LOW);
		builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		builder.setColor(getResources().getColor(R.color.theme_accent_color));
		return builder.build();
	}

	private void updateNavigationState(final int state)
	{
		if (state != STATE_STOPED && state != STATE_REACHED)
		{
			NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_ID, getNotification());
		}
		sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state));
		Log.d(TAG, "State dispatched");
	}

	private void updateNavigationStatus()
	{
		sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATUS));
		Log.d(TAG, "Status dispatched");
	}
	
	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			locationService = (ILocationService) service;
			locationService.registerLocationCallback(locationListener);
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
	};
}
