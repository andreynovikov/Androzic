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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.androzic.waypoint.CoordinatesReceived;

public class ActionsReceiver extends BroadcastReceiver
{
	private static final String TAG = "ActionsReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		Log.i(TAG, "Action received: " + action);

		Intent activity = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		if (action.equals("com.androzic.COORDINATES_RECEIVED"))
		{
			activity.putExtras(intent);
			activity.putExtra(MainActivity.SHOW_FRAGMENT, CoordinatesReceived.class);
		}
		if (action.equals("com.androzic.CENTER_ON_COORDINATES"))
		{
			activity.putExtras(intent);
		}
		context.startActivity(activity);
	}
}
