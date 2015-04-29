/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.androzic.util.Astro;
import com.androzic.util.StringFormatter;

public class LocationInfo extends DialogFragment
{
	private double[] location;

	public LocationInfo()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

    //FIXME Fix lint error
	@SuppressLint("ValidFragment")
    public LocationInfo(double[] location)
	{
		this.location = location;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.information_name));
		View view = getActivity().getLayoutInflater().inflate(R.layout.dlg_location_info, null);
		builder.setView(view);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				LocationInfo.this.dismiss();
			}
		});
		updateLocationInfo(view);
		return builder.create();
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private void updateLocationInfo(View view)
	{

		Androzic application = Androzic.getApplication();

		Location loc = new Location("fake");
		loc.setLatitude(location[0]);
		loc.setLongitude(location[1]);

		((TextView) view.findViewById(R.id.coordinate_degree)).setText(StringFormatter.coordinates(0, " ", location[0], location[1]));
		((TextView) view.findViewById(R.id.coordinate_degmin)).setText(StringFormatter.coordinates(1, " ", location[0], location[1]));
		((TextView) view.findViewById(R.id.coordinate_degminsec)).setText(StringFormatter.coordinates(2, " ", location[0], location[1]));
		((TextView) view.findViewById(R.id.coordinate_utmups)).setText(StringFormatter.coordinates(3, " ", location[0], location[1]));
		((TextView) view.findViewById(R.id.coordinate_mgrs)).setText(StringFormatter.coordinates(4, " ", location[0], location[1]));

		Calendar now = GregorianCalendar.getInstance(TimeZone.getDefault());
		double sunrise = Astro.computeSunriseTime(application.getZenith(), loc, now);
		double sunset = Astro.computeSunsetTime(application.getZenith(), loc, now);

		if (Double.isNaN(sunrise))
		{
			((TextView) view.findViewById(R.id.sunrise)).setText(R.string.never);
		}
		else
		{
			((TextView) view.findViewById(R.id.sunrise)).setText(Astro.getLocalTimeAsString(sunrise));
		}
		if (Double.isNaN(sunset))
		{
			((TextView) view.findViewById(R.id.sunset)).setText(R.string.never);
		}
		else
		{
			((TextView) view.findViewById(R.id.sunset)).setText(Astro.getLocalTimeAsString(sunset));
		}
		double declination = application.getDeclination(location[0], location[1]);
		((TextView) view.findViewById(R.id.declination)).setText(String.format("%+.1f\u00B0", declination));
	}
}
