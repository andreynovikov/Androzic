/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014  Andrey Novikov <http://andreynovikov.info/>
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

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.hardware.GeomagneticField;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointProject extends DialogFragment
{
	List<Waypoint> waypoints = null;
	
	public WaypointProject()
	{
		setRetainInstance(true);
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.setTitle(R.string.waypointproject_name);
		return dialog;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.act_waypoint_project, container);

		Activity activity = getActivity();
		Androzic application = Androzic.getApplication();
		waypoints = application.getWaypoints();
		
		((TextView) view.findViewById(R.id.name_text)).setText("WPT"+waypoints.size());

		Collections.sort(waypoints, new Comparator<Waypoint>()
        {
            @Override
            public int compare(Waypoint o1, Waypoint o2)
            {
           		return (o1.name.compareToIgnoreCase(o2.name));
            }
        });

		String[] items = new String[waypoints.size()+1];
		items[0] = getString(R.string.currentloc);
		int i = 1;
		for (Waypoint wpt : waypoints)
		{
			items[i] =  wpt.name;
			i++;
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		((Spinner) view.findViewById(R.id.source_spinner)).setAdapter(adapter);
		
		items = new String[2];
		items[0] = StringFormatter.distanceAbbr;
		items[1] = StringFormatter.distanceShortAbbr;
		adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		((Spinner) view.findViewById(R.id.distance_spinner)).setAdapter(adapter);

		items = getResources().getStringArray(R.array.angle_types);
		adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		((TextView) view.findViewById(R.id.bearing_abbr)).setText(StringFormatter.angleAbbr);
		((Spinner) view.findViewById(R.id.bearing_spinner)).setAdapter(adapter);
		((Spinner) view.findViewById(R.id.bearing_spinner)).setSelection(application.angleMagnetic ? 1 : 0);

	    ((Button) view.findViewById(R.id.done_button)).setOnClickListener(doneOnClickListener);
	    ((Button) view.findViewById(R.id.cancel_button)).setOnClickListener(new OnClickListener() { public void onClick(View v) { dismiss(); } });

	    return view;
	}
	
    @Override
	public void onDestroyView()
	{
		super.onDestroyView();
		
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		
		waypoints = null;
	}
		
	private OnClickListener doneOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	try
        	{
        		Androzic application = Androzic.getApplication();
        		Waypoint waypoint = new Waypoint();
        		View view = getView();
        		waypoint.name = ((TextView) view.findViewById(R.id.name_text)).getText().toString();
        		double distance = Integer.parseInt(((TextView) view.findViewById(R.id.distance_text)).getText().toString());
        		double bearing = Double.parseDouble(((TextView) view.findViewById(R.id.bearing_text)).getText().toString());
        		int src = ((Spinner) view.findViewById(R.id.source_spinner)).getSelectedItemPosition();
        		int df = ((Spinner) view.findViewById(R.id.distance_spinner)).getSelectedItemPosition();
        		int bf = ((Spinner) view.findViewById(R.id.bearing_spinner)).getSelectedItemPosition();
        		double[] loc;
        		if (src > 0)
        		{
        			 loc = new double[2];
        			 loc[0] = waypoints.get(src-1).latitude;
        			 loc[1] = waypoints.get(src-1).longitude;
        		}
        		else
        		{
    				loc = application.getLocation();
        		}

        		if (df == 0)
        		{
        			distance = distance / StringFormatter.distanceFactor * 1000;
        		}
        		else
        		{
        			distance = distance / StringFormatter.distanceShortFactor;
        		}
		        bearing = bearing * StringFormatter.angleFactor;
        		if (bf == 1)
        		{
        			GeomagneticField mag = new GeomagneticField((float) loc[0], (float) loc[1], 0.0f, System.currentTimeMillis());
        			bearing += mag.getDeclination();
			        if (bearing > 360d)
				        bearing -= 360d;
        		}
        		double[] prj = Geo.projection(loc[0], loc[1], distance, bearing);
        		waypoint.latitude = prj[0];
        		waypoint.longitude = prj[1];
        		waypoint.date = Calendar.getInstance().getTime();
        		application.addWaypoint(waypoint);

        		getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, null);
				dismiss();
        	}
        	catch (Exception e)
        	{
    			Toast.makeText(getActivity(), "Invalid input", Toast.LENGTH_LONG).show();
    			e.printStackTrace();
        	}
        }
    };
}
