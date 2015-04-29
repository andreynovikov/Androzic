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

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.FragmentHolder;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.ui.TooltipManager;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointDetails extends Fragment
{
	public static final String TAG = "WaypointDetails";

	private FragmentHolder fragmentHolderCallback;
	private OnWaypointActionListener waypointActionsCallback;

	private Waypoint waypoint;

	private Handler tooltipCallback = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.waypoint_details, container, false);
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			fragmentHolderCallback = (FragmentHolder) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement FragmentHolder");
		}
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

		if (waypoint != null)
		{
			Bundle args = getArguments();
			double lat = args != null ? args.getDouble("lat") : waypoint.latitude;
	        double lon = args != null ? args.getDouble("lon") : waypoint.longitude;
			updateWaypointDetails(lat, lon);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();

		fragmentHolderCallback.enableActionButton().setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				waypointActionsCallback.onWaypointNavigate(waypoint);
			}
		});

		tooltipCallback.postDelayed(showTooltip, TooltipManager.TOOLTIP_DELAY);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		fragmentHolderCallback.disableActionButton();

		// Stop showing tooltips
		tooltipCallback.removeCallbacks(showTooltip);
		TooltipManager.dismiss();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.waypoint_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.action_navigate).setVisible(false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_edit:
				waypointActionsCallback.onWaypointEdit(waypoint);
				return true;
			case R.id.action_view:
				waypointActionsCallback.onWaypointView(waypoint);
				return true;
			case R.id.action_share:
				waypointActionsCallback.onWaypointShare(waypoint);
				return true;
			case R.id.action_delete:
				waypointActionsCallback.onWaypointRemove(waypoint);
				// "Close" fragment
				getFragmentManager().popBackStack();
				return true;
		}
		return false;
	}

	final private Runnable showTooltip = new Runnable() {
		@Override
		public void run()
		{
			long tooltip = TooltipManager.getTooltip(TAG);
			if (tooltip == 0L)
				return;
			if (tooltip == TooltipManager.TOOLTIP_WAYPOINT_COORDINATES)
				TooltipManager.showTooltip(tooltip, getView().findViewById(R.id.coordinates));
			tooltipCallback.postDelayed(this, TooltipManager.TOOLTIP_PERIOD);
		}
	};

	public void setWaypoint(Waypoint waypoint)
	{
		this.waypoint = waypoint;
		
		if (isVisible())
		{
			Bundle args = getArguments();
			double lat = args != null ? args.getDouble("lat") : waypoint.latitude;
	        double lon = args != null ? args.getDouble("lon") : waypoint.longitude;
			updateWaypointDetails(lat, lon);
		}
	}

	@SuppressLint("NewApi")
	private void updateWaypointDetails(double lat, double lon)
	{
		Androzic application = Androzic.getApplication();
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		
		activity.getSupportActionBar().setTitle(waypoint.name);

		View view = getView();
		
		final TextView coordsView = (TextView) view.findViewById(R.id.coordinates);
		coordsView.requestFocus();
		coordsView.setTag(Integer.valueOf(StringFormatter.coordinateFormat));
		coordsView.setText(StringFormatter.coordinates(" ", waypoint.latitude, waypoint.longitude));
		coordsView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				int format = ((Integer)coordsView.getTag()).intValue() + 1;
				if (format == 5)
					format = 0;
				coordsView.setText(StringFormatter.coordinates(format, " ", waypoint.latitude, waypoint.longitude));
				coordsView.setTag(Integer.valueOf(format));
			}
		});

		if (waypoint.altitude != Integer.MIN_VALUE)
		{
			((TextView) view.findViewById(R.id.altitude)).setText("\u2336 " + StringFormatter.elevationH(waypoint.altitude));
			view.findViewById(R.id.altitude).setVisibility(View.VISIBLE);
		}
		else
		{
			view.findViewById(R.id.altitude).setVisibility(View.GONE);
		}

		if (waypoint.proximity > 0)
		{
			((TextView) view.findViewById(R.id.proximity)).setText("~ " + StringFormatter.distanceH(waypoint.proximity));
			view.findViewById(R.id.proximity).setVisibility(View.VISIBLE);
		}
		else
		{
			view.findViewById(R.id.proximity).setVisibility(View.GONE);
		}

		double dist = Geo.distance(lat, lon, waypoint.latitude, waypoint.longitude);
		double bearing = Geo.bearing(lat, lon, waypoint.latitude, waypoint.longitude);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.angleH(bearing);
		((TextView) view.findViewById(R.id.distance)).setText(distance);

		((TextView) view.findViewById(R.id.waypointset)).setText(waypoint.set.name);

		if (waypoint.date != null)
		{
			view.findViewById(R.id.date_row).setVisibility(View.VISIBLE);
			((TextView) view.findViewById(R.id.date)).setText(DateFormat.getDateFormat(activity).format(waypoint.date)+" "+DateFormat.getTimeFormat(activity).format(waypoint.date));
		}
		else
		{
			view.findViewById(R.id.date_row).setVisibility(View.GONE);
		}
		
		if ("".equals(waypoint.description))
		{
			view.findViewById(R.id.description_row).setVisibility(View.GONE);
		}
		else
		{
			WebView description = (WebView) view.findViewById(R.id.description);
			String descriptionHtml;
			try
			{
				TypedValue tv = new TypedValue();
				Theme theme = activity.getTheme();
				Resources resources = getResources();
				theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true);
				int secondaryColor = resources.getColor(tv.resourceId);
				String css = String.format("<style type=\"text/css\">html,body{margin:0;background:transparent} *{color:#%06X}</style>\n", (secondaryColor & 0x00FFFFFF));
				descriptionHtml = css + waypoint.description;
				description.setWebViewClient(new WebViewClient()
				{
				    @SuppressLint("NewApi")
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
			view.findViewById(R.id.description_row).setVisibility(View.VISIBLE);
		}
	}
}
