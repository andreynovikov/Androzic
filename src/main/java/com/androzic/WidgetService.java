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

import com.androzic.location.ITrackingCallback;
import com.androzic.location.ITrackingRemoteService;
import com.androzic.util.StringFormatter;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class WidgetService extends Service
{
	public static final String WIDGET_UPDATE = "update";
	public static final String WIDGET_REFRESH = "refresh";
	public static final String TRACKING_START = "start";
	public static final String TRACKING_STOP = "stop";

	private ITrackingRemoteService remoteService = null;
	private boolean isBound = false;
	protected boolean isConnected = false;

	protected double latitude;
	protected double longitude;

	@Override
	public void onDestroy()
	{
		Log.d("ANDROZIC", "WidgetService: onDestroy");
		if (isBound)
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
				unbindService(connection);
			}
			isConnected = false;
			isBound = false;
		}
		super.onDestroy();
	}

	@Override
	public void onStart(Intent intent, int startId)
	{
		if (intent != null)
		{
			if (intent.getAction().equals(WIDGET_UPDATE))
			{
				Log.d("ANDROZIC", "WidgetService: widget update");
				if (! isBound)
				{
					isBound = bindService(new Intent(ITrackingRemoteService.class.getName()), connection, 0);
					Log.d("ANDROZIC", "WidgetService: bind to service: " + isBound);
				}
				int appWidgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);

				RemoteViews remoteView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.location_widget);
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());

				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
				int format = Integer.parseInt(settings.getString(getString(R.string.pref_unitcoordinate), "0"));

				remoteView.setTextColor(R.id.latitude, Color.WHITE);
				remoteView.setTextColor(R.id.longitude, Color.WHITE);

				Intent start = new Intent(getBaseContext(), WidgetService.class);
				start.setAction(WidgetService.TRACKING_START);
				start.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
				PendingIntent startIntent = PendingIntent.getService(getBaseContext(), 0, start, PendingIntent.FLAG_UPDATE_CURRENT);

				Intent stop = new Intent(getBaseContext(), WidgetService.class);
				stop.setAction(WidgetService.TRACKING_STOP);
				stop.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
				PendingIntent stopIntent = PendingIntent.getService(getBaseContext(), 0, stop, PendingIntent.FLAG_UPDATE_CURRENT);
				
				remoteView.setOnClickPendingIntent(R.id.start, startIntent);
				remoteView.setOnClickPendingIntent(R.id.stop, stopIntent);

				if (isConnected)
				{
					//FIXME Needs UTM support here
					remoteView.setTextViewText(R.id.latitude, StringFormatter.coordinate(format, latitude));
					remoteView.setTextViewText(R.id.longitude, StringFormatter.coordinate(format, longitude));
					remoteView.setViewVisibility(R.id.start, View.GONE);
					remoteView.setViewVisibility(R.id.stop, View.VISIBLE);
				}
				else
				{
					remoteView.setTextViewText(R.id.latitude, "Tracking");
					remoteView.setTextViewText(R.id.longitude, "disabled");
					remoteView.setViewVisibility(R.id.start, View.VISIBLE);
					remoteView.setViewVisibility(R.id.stop, View.GONE);
				}

				appWidgetManager.updateAppWidget(appWidgetId, remoteView);
			}
			if (intent.getAction().equals(TRACKING_START))
			{
				Log.d("ANDROZIC", "WidgetService: start tracking");
				isBound = bindService(new Intent(ITrackingRemoteService.class.getName()), connection, BIND_AUTO_CREATE);
				Log.d("ANDROZIC", "WidgetService: bind to service: " + isBound);
			}
			if (intent.getAction().equals(TRACKING_STOP))
			{
				Log.d("ANDROZIC", "WidgetService: stop tracking");
				if (isBound)
				{
					unbindService(connection);
					Log.d("ANDROZIC", "WidgetService: unbind from service");
				}
				remoteService = null;
				isConnected = false;
				isBound = false;
			}
		}

		super.onStart(intent, startId);
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			remoteService = ITrackingRemoteService.Stub.asInterface(service);

			try
			{
				remoteService.registerCallback(callback);
				isConnected = true;
				Log.d("ANDROZIC", "WidgetService: service connected");
			}
			catch (RemoteException e)
			{
			}
		}

		public void onServiceDisconnected(ComponentName className)
		{
			isConnected = false;
			remoteService = null;
			if (isBound)
			{
				unbindService(connection);
				Log.d("ANDROZIC", "WidgetService: unbind from service");
			}
			isBound = false;
			Log.d("ANDROZIC", "WidgetService: service disconnected");
		}
	};

	private ITrackingCallback callback = new ITrackingCallback.Stub()
	{
		@Override
		public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, double accuracy, long time) throws RemoteException
		{
			Log.d("ANDROZIC", "WidgetService: track point arrived");
			latitude = lat;
			longitude = lon;
		}
	};
}
