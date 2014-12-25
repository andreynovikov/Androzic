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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class LocationWidget extends AppWidgetProvider
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.d("ANCROZIC", "Widget onReceive()");
		super.onReceive(context, intent);
		String action = intent.getAction();
		Log.d("ANCROZIC", "action:" + action);
		if (action.contentEquals(WidgetService.WIDGET_REFRESH))
		{
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
			ComponentName thisWidget = new ComponentName(context, LocationWidget.class);
			int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
			for (int appWidgetId : appWidgetIds)
			{
//				appWidgetManager.updateAppWidget(appWidgetId, views);
			}
		}
	}

	/*
	 * @Override public void onReceive(Context context, Intent intent) {
	 * if(intent.getAction().equals("widget")) {
	 * 
	 * RemoteViews remoteView = new RemoteViews(context.getPackageName(),
	 * R.layout.location_widget); AppWidgetManager appWidgetManager =
	 * AppWidgetManager.getInstance(context); ComponentName thisAppWidget = new
	 * ComponentName(PACKAGE, PACKAGE+"."+CLASS); this. int[] appWidgetIds =
	 * appWidgetManager.getAppWidgetIds(thisAppWidget); try { onUpdate(context,
	 * appWidgetManager, appWidgetIds); } catch (Exception e) {
	 * Auto-generated catch block e.printStackTrace();
	 * 
	 * }
	 * 
	 * appWidgetManager.updateAppWidget(appWidgetId, remoteView); }
	 * 
	 * super.onReceive(context, intent); }
	 */

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		for (int appWidgetId : appWidgetIds)
		{
			setAlarm(context, appWidgetId, 2000);
		}
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}

	public static void setAlarm(Context context, int appWidgetId, int updateRate)
	{
		Intent active = new Intent(context, WidgetService.class);
		active.setAction(WidgetService.WIDGET_UPDATE);
		active.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		PendingIntent intent = PendingIntent.getService(context, 0, active, PendingIntent.FLAG_UPDATE_CURRENT);

		AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		if (updateRate >= 0)
		{
			alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), updateRate, intent);
		}
		else
		{
			alarms.cancel(intent);
		}
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds)
	{
		for (int appWidgetId : appWidgetIds)
		{
			setAlarm(context, appWidgetId, -1);
		}
		super.onDeleted(context, appWidgetIds);
	}

	public void onDisabled(Context context)
	{
		context.stopService(new Intent(context, WidgetService.class));
		super.onDisabled(context);
	}

}
