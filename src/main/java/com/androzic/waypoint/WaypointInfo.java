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

/*
 * Contributor: Gutorov Dmitry <dolfwolkov at gmail dot com>
 */

package com.androzic.waypoint;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointInfo extends DialogFragment implements OnClickListener
{
	private Waypoint waypoint;
	private OnWaypointActionListener waypointActionsCallback;

	public WaypointInfo()
	{
		setRetainInstance(true);
	}
	
	public void setWaypoint(Waypoint waypoint)
	{
		this.waypoint = waypoint;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.act_waypoint_info, container);
	    ((ImageButton) view.findViewById(R.id.navigate_button)).setOnClickListener(this);
	    ((ImageButton) view.findViewById(R.id.edit_button)).setOnClickListener(this);
	    ((ImageButton) view.findViewById(R.id.share_button)).setOnClickListener(this);
	    ((ImageButton) view.findViewById(R.id.remove_button)).setOnClickListener(this);
	    return view;
    }

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			waypointActionsCallback = (OnWaypointActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnWaypointActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Bundle args = getArguments();
		if (args != null)
		{
			double lat = args.getDouble("lat");
	        double lon = args.getDouble("lon");
	        updateWaypointInfo(lat, lon);
		}
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	@Override
    public void onClick(View v)
    {
		switch (v.getId())
		{
			case R.id.navigate_button:
				waypointActionsCallback.onWaypointNavigate(waypoint);
				break;
			case R.id.edit_button:
				waypointActionsCallback.onWaypointEdit(waypoint);
				break;
			case R.id.share_button:
				waypointActionsCallback.onWaypointShare(waypoint);
				break;
			case R.id.remove_button:
				waypointActionsCallback.onWaypointRemove(waypoint);
				break;
		}
		dismiss();
    }
	
	@SuppressLint("NewApi")
	private void updateWaypointInfo(double lat, double lon)
	{
		Androzic application = Androzic.getApplication();
		Activity activity = getActivity();
		Dialog dialog = getDialog();
		View view = getView();
		
		WebView description = (WebView) view.findViewById(R.id.description);
		
		if ("".equals(waypoint.description))
		{
			description.setVisibility(View.GONE);
		}
		else
		{
			String descriptionHtml;
			try
			{
				TypedValue tv = new TypedValue();
				Theme theme = activity.getTheme();
				Resources resources = getResources();
				theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true);
				int secondaryColor = resources.getColor(tv.resourceId);
				String css = String.format("<style type=\"text/css\">html,body{margin:0;background:transparent} *{color:#%06X}</style>\n", (secondaryColor & 0x00FFFFFF));
				descriptionHtml = css + waypoint.description;
				description.setWebViewClient(new WebViewClient()
				{
				    @Override
				    public void onPageFinished(WebView view, String url)
				    {
				    	view.setBackgroundColor(Color.TRANSPARENT);
				        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				        	view.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
				    }
				});
				description.setBackgroundColor(Color.TRANSPARENT);
		        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		        	description.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
			}
			catch (Resources.NotFoundException e)
			{
				description.setBackgroundColor(Color.LTGRAY);
				descriptionHtml = waypoint.description;
			}
			
			WebSettings settings = description.getSettings();
			settings.setDefaultTextEncodingName("utf-8");
			settings.setAllowFileAccess(true);
			Uri baseUrl = Uri.fromFile(new File(application.dataPath));
			description.loadDataWithBaseURL(baseUrl.toString() + "/", descriptionHtml, "text/html", "utf-8", null);
		}

		String coords = StringFormatter.coordinates(" ", waypoint.latitude, waypoint.longitude);
		((TextView) view.findViewById(R.id.coordinates)).setText(coords);
		
		if (waypoint.altitude != Integer.MIN_VALUE)
		{
			((TextView) view.findViewById(R.id.altitude)).setText(StringFormatter.elevationH(waypoint.altitude));
		}
		
		double dist = Geo.distance(lat, lon, waypoint.latitude, waypoint.longitude);
		double bearing = Geo.bearing(lat, lon, waypoint.latitude, waypoint.longitude);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.angleH(bearing);
		((TextView) view.findViewById(R.id.distance)).setText(distance);

		if (waypoint.date != null)
			((TextView) view.findViewById(R.id.date)).setText(DateFormat.getDateFormat(activity).format(waypoint.date)+" "+DateFormat.getTimeFormat(activity).format(waypoint.date));
		else
			((TextView) view.findViewById(R.id.date)).setVisibility(View.GONE);

		dialog.setTitle(waypoint.name);
	}
}