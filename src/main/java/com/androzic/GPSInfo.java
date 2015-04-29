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

import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.androzic.location.LocationService;
import com.androzic.util.StringFormatter;

public class GPSInfo extends DialogFragment
{
	private TextView satsValue;
	private TextView lastfixValue;
	private TextView accuracyValue;
	private TextView hdopValue;
	private TextView vdopValue;

	protected Androzic application;

	protected Animation shake;

	public GPSInfo()
	{
		application = Androzic.getApplication();
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
		shake = AnimationUtils.loadAnimation(getActivity(), R.anim.shake);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.information_name));
		View view = getActivity().getLayoutInflater().inflate(R.layout.dlg_gps_info, null);
		builder.setView(view);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				GPSInfo.this.dismiss();
			}
		});
		builder.setNeutralButton(R.string.update_almanac, updateOnClickListener);

		satsValue = (TextView) view.findViewById(R.id.sats);
		lastfixValue = (TextView) view.findViewById(R.id.lastfix);
		accuracyValue = (TextView) view.findViewById(R.id.accuracy);
		hdopValue = (TextView) view.findViewById(R.id.hdop);
		vdopValue = (TextView) view.findViewById(R.id.vdop);

		//TODO Make it update periodically
		updateGPSInfo();

		return builder.create();
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private void updateGPSInfo()
	{
		switch (application.gpsStatus)
		{
			case LocationService.GPS_OK:
				satsValue.setText(String.valueOf(application.gpsFSats) + "/" + String.valueOf(application.gpsTSats));
				break;
			case LocationService.GPS_OFF:
				satsValue.setText(R.string.sat_stop);
				break;
			case LocationService.GPS_SEARCHING:
				satsValue.setText(String.valueOf(application.gpsFSats) + "/" + String.valueOf(application.gpsTSats));
				satsValue.startAnimation(shake);
				break;
		}
		float hdop = application.getHDOP();
		if (!Float.isNaN(hdop))
			hdopValue.setText(String.format("%.1f", hdop));
		float vdop = application.getVDOP();
		if (!Float.isNaN(vdop))
			vdopValue.setText(String.format("%.1f", vdop));

		if (application.lastKnownLocation != null)
		{
			Date date = new Date(application.lastKnownLocation.getTime());
			lastfixValue.setText(DateFormat.getDateFormat(application).format(date) + " "
				+ DateFormat.getTimeFormat(application).format(date));
			accuracyValue.setText(application.lastKnownLocation.hasAccuracy() ? StringFormatter.distanceH(application.lastKnownLocation.getAccuracy(), "%.1f", 1000) : "N/A");
		}
	}
	
	private DialogInterface.OnClickListener updateOnClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			LocationManager locationManager = (LocationManager) application.getSystemService(Context.LOCATION_SERVICE);
			if (locationManager != null)
			{
				locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null);
				locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null);
			}
		}
	};

}
