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

package com.androzic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.location.ILocationCallback;
import com.androzic.location.ILocationRemoteService;
import com.androzic.util.Geo;

public class NavigationService extends Service implements OnSharedPreferenceChangeListener
{
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
	
	private ILocationRemoteService remoteService = null;
	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();
	protected Location lastKnownLocation;
	
	int routeProximity = 200;
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
		
		bindService(new Intent(ILocationRemoteService.class.getName()), connection, BIND_AUTO_CREATE);
		Log.d("ANDROZIC", "NavigationService: service started");
	}

	@Override
	public void onStart(Intent intent, int startId)
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
	}

	@Override
	public void onDestroy()
	{
		if (remoteService != null)
		{
			try
			{
				remoteService.unregisterCallback(callback);
			}
			catch (RemoteException e)
			{
			}
		}
		unbindService(connection);
		
		clearNavigation();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
		Log.d("ANDROZIC", "NavigationService: service stopped");
	}

	private final IBinder binder = new LocalBinder();

	public class LocalBinder extends Binder
	{
		NavigationService getService()
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

	public void stopNavigation()
	{
		clearNavigation();
		updateNavigationState(STATE_STOPED);
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
					clearNavigation();
					updateNavigationState(STATE_REACHED);
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
		executorThread.execute(new Runnable()
        {
			@Override
			public void run()
			{
				sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state));
			}
        });
		Log.d("ANDROZIC", "NavigationService: state dispatched");
	}

	private void updateNavigationStatus()
	{
		executorThread.execute(new Runnable()
        {
			@Override
			public void run()
			{
				sendBroadcast(new Intent(BROADCAST_NAVIGATION_STATUS));
			}
        });
		Log.d("ANDROZIC", "NavigationService: status dispatched");
	}
	
	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			remoteService = ILocationRemoteService.Stub.asInterface(service);

			try
			{
				remoteService.registerCallback(callback);
				Log.d("ANDROZIC", "NavigationService: service connected");
			}
			catch (RemoteException e)
			{
			}
		}

		public void onServiceDisconnected(ComponentName className)
		{
			remoteService = null;
			Log.d("ANDROZIC", "NavigationService: service disconnected");
		}
	};
	
	private ILocationCallback callback = new ILocationCallback.Stub()
	{
		@Override
		public void onGpsStatusChanged(String provider, int status, int fsats, int tsats) throws RemoteException
		{
		}

		@Override
		public void onLocationChanged(Location loc, boolean continous, float smoothspeed, float avgspeed) throws RemoteException
		{
			Log.d("ANDROZIC", "NavigationService: location arrived");
			lastKnownLocation = loc;
			
			if (navWaypoint != null)
				calculateNavigationStatus(loc, smoothspeed, avgspeed);			
		}

		@Override
		public void onProviderChanged(String provider) throws RemoteException
		{
		}

		@Override
		public void onProviderDisabled(String provider) throws RemoteException
		{
		}

		@Override
		public void onProviderEnabled(String provider) throws RemoteException
		{
		}

		@Override
		public void onSensorChanged(float azimuth, float pitch, float roll) throws RemoteException
		{
		}
	};
}
