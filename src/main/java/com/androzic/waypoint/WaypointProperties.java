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

package com.androzic.waypoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.coords.MGRSCoord;
import gov.nasa.worldwind.geom.coords.UPSCoord;
import gov.nasa.worldwind.geom.coords.UTMCoord;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.ui.ColorButton;
import com.androzic.ui.MarkerPicker;
import com.androzic.ui.TooltipManager;
import com.androzic.util.StringFormatter;

public class WaypointProperties extends Fragment implements AdapterView.OnItemSelectedListener, PopupMenu.OnMenuItemClickListener, MarkerPicker.OnMarkerPickerDialogListener
{
	public static final String TAG = "WaypointProperties";

	private Waypoint waypoint;
	private Route route;

	private TabHost tabHost;

	private TextView name;
	private TextView description;
	private TextView altitude;
	private TextView proximity;
	private Spinner waypointSet;
	private ColorButton markercolor;
	private ColorButton textcolor;

	private ViewGroup coordDeg;
	private ViewGroup coordUtm;
	private ViewGroup coordMgrs;
	private ViewGroup coordLatDeg;
	private ViewGroup coordLatMin;
	private ViewGroup coordLatSec;
	private ViewGroup coordLonDeg;
	private ViewGroup coordLonMin;
	private ViewGroup coordLonSec;

	private int curFormat = -1;
	private String iconValue;

	private int defMarkerColor;
	private int defTextColor;

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
		Androzic application = Androzic.getApplication();

		View rootView = inflater.inflate(R.layout.act_waypoint_properties, container, false);

