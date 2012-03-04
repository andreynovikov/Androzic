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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;

public class CompassActivity extends Activity
{
	private WakeLock wakeLock;
	private ILocationService locationService = null;
    private HSIView hsiView;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.act_compass);

        hsiView = (HSIView) findViewById(R.id.hsiview);
		hsiView.setCompassMode(true);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "DoNotDimScreen");
    }
    
	@Override
	protected void onResume()
	{
		super.onResume();
		boolean lock = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_wakelock), getResources().getBoolean(R.bool.def_wakelock));
		if (lock)
		{
			wakeLock.acquire();
		}
        bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (locationService != null)
		{
			locationService.unregisterCallback(locationListener);
			unbindService(locationConnection);
			locationService = null;
		}
		if (wakeLock.isHeld())
		{
			wakeLock.release();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.view_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuGPS:
				startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				return true;
			case R.id.menuPreferences:
				startActivity(new Intent(this, Preferences.class));
				return true;
		}
		return false;
	}

	private ServiceConnection locationConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			locationService = (ILocationService) service;
			locationService.registerCallback(locationListener);
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
		public void onSensorChanged(final float azimuth, final float pitch, final float roll)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					hsiView.setAzimuth(azimuth);
				}
			});
		}
	};
}
