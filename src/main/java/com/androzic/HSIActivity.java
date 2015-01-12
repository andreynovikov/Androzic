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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.navigation.NavigationService;
import com.androzic.ui.view.HSIView;
import com.androzic.util.StringFormatter;

public class HSIActivity extends Activity
{
	private ILocationService locationService = null;
    private NavigationService navigationService;
    private HSIView hsiView;
	private TextView distanceValue;
	private TextView distanceUnit;
	private TextView bearingValue;
	private TextView bearingUnit;
	private TextView speedValue;
	private TextView speedUnit;
	private TextView trackValue;
	private TextView trackUnit;
	private TextView elevationValue;
	private TextView elevationUnit;
	private TextView vmgValue;
	private TextView vmgUnit;
	private TextView courseValue;
	private TextView courseUnit;
	private TextView xtkValue;
	private TextView xtkUnit;
	private TextView eteValue;
	private TextView eteUnit;

	private Androzic application;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.act_hsi);
        
        application = (Androzic) getApplication();

        hsiView = (HSIView) findViewById(R.id.hsiview);
		speedValue = (TextView) findViewById(R.id.speed);
		speedUnit = (TextView) findViewById(R.id.speedunit);
		trackValue = (TextView) findViewById(R.id.track);
		trackUnit = (TextView) findViewById(R.id.trackunit);
		elevationValue = (TextView) findViewById(R.id.elevation);
		elevationUnit = (TextView) findViewById(R.id.elevationunit);
		distanceValue = (TextView) findViewById(R.id.distance);
		distanceUnit = (TextView) findViewById(R.id.distanceunit);
		vmgValue = (TextView) findViewById(R.id.vmg);
		vmgUnit = (TextView) findViewById(R.id.vmgunit);
		xtkValue = (TextView) findViewById(R.id.xtk);
		xtkUnit = (TextView) findViewById(R.id.xtkunit);
		courseValue = (TextView) findViewById(R.id.course);
		courseUnit = (TextView) findViewById(R.id.courseunit);
		bearingValue = (TextView) findViewById(R.id.bearing);
		bearingUnit = (TextView) findViewById(R.id.bearingunit);
		eteValue = (TextView) findViewById(R.id.ete);
		eteUnit = (TextView) findViewById(R.id.eteunit);
    }

    
	@Override
	protected void onResume()
	{
		super.onResume();
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Resources resources = getResources();
		
		speedUnit.setText(StringFormatter.speedAbbr);
		vmgUnit.setText(StringFormatter.speedAbbr);
		elevationUnit.setText(StringFormatter.elevationAbbr);
		trackUnit.setText(StringFormatter.angleAbbr);
		bearingUnit.setText(StringFormatter.angleAbbr);
		courseUnit.setText(StringFormatter.angleAbbr);
		int proximity = Integer.parseInt(settings.getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity)));

		hsiView.setProximity(proximity);
		
        bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, NavigationService.class), navigationConnection, BIND_AUTO_CREATE);
		
		boolean lock = settings.getBoolean(getString(R.string.pref_wakelock), resources.getBoolean(R.bool.def_wakelock));
		if (lock)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (locationService != null)
		{
			locationService.unregisterLocationCallback(locationListener);
			unbindService(locationConnection);
			locationService = null;
		}
    	unregisterReceiver(navigationReceiver);
		unbindService(navigationConnection);
	}

	private void updateNavigationInfo(boolean full)
	{
		if (full)
		{
			float course = (float) application.fixDeclination(navigationService.navCourse);
			hsiView.setNavigating(navigationService.isNavigatingViaRoute() ? 2 : navigationService.isNavigating() ? 1 : 0);
			hsiView.setCourse(course);
			if (navigationService.isNavigatingViaRoute())
				courseValue.setText(StringFormatter.angleC(course));
			else
				courseValue.setText("--");
		}
		if (navigationService.isNavigating())
		{
			float bearing = (float) application.fixDeclination(navigationService.navBearing);
			hsiView.setBearing(bearing);
			hsiView.setXtk(navigationService.navXTK == Double.NEGATIVE_INFINITY ? 0 : (float) navigationService.navXTK);
			bearingValue.setText(StringFormatter.angleC(bearing));
			String[] dist = StringFormatter.distanceC(navigationService.navDistance);
			distanceValue.setText(dist[0]);
			distanceUnit.setText(dist[1]);
			if (navigationService.navXTK == Double.NEGATIVE_INFINITY)
			{
				xtkValue.setText("--");
				xtkUnit.setText("--");
			}
			else
			{
				String xtksym = navigationService.navXTK == 0 ? "" : navigationService.navXTK > 0 ? "R" : "L";
				String[] xtks = StringFormatter.distanceC(Math.abs(navigationService.navXTK));
				xtkValue.setText(xtks[0] + xtksym);
				xtkUnit.setText(xtks[1]);
			}
			vmgValue.setText(String.valueOf(StringFormatter.speedC(navigationService.navVMG)));
			String[] ete = StringFormatter.timeC(navigationService.navETE);
			eteValue.setText(ete[0]);
			eteUnit.setText(ete[1]);
		}
		else
		{
			bearingValue.setText("--");
			distanceValue.setText("--");
			distanceUnit.setText("--");
			xtkValue.setText("--");
			xtkUnit.setText("--");
			vmgValue.setText("--");
			eteValue.setText("--");
			eteUnit.setText("--");
		}
	}
	
	private ServiceConnection navigationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
            navigationService = ((NavigationService.LocalBinder)service).getService();
    		registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
    		registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));
    		Log.d("ANDROZIC","Navigation broadcast receiver registered");
			runOnUiThread(new Runnable() {
				public void run()
				{
					updateNavigationInfo(true);
				}
			});
        }

        public void onServiceDisconnected(ComponentName className)
        {
        	unregisterReceiver(navigationReceiver);
            navigationService = null;
        }
    };
    
    private BroadcastReceiver navigationReceiver = new BroadcastReceiver()
    {

		@Override
		public void onReceive(Context context, Intent intent)
		{
        	Log.e("ANDROZIC","Broadcast: "+intent.getAction());
            if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATE))
            {
            	runOnUiThread(new Runnable() {
					public void run()
					{
						updateNavigationInfo(true);
					}
				});
            }
            if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATUS))
            {
				runOnUiThread(new Runnable() {
					public void run()
					{
						updateNavigationInfo(false);
					}
				});
            }
		}
    };

	private ServiceConnection locationConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			locationService = (ILocationService) service;
			locationService.registerLocationCallback(locationListener);
		}

		public void onServiceDisconnected(ComponentName className)
		{
			locationService = null;
		}
	};

	private ILocationListener locationListener = new ILocationListener()
	{

		@Override
		public void onGpsStatusChanged(String provider, int status, int fsats, int tsats)
		{
		}

		@Override
		public void onLocationChanged(final Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					float track = (float) application.fixDeclination(loc.getBearing());
					hsiView.setAzimuth(track);
					speedValue.setText(StringFormatter.speedC(loc.getSpeed()));
					trackValue.setText(StringFormatter.angleC(track));
					elevationValue.setText(StringFormatter.elevationC(loc.getAltitude()));
				}
			});
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