		tabHost = (TabHost) rootView.findViewById(R.id.tabhost);
		tabHost.setup();
		tabHost.addTab(tabHost.newTabSpec("main").setIndicator(getString(R.string.primary)).setContent(R.id.properties));
		tabHost.addTab(tabHost.newTabSpec("advanced").setIndicator(getString(R.string.advanced)).setContent(R.id.advanced));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			tabHost.getTabWidget().getChildAt(0).getLayoutParams().height = 50;
			tabHost.getTabWidget().getChildAt(1).getLayoutParams().height = 50;
		}

		name = (TextView) rootView.findViewById(R.id.name_text);
		description = (TextView) rootView.findViewById(R.id.description_text);
		altitude = (TextView) rootView.findViewById(R.id.altitude_text);
		proximity = (TextView) rootView.findViewById(R.id.proximity_text);

		iconValue = null;
		ImageButton icon = (ImageButton) rootView.findViewById(R.id.icon_button);
		icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_highlight_remove_white_24dp));
		if (waypoint.drawImage)
		{
			Bitmap b = BitmapFactory.decodeFile(application.markerPath + File.separator + waypoint.marker);
			if (b != null)
			{
				icon.setImageBitmap(b);
				iconValue = waypoint.marker;
			}
		}
		icon.setOnClickListener(iconOnClickListener);

		ArrayList<String> items = new ArrayList<String>();
		for (WaypointSet wptset : application.getWaypointSets())
		{
			items.add(wptset.name);
		}

		waypointSet = (Spinner) rootView.findViewById(R.id.set_spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		waypointSet.setAdapter(adapter);

		markercolor = (ColorButton) rootView.findViewById(R.id.markercolor_button);
		textcolor = (ColorButton) rootView.findViewById(R.id.textcolor_button);

		coordDeg = (ViewGroup) rootView.findViewById(R.id.coord_deg);
		coordUtm = (ViewGroup) rootView.findViewById(R.id.coord_utm);
		coordMgrs = (ViewGroup) rootView.findViewById(R.id.coord_mgrs);
		coordLatDeg = (ViewGroup) rootView.findViewById(R.id.coord_lat_deg);
		coordLatMin = (ViewGroup) rootView.findViewById(R.id.coord_lat_min);
		coordLatSec = (ViewGroup) rootView.findViewById(R.id.coord_lat_sec);
		coordLonDeg = (ViewGroup) rootView.findViewById(R.id.coord_lon_deg);
		coordLonMin = (ViewGroup) rootView.findViewById(R.id.coord_lon_min);
		coordLonSec = (ViewGroup) rootView.findViewById(R.id.coord_lon_sec);

		Spinner coordformat = (Spinner) rootView.findViewById(R.id.coordformat_spinner);
		coordformat.setOnItemSelectedListener(this);
		coordformat.setSelection(StringFormatter.coordinateFormat);

		return rootView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState != null)
		{
			View rootView = getView();

			tabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));

			iconValue = savedInstanceState.getString("icon");
			ImageButton icon = (ImageButton) rootView.findViewById(R.id.icon_button);
			if (iconValue != null)
			{
				Androzic application = Androzic.getApplication();
				Bitmap b = BitmapFactory.decodeFile(application.markerPath + File.separator + iconValue);
				if (b != null)
					icon.setImageBitmap(b);
			}
			else
			{
				icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_highlight_remove_white_24dp));
			}
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (waypoint != null)
			updateWaypointProperties();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.waypointproperties_name);
		//tooltipCallback.postDelayed(showTooltip, TooltipManager.TOOLTIP_DELAY_SHORT);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		// Stop showing tooltips
		tooltipCallback.removeCallbacks(showTooltip);
		TooltipManager.dismiss();
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString("tab", tabHost.getCurrentTabTag());
		outState.putString("icon", iconValue);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.itemsave_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_save:
				Androzic application = Androzic.getApplication();
				try
				{
					if (name.getText().length() == 0)
						return false;

					if (waypoint == null)
					{
						waypoint = new Waypoint();
					}
					if (waypoint.date == null)
					{
						waypoint.date = Calendar.getInstance().getTime();
					}

					waypoint.name = name.getText().toString();

					waypoint.description = description.getText().toString();
					Angle[] coords = getLatLon();
					waypoint.latitude = coords[0].degrees;
					waypoint.longitude = coords[1].degrees;

					try
					{
						String p = proximity.getText().toString();
						if ("".equals(p))
							waypoint.proximity = 0;
						else
							waypoint.proximity = Integer.parseInt(p);
					}
					catch (NumberFormatException e)
					{
						e.printStackTrace();
					}

					try
					{
						String a = altitude.getText().toString();
						if ("".equals(a))
							waypoint.altitude = Integer.MIN_VALUE;
						else
							waypoint.altitude = Integer.parseInt(a);
					}
					catch (NumberFormatException e)
					{
						e.printStackTrace();
					}

					if (iconValue == null)
					{
						waypoint.marker = "";
						waypoint.drawImage = false;
					}
					else
					{
						waypoint.marker = iconValue;
						waypoint.drawImage = true;
					}
					int markerColorValue = markercolor.getColor();
					if (markerColorValue != defMarkerColor)
						waypoint.backcolor = markerColorValue;
					int textColorValue = textcolor.getColor();
					if (textColorValue != defTextColor)
						waypoint.textcolor = textColorValue;

					if (route != null)
					{
						application.dispatchRoutePropertiesChanged(route);
					}
					else
					{
						if (waypoint.set == null)
						{
							application.addWaypoint(waypoint);
						}

						int set = waypointSet.getSelectedItemPosition();
						waypoint.set = application.getWaypointSets().get(set);

						application.saveWaypoints();
					}

					// Hide keyboard
					final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
					// "Close" fragment
					getFragmentManager().popBackStack();
					return true;
				}
				catch (Exception e)
				{
					e.printStackTrace();
					Toast.makeText(getActivity(), "Invalid input", Toast.LENGTH_LONG).show();
				}
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	final private Runnable showTooltip = new Runnable() {
		@Override
		public void run()
		{
			long tooltip = TooltipManager.getTooltip(TAG);
			if (tooltip == 0L)
				return;
			View utmZone = getView().findViewById(R.id.utm_zone_text);
			if (tooltip == TooltipManager.TOOLTIP_UTM_ZONE && utmZone != null && utmZone.isShown())
				TooltipManager.showTooltip(tooltip, utmZone);
			tooltipCallback.postDelayed(this, TooltipManager.TOOLTIP_PERIOD);
		}
	};

	public void setWaypoint(Waypoint waypoint)
	{
		this.waypoint = waypoint;
		this.route = null;
		if (isVisible())
			updateWaypointProperties();
	}

	public void setWaypoint(Waypoint waypoint, Route route)
	{
		this.waypoint = waypoint;
		this.route = route;
		if (isVisible())
			updateWaypointProperties();
	}

	private void updateWaypointProperties()
	{
		Androzic application = Androzic.getApplication();

		View rootView = getView();

		int visible = route != null ? View.GONE : View.VISIBLE;
		// TODO Think about this case
		// rootView.findViewById(R.id.advanced).setVisibility(visible);
		rootView.findViewById(R.id.icon_container).setVisibility(visible);
		rootView.findViewById(android.R.id.tabs).setVisibility(visible);

		name.setText(waypoint.name);
		description.setText(waypoint.description);

		if (waypoint.altitude != Integer.MIN_VALUE)
			altitude.setText(String.valueOf(waypoint.altitude));
		if (waypoint.proximity != 0)
			proximity.setText(String.valueOf(waypoint.proximity));

		int markerColorValue = waypoint.backcolor;
		int textColorValue = waypoint.textcolor;

		int set = waypoint.set == null ? 0 : application.getWaypointSets().indexOf(waypoint.set);
		waypointSet.setSelection(set);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
		defMarkerColor = settings.getInt(getString(R.string.pref_waypoint_color), getResources().getColor(R.color.waypoint));
		defTextColor = settings.getInt(getString(R.string.pref_waypoint_namecolor), getResources().getColor(R.color.waypointtext));
		if (markerColorValue == Integer.MIN_VALUE)
			markerColorValue = defMarkerColor;
		if (textColorValue == Integer.MIN_VALUE)
			textColorValue = defTextColor;
		markercolor.setColor(markerColorValue, defMarkerColor);
		textcolor.setColor(textColorValue, defTextColor);
	}

	@Override
	public void onMarkerSelected(String icon)
	{
		iconValue = icon;
		ImageButton iconButton = (ImageButton) getView().findViewById(R.id.icon_button);
		Androzic application = Androzic.getApplication();
		Bitmap b = BitmapFactory.decodeFile(application.markerPath + File.separator + iconValue);
		if (b != null)
			iconButton.setImageBitmap(b);
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.change:
				MarkerPicker dialog = new MarkerPicker(this);
				dialog.show(getFragmentManager(), "dialog");
				break;
			case R.id.remove:
				iconValue = null;
				ImageButton icon = (ImageButton) getView().findViewById(R.id.icon_button);
				icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_highlight_remove_white_24dp));
				break;
		}
		return true;
	}

	private OnClickListener iconOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			PopupMenu popup = new PopupMenu(getActivity(), v);
			popup.getMenuInflater().inflate(R.menu.marker_popup, popup.getMenu());
			popup.setOnMenuItemClickListener(WaypointProperties.this);
			popup.show();
		}
	};

	private Angle[] getLatLon()
	{
		int degrees, minutes;
		double mins, seconds;

		View rootView = getView();
		Angle[] coords = new Angle[2];
		switch (curFormat)
		{
			case -1:
				coords[0] = Angle.fromDegreesLatitude(waypoint.latitude);
				coords[1] = Angle.fromDegreesLongitude(waypoint.longitude);
				break;
			case 0:
				coords[0] = Angle.fromDegreesLatitude(Double.valueOf(((TextView) rootView.findViewById(R.id.lat_dd_text)).getText().toString()));
				coords[1] = Angle.fromDegreesLongitude(Double.valueOf(((TextView) rootView.findViewById(R.id.lon_dd_text)).getText().toString()));
				break;
			case 1:
				degrees = Integer.valueOf(((TextView) rootView.findViewById(R.id.lat_md_text)).getText().toString());
				mins = Double.valueOf(((TextView) rootView.findViewById(R.id.lat_mm_text)).getText().toString());
				coords[0] = Angle.fromDM(degrees, mins);
				degrees = Integer.valueOf(((TextView) rootView.findViewById(R.id.lon_md_text)).getText().toString());
				mins = Double.valueOf(((TextView) rootView.findViewById(R.id.lon_mm_text)).getText().toString());
				coords[1] = Angle.fromDM(degrees, mins);
				break;
			case 2:
				degrees = Integer.valueOf(((TextView) rootView.findViewById(R.id.lat_sd_text)).getText().toString());
				minutes = Integer.valueOf(((TextView) rootView.findViewById(R.id.lat_sm_text)).getText().toString());
				seconds = Double.valueOf(((TextView) rootView.findViewById(R.id.lat_ss_text)).getText().toString());
				coords[0] = Angle.fromDMS(degrees, minutes, seconds);
				degrees = Integer.valueOf(((TextView) rootView.findViewById(R.id.lon_sd_text)).getText().toString());
				minutes = Integer.valueOf(((TextView) rootView.findViewById(R.id.lon_sm_text)).getText().toString());
				seconds = Double.valueOf(((TextView) rootView.findViewById(R.id.lon_ss_text)).getText().toString());
				coords[1] = Angle.fromDMS(degrees, minutes, seconds);
				break;
			case 3:
				int easting = Integer.valueOf(((TextView) rootView.findViewById(R.id.utm_easting_text)).getText().toString());
				int northing = Integer.valueOf(((TextView) rootView.findViewById(R.id.utm_northing_text)).getText().toString());
				String zone = ((TextView) rootView.findViewById(R.id.utm_zone_text)).getText().toString();
				boolean hemi = ((RadioButton) rootView.findViewById(R.id.utm_hemi_s)).isChecked();
				try
				{
					if (!"".equals(zone))
					{
						int z = Integer.valueOf(zone);
						UTMCoord utm = UTMCoord.fromUTM(z, hemi ? AVKey.SOUTH : AVKey.NORTH, easting, northing);
						coords[0] = utm.getLatitude();
						coords[1] = utm.getLongitude();
					}
					else
					{
						UPSCoord ups = UPSCoord.fromUPS(hemi ? AVKey.SOUTH : AVKey.NORTH, easting, northing);
						coords[0] = ups.getLatitude();
						coords[1] = ups.getLongitude();
					}
				}
				catch (IllegalArgumentException e)
				{
					Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
				break;
			case 4:
				String mgrsstring = ((TextView) rootView.findViewById(R.id.mgrs_text)).getText().toString();
				try
				{
					MGRSCoord mgrs = MGRSCoord.fromString(mgrsstring);
					coords[0] = mgrs.getLatitude();
					coords[1] = mgrs.getLongitude();
				}
				catch (IllegalArgumentException e)
				{
					Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
				break;
		}
		return coords;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
	{
		int degrees, minutes;
		double mins, seconds;

		View rootView = getView();
		Angle[] coords = getLatLon();

		switch (position)
		{
			case 0:
				coordUtm.setVisibility(View.GONE);
				coordMgrs.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				((TextView) rootView.findViewById(R.id.lat_dd_text)).setText(StringFormatter.coordinate(0, coords[0].degrees));
				coordLatMin.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.GONE);
				coordLatDeg.setVisibility(View.VISIBLE);
				((TextView) rootView.findViewById(R.id.lon_dd_text)).setText(StringFormatter.coordinate(0, coords[1].degrees));
				coordLonMin.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.GONE);
				coordLonDeg.setVisibility(View.VISIBLE);
				break;
			case 1:
				coordUtm.setVisibility(View.GONE);
				coordMgrs.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords[0].degrees));
				mins = (Math.abs(coords[0].degrees) - degrees) * 60;
				degrees *= Math.signum(coords[0].degrees);
				((TextView) rootView.findViewById(R.id.lat_md_text)).setText(String.valueOf(degrees));
				((TextView) rootView.findViewById(R.id.lat_mm_text)).setText(String.valueOf(mins));
				coordLatDeg.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.GONE);
				coordLatMin.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords[1].degrees));
				mins = (Math.abs(coords[1].degrees) - degrees) * 60;
				degrees *= Math.signum(coords[1].degrees);
				((TextView) rootView.findViewById(R.id.lon_md_text)).setText(String.valueOf(degrees));
				((TextView) rootView.findViewById(R.id.lon_mm_text)).setText(String.valueOf(mins));
				coordLonDeg.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.GONE);
				coordLonMin.setVisibility(View.VISIBLE);
				break;
			case 2:
				coordUtm.setVisibility(View.GONE);
				coordMgrs.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords[0].degrees));
				mins = (Math.abs(coords[0].degrees) - degrees) * 60;
				degrees *= Math.signum(coords[0].degrees);
				minutes = (int) Math.floor(mins);
				seconds = (mins - minutes) * 60;
				((TextView) rootView.findViewById(R.id.lat_sd_text)).setText(String.valueOf(degrees));
				((TextView) rootView.findViewById(R.id.lat_sm_text)).setText(String.valueOf(minutes));
				((TextView) rootView.findViewById(R.id.lat_ss_text)).setText(String.valueOf(seconds));
				coordLatDeg.setVisibility(View.GONE);
				coordLatMin.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords[1].degrees));
				mins = (Math.abs(coords[1].degrees) - degrees) * 60;
				degrees *= Math.signum(coords[1].degrees);
				minutes = (int) Math.floor(mins);
				seconds = (mins - minutes) * 60;
				((TextView) rootView.findViewById(R.id.lon_sd_text)).setText(String.valueOf(degrees));
				((TextView) rootView.findViewById(R.id.lon_sm_text)).setText(String.valueOf(minutes));
				((TextView) rootView.findViewById(R.id.lon_ss_text)).setText(String.valueOf(seconds));
				coordLonDeg.setVisibility(View.GONE);
				coordLonMin.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.VISIBLE);
				break;
			case 3:
				try
				{
					coordDeg.setVisibility(View.GONE);
					coordMgrs.setVisibility(View.GONE);
					coordUtm.setVisibility(View.VISIBLE);
					if (coords[0].degrees < 84 && coords[0].degrees > -80)
					{
						UTMCoord utm = UTMCoord.fromLatLon(coords[0], coords[1]);
						((TextView) rootView.findViewById(R.id.utm_easting_text)).setText(String.valueOf(Math.round(utm.getEasting())));
						((TextView) rootView.findViewById(R.id.utm_northing_text)).setText(String.valueOf(Math.round(utm.getNorthing())));
						((TextView) rootView.findViewById(R.id.utm_zone_text)).setText(String.valueOf(utm.getZone()));
						if (AVKey.SOUTH.equals(utm.getHemisphere()))
							((RadioButton) rootView.findViewById(R.id.utm_hemi_s)).setChecked(true);
						else
							((RadioButton) rootView.findViewById(R.id.utm_hemi_n)).setChecked(true);
					}
					else
					{
						UPSCoord ups = UPSCoord.fromLatLon(coords[0], coords[1]);
						((TextView) rootView.findViewById(R.id.utm_easting_text)).setText(String.valueOf(Math.round(ups.getEasting())));
						((TextView) rootView.findViewById(R.id.utm_northing_text)).setText(String.valueOf(Math.round(ups.getNorthing())));
						if (AVKey.SOUTH.equals(ups.getHemisphere()))
							((RadioButton) rootView.findViewById(R.id.utm_hemi_s)).setChecked(true);
						else
							((RadioButton) rootView.findViewById(R.id.utm_hemi_n)).setChecked(true);
					}
					tooltipCallback.postDelayed(showTooltip, TooltipManager.TOOLTIP_DELAY_SHORT);
				}
				catch (IllegalArgumentException e)
				{
					Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
				break;
			case 4:
				try
				{
					MGRSCoord mgrs = MGRSCoord.fromLatLon(coords[0], coords[1]);
					coordDeg.setVisibility(View.GONE);
					coordUtm.setVisibility(View.GONE);
					coordMgrs.setVisibility(View.VISIBLE);
					((TextView) rootView.findViewById(R.id.mgrs_text)).setText(mgrs.toString());
				}
				catch (IllegalArgumentException e)
				{
					Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
				break;
		}
		curFormat = position;
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent)
	{
		// TODO Auto-generated method stub
	}
}
