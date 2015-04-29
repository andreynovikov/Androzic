/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2015  Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.waypoint;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class CoordinatesReceived extends DialogFragment
{
    private double lat, lon;

	@NonNull
	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.information_name));
		View view = getActivity().getLayoutInflater().inflate(R.layout.dlg_coordinates_received, null);
		builder.setView(view);
		builder.setPositiveButton(R.string.menu_visible, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				Androzic application = Androzic.getApplication();
				application.ensureVisible(lat, lon);
				CoordinatesReceived.this.dismiss();
			}
		});

		Bundle args = getArguments();

		String title = args.getString("title");
		String sender = args.getString("sender");
		lat = args.getDouble("lat");
		lon = args.getDouble("lon");

		if (! "".equals(title))
			builder.setTitle(title);
		else
			builder.setTitle(R.string.coordinates_name);

		Androzic application = Androzic.getApplication();
		double[] ll = application.getLocation();

		((TextView) view.findViewById(R.id.message)).setText(getString(R.string.new_coordinates, sender));

		String coords = StringFormatter.coordinates(" ", lat, lon);
		((TextView) view.findViewById(R.id.coordinates)).setText(coords);

		double dist = Geo.distance(ll[0], ll[1], lat, lon);
		double bearing = Geo.bearing(ll[0], ll[1], lat, lon);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.angleH(bearing);
		((TextView) view.findViewById(R.id.distance)).setText(distance);

		return builder.create();
	}
}