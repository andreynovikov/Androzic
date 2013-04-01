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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.util.Astro;
import com.androzic.util.StringFormatter;

public class Information extends SherlockActivity
{
	private ILocationService locationService = null;
	private TextView satsValue;
	private TextView lastfixValue;
	private TextView providerValue;
	private TextView latitudeValue;
	private TextView longitudeValue;
	private TextView accuracyValue;
	private TextView sunriseValue;
	private TextView sunsetValue;
	private TextView declinationValue;
	private TextView hdopValue;
	private TextView vdopValue;

	protected Androzic application;

	protected Animation shake;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_information);

		application = (Androzic) getApplication();
		shake = AnimationUtils.loadAnimation(Information.this, R.anim.shake);

		satsValue = (TextView) findViewById(R.id.sats);
		lastfixValue = (TextView) findViewById(R.id.lastfix);
		accuracyValue = (TextView) findViewById(R.id.accuracy);
		providerValue = (TextView) findViewById(R.id.provider);
		latitudeValue = (TextView) findViewById(R.id.latitude);
		longitudeValue = (TextView) findViewById(R.id.longitude);
		sunriseValue = (TextView) findViewById(R.id.sunrise);
		sunsetValue = (TextView) findViewById(R.id.sunset);
		declinationValue = (TextView) findViewById(R.id.declination);
		hdopValue = (TextView) findViewById(R.id.hdop);
		vdopValue = (TextView) findViewById(R.id.vdop);

		Button update = (Button) findViewById(R.id.almanac_button);
		update.setOnClickListener(updateOnClickListener);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
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
	}

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

	private ILocationListener locationListener = new ILocationListener() {

		@Override
		public void onGpsStatusChanged(final String provider, final int status, final int fsats, final int tsats)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					switch (status)
					{
						case LocationService.GPS_OK:
							satsValue.setText(String.valueOf(fsats) + "/" + String.valueOf(tsats));
							break;
						case LocationService.GPS_OFF:
							satsValue.setText(R.string.sat_stop);
							break;
						case LocationService.GPS_SEARCHING:
							satsValue.setText(String.valueOf(fsats) + "/" + String.valueOf(tsats));
							satsValue.startAnimation(shake);
							break;
					}
					if (locationService != null)
					{
						float hdop = locationService.getHDOP();
						if (!Float.isNaN(hdop))
							hdopValue.setText(String.format("%.1f", hdop));
						float vdop = locationService.getVDOP();
						if (!Float.isNaN(vdop))
							vdopValue.setText(String.format("%.1f", vdop));
					}
				}
			});
		}

		@Override
		public void onLocationChanged(final Location loc, boolean continous, boolean geoid, float smoothspeed, float avgspeed)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					Date date = new Date(loc.getTime());
					lastfixValue.setText(DateFormat.getDateFormat(Information.this).format(date) + " "
							+ DateFormat.getTimeFormat(Information.this).format(date));
					providerValue.setText(loc.getProvider() != null ? loc.getProvider() : "N/A");
					// FIXME Needs UTM support here
					latitudeValue.setText(StringFormatter.coordinate(application.coordinateFormat, loc.getLatitude()));
					longitudeValue.setText(StringFormatter.coordinate(application.coordinateFormat, loc.getLongitude()));
					accuracyValue.setText(loc.hasAccuracy() ? StringFormatter.distanceH(loc.getAccuracy(), "%.1f", 1000) : "N/A");

					Calendar now = GregorianCalendar.getInstance(TimeZone.getDefault());
					double sunrise = Astro.computeSunriseTime(application.getZenith(), loc, now);
					double sunset = Astro.computeSunsetTime(application.getZenith(), loc, now);

					if (Double.isNaN(sunrise))
					{
						sunriseValue.setText(R.string.never);
					}
					else
					{
						sunriseValue.setText(Astro.getLocalTimeAsString(sunrise));
					}
					if (Double.isNaN(sunset))
					{
						sunsetValue.setText(R.string.never);
					}
					else
					{
						sunsetValue.setText(Astro.getLocalTimeAsString(sunset));
					}
					double declination = application.getDeclination();
					declinationValue.setText(String.format("%+.1f°", declination));
				}
			});
		}

		@Override
		public void onProviderChanged(String provider)
		{
			// TODO Auto-generated method stub

		}

		@Override
		public void onProviderDisabled(String provider)
		{
			// TODO Auto-generated method stub

		}

		@Override
		public void onProviderEnabled(String provider)
		{
			// TODO Auto-generated method stub

		}
	};

	private OnClickListener updateOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			if (locationManager != null)
			{
				locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null);
				locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null);
			}
		}
	};
}
