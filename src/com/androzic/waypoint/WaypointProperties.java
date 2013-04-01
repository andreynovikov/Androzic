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

package com.androzic.waypoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.ui.ColorButton;
import com.androzic.ui.MarkerPickerActivity;
import com.androzic.util.StringFormatter;
import com.jhlabs.map.GeodeticPosition;
import com.jhlabs.map.ReferenceException;
import com.jhlabs.map.UTMReference;

public class WaypointProperties extends SherlockActivity implements OnItemSelectedListener
{
	private Waypoint waypoint;

	private TabHost tabHost;

	private TextView name;
	private TextView description;
	private TextView altitude;
	private TextView proximity;
	private ColorButton markercolor;
	private ColorButton textcolor;

	private ViewGroup coordDeg;
	private ViewGroup coordUtm;
	private ViewGroup coordLatDeg;
	private ViewGroup coordLatMin;
	private ViewGroup coordLatSec;
	private ViewGroup coordLonDeg;
	private ViewGroup coordLonMin;
	private ViewGroup coordLonSec;

	private int curFormat = -1;
	private String iconValue;
	private int route;

	private int defMarkerColor;
	private int defTextColor;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_waypoint_properties);

		int index = getIntent().getExtras().getInt("INDEX");
		route = getIntent().getExtras().getInt("ROUTE");

		tabHost = (TabHost) findViewById(R.id.tabhost);
		tabHost.setup();
		tabHost.addTab(tabHost.newTabSpec("main").setIndicator(getString(R.string.primary)).setContent(R.id.properties));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			tabHost.getTabWidget().getChildAt(0).getLayoutParams().height = 50;

		if (route == 0)
		{
			tabHost.addTab(tabHost.newTabSpec("advanced").setIndicator(getString(R.string.advanced)).setContent(R.id.advanced));
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
				tabHost.getTabWidget().getChildAt(1).getLayoutParams().height = 50;
		}
		else
		{
			findViewById(android.R.id.tabs).setVisibility(View.GONE);
		}

		if (savedInstanceState != null)
		{
			tabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}

		Androzic application = (Androzic) getApplication();
		if (route > 0)
		{
			waypoint = application.getRoute(route - 1).getWaypoints().get(index);
			findViewById(R.id.advanced).setVisibility(View.GONE);
			findViewById(R.id.icon_container).setVisibility(View.GONE);
		}
		else if (index >= 0)
		{
			waypoint = application.getWaypoint(index);
		}
		else
		{
			waypoint = new Waypoint();
			waypoint.date = Calendar.getInstance().getTime();
		}

		name = (TextView) findViewById(R.id.name_text);
		name.setText(waypoint.name);
		description = (TextView) findViewById(R.id.description_text);
		description.setText(waypoint.description);

		altitude = (TextView) findViewById(R.id.altitude_text);
		if (waypoint.altitude != Integer.MIN_VALUE)
			altitude.setText(String.valueOf(waypoint.altitude));
		proximity = (TextView) findViewById(R.id.proximity_text);
		if (waypoint.proximity != 0)
			proximity.setText(String.valueOf(waypoint.proximity));

		iconValue = null;
		ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
		icon.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_action_halt));
		if (application.iconsEnabled)
		{
			if (waypoint.drawImage)
			{
				Bitmap b = BitmapFactory.decodeFile(application.iconPath + File.separator + waypoint.image);
				if (b != null)
				{
					icon.setImageBitmap(b);
					iconValue = waypoint.image;
				}
			}
			icon.setOnClickListener(iconOnClickListener);
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				registerForContextMenu(icon);
			}
		}
		else
		{
			icon.setEnabled(false);
		}

		int set = waypoint.set == null ? 0 : application.getWaypointSets().indexOf(waypoint.set);

		ArrayList<String> items = new ArrayList<String>();
		for (WaypointSet wptset : application.getWaypointSets())
		{
			items.add(wptset.name);
		}

		Spinner spinner = (Spinner) findViewById(R.id.set_spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setSelection(set);

		int markerColorValue = waypoint.backcolor;
		int textColorValue = waypoint.textcolor;

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		defMarkerColor = settings.getInt(getString(R.string.pref_waypoint_color), getResources().getColor(R.color.waypoint));
		defTextColor = settings.getInt(getString(R.string.pref_waypoint_namecolor), getResources().getColor(R.color.waypointtext));

		if (markerColorValue == Integer.MIN_VALUE)
		{
			markerColorValue = defMarkerColor;
		}
		if (textColorValue == Integer.MIN_VALUE)
		{
			textColorValue = defTextColor;
		}

		markercolor = (ColorButton) findViewById(R.id.markercolor_button);
		markercolor.setColor(markerColorValue, defMarkerColor);

		textcolor = (ColorButton) findViewById(R.id.textcolor_button);
		textcolor.setColor(textColorValue, defTextColor);

		coordDeg = (ViewGroup) findViewById(R.id.coord_deg);
		coordUtm = (ViewGroup) findViewById(R.id.coord_utm);
		coordLatDeg = (ViewGroup) findViewById(R.id.coord_lat_deg);
		coordLatMin = (ViewGroup) findViewById(R.id.coord_lat_min);
		coordLatSec = (ViewGroup) findViewById(R.id.coord_lat_sec);
		coordLonDeg = (ViewGroup) findViewById(R.id.coord_lon_deg);
		coordLonMin = (ViewGroup) findViewById(R.id.coord_lon_min);
		coordLonSec = (ViewGroup) findViewById(R.id.coord_lon_sec);

		Spinner coordformat = (Spinner) findViewById(R.id.coordformat_spinner);
		coordformat.setOnItemSelectedListener(this);
		coordformat.setSelection(application.coordinateFormat);

		((Button) findViewById(R.id.done_button)).setOnClickListener(doneOnClickListener);
		((Button) findViewById(R.id.cancel_button)).setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				finish();
			}
		});
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		iconValue = savedInstanceState.getString("icon");
		ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
		if (iconValue != null)
		{
			Androzic application = (Androzic) getApplication();
			Bitmap b = BitmapFactory.decodeFile(application.iconPath + File.separator + iconValue);
			if (b != null)
				icon.setImageBitmap(b);
		}
		else
		{
			icon.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_action_halt));
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString("tab", tabHost.getCurrentTabTag());
		outState.putString("icon", iconValue);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == RESULT_OK)
		{
			iconValue = data.getStringExtra("icon");
			ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
			Androzic application = (Androzic) getApplication();
			Bitmap b = BitmapFactory.decodeFile(application.iconPath + File.separator + iconValue);
			if (b != null)
				icon.setImageBitmap(b);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		// menu.setHeaderTitle(application.getWaypoint(map.waypointSelected).name);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.marker_popup, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.change:
				startActivityForResult(new Intent(this, MarkerPickerActivity.class), 0);
				break;
			case R.id.remove:
				iconValue = null;
				ImageButton icon = (ImageButton) findViewById(R.id.icon_button);
				icon.setImageDrawable(this.getResources().getDrawable(R.drawable.ic_action_halt));
				break;
		}
		return true;
	}

	private OnClickListener iconOnClickListener = new OnClickListener() {
		@SuppressLint("NewApi")
		public void onClick(View v)
		{
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				v.showContextMenu();
			}
			else
			{
				PopupMenu popup = new PopupMenu(WaypointProperties.this, v);
				popup.getMenuInflater().inflate(R.menu.marker_popup, popup.getMenu());
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item)
					{
						return onContextItemSelected(item);
					}
				});
				popup.show();
			}
		}
	};

	private OnClickListener doneOnClickListener = new OnClickListener() {
		public void onClick(View v)
		{
			try
			{
				Androzic application = (Androzic) getApplication();

				if (name.getText().length() == 0)
					return;

				waypoint.name = name.getText().toString();

				waypoint.description = description.getText().toString();
				GeodeticPosition coords = getLatLon();
				waypoint.latitude = coords.lat;
				waypoint.longitude = coords.lon;

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
					waypoint.image = "";
					waypoint.drawImage = false;
				}
				else
				{
					waypoint.image = iconValue;
					waypoint.drawImage = true;
				}
				int markerColorValue = markercolor.getColor();
				if (markerColorValue != defMarkerColor)
					waypoint.backcolor = markerColorValue;
				int textColorValue = textcolor.getColor();
				if (textColorValue != defTextColor)
					waypoint.textcolor = textColorValue;

				int index = -1;
				if (route == 0)
				{
					if (waypoint.set == null)
					{
						application.addWaypoint(waypoint);
						index = application.getWaypointIndex(waypoint);
					}

					int set = ((Spinner) findViewById(R.id.set_spinner)).getSelectedItemPosition();
					waypoint.set = application.getWaypointSets().get(set);
				}

				if (index != -1)
				{
					setResult(RESULT_OK, new Intent().putExtra("index", index));
				}
				else
				{
					setResult(RESULT_OK);
				}
				finish();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				Toast.makeText(getBaseContext(), "Invalid input", Toast.LENGTH_LONG).show();
			}
		}
	};

	private GeodeticPosition getLatLon()
	{
		double degrees, minutes, seconds;

		GeodeticPosition coords = new GeodeticPosition();
		switch (curFormat)
		{
			case -1:
				coords.lat = waypoint.latitude;
				coords.lon = waypoint.longitude;
				break;
			case 0:
				coords.lat = Double.valueOf(((TextView) findViewById(R.id.lat_dd_text)).getText().toString());
				coords.lon = Double.valueOf(((TextView) findViewById(R.id.lon_dd_text)).getText().toString());
				break;
			case 1:
				degrees = Integer.valueOf(((TextView) findViewById(R.id.lat_md_text)).getText().toString());
				minutes = Double.valueOf(((TextView) findViewById(R.id.lat_mm_text)).getText().toString()) / 60;
				if (degrees != 0)
					minutes *= Math.signum(degrees);
				coords.lat = degrees + minutes;
				degrees = Integer.valueOf(((TextView) findViewById(R.id.lon_md_text)).getText().toString());
				minutes = Double.valueOf(((TextView) findViewById(R.id.lon_mm_text)).getText().toString()) / 60;
				if (degrees != 0)
					minutes *= Math.signum(degrees);
				coords.lon = degrees + minutes;
				break;
			case 2:
				degrees = Integer.valueOf(((TextView) findViewById(R.id.lat_sd_text)).getText().toString());
				minutes = Integer.valueOf(((TextView) findViewById(R.id.lat_sm_text)).getText().toString());
				seconds = Double.valueOf(((TextView) findViewById(R.id.lat_ss_text)).getText().toString()) / 60;
				minutes = (minutes + seconds) / 60;
				if (degrees != 0)
					minutes *= Math.signum(degrees);
				coords.lat = degrees + minutes;
				degrees = Integer.valueOf(((TextView) findViewById(R.id.lon_sd_text)).getText().toString());
				minutes = Integer.valueOf(((TextView) findViewById(R.id.lon_sm_text)).getText().toString());
				seconds = Double.valueOf(((TextView) findViewById(R.id.lon_ss_text)).getText().toString()) / 60;
				minutes = (minutes + seconds) / 60;
				if (degrees != 0)
					minutes *= Math.signum(degrees);
				coords.lon = degrees + minutes;
				break;
			case 3:
				int easting = Integer.valueOf(((TextView) findViewById(R.id.utm_easting_text)).getText().toString());
				int northing = Integer.valueOf(((TextView) findViewById(R.id.utm_northing_text)).getText().toString());
				int zone = Integer.valueOf(((TextView) findViewById(R.id.utm_zone_text)).getText().toString());
				boolean hemi = ((RadioButton) findViewById(R.id.utm_hemi_s)).isChecked();
				char band = UTMReference.getUTMNorthingZoneLetter(hemi, northing);
				try
				{
					UTMReference utm = new UTMReference(zone, band, easting, northing);
					coords = utm.toLatLng();
				}
				catch (ReferenceException e)
				{
					Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
		}
		return coords;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
	{
		int degrees, minutes;
		double min, seconds;

		GeodeticPosition coords = getLatLon();

		switch (position)
		{
			case 0:
				coordUtm.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				((TextView) findViewById(R.id.lat_dd_text)).setText(StringFormatter.coordinate(0, coords.lat));
				coordLatMin.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.GONE);
				coordLatDeg.setVisibility(View.VISIBLE);
				((TextView) findViewById(R.id.lon_dd_text)).setText(StringFormatter.coordinate(0, coords.lon));
				coordLonMin.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.GONE);
				coordLonDeg.setVisibility(View.VISIBLE);
				break;
			case 1:
				coordUtm.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords.lat));
				min = (Math.abs(coords.lat) - degrees) * 60;
				degrees *= Math.signum(coords.lat);
				((TextView) findViewById(R.id.lat_md_text)).setText(String.valueOf(degrees));
				((TextView) findViewById(R.id.lat_mm_text)).setText(String.valueOf(min));
				coordLatDeg.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.GONE);
				coordLatMin.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords.lon));
				min = (Math.abs(coords.lon) - degrees) * 60;
				degrees *= Math.signum(coords.lon);
				((TextView) findViewById(R.id.lon_md_text)).setText(String.valueOf(degrees));
				((TextView) findViewById(R.id.lon_mm_text)).setText(String.valueOf(min));
				coordLonDeg.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.GONE);
				coordLonMin.setVisibility(View.VISIBLE);
				break;
			case 2:
				coordUtm.setVisibility(View.GONE);
				coordDeg.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords.lat));
				min = (Math.abs(coords.lat) - degrees) * 60;
				degrees *= Math.signum(coords.lat);
				minutes = (int) Math.floor(min);
				seconds = (min - minutes) * 60;
				((TextView) findViewById(R.id.lat_sd_text)).setText(String.valueOf(degrees));
				((TextView) findViewById(R.id.lat_sm_text)).setText(String.valueOf(minutes));
				((TextView) findViewById(R.id.lat_ss_text)).setText(String.valueOf(seconds));
				coordLatDeg.setVisibility(View.GONE);
				coordLatMin.setVisibility(View.GONE);
				coordLatSec.setVisibility(View.VISIBLE);
				degrees = (int) Math.floor(Math.abs(coords.lon));
				min = (Math.abs(coords.lon) - degrees) * 60;
				degrees *= Math.signum(coords.lon);
				minutes = (int) Math.floor(min);
				seconds = (min - minutes) * 60;
				((TextView) findViewById(R.id.lon_sd_text)).setText(String.valueOf(degrees));
				((TextView) findViewById(R.id.lon_sm_text)).setText(String.valueOf(minutes));
				((TextView) findViewById(R.id.lon_ss_text)).setText(String.valueOf(seconds));
				coordLonDeg.setVisibility(View.GONE);
				coordLonMin.setVisibility(View.GONE);
				coordLonSec.setVisibility(View.VISIBLE);
				break;
			case 3:
				try
				{
					UTMReference utm = UTMReference.toUTMRef(new GeodeticPosition(coords.lat, coords.lon));
					coordDeg.setVisibility(View.GONE);
					coordUtm.setVisibility(View.VISIBLE);
					((TextView) findViewById(R.id.utm_easting_text)).setText(String.valueOf(Math.round(utm.getEasting())));
					((TextView) findViewById(R.id.utm_northing_text)).setText(String.valueOf(Math.round(utm.getNorthing())));
					((TextView) findViewById(R.id.utm_zone_text)).setText(String.valueOf(utm.getLngZone()));
					if (utm.isSouthernHemisphere())
					{
						((RadioButton) findViewById(R.id.utm_hemi_s)).setChecked(true);
					}
					else
					{
						((RadioButton) findViewById(R.id.utm_hemi_n)).setChecked(true);
					}
				}
				catch (ReferenceException e)
				{
					Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
		}
		curFormat = position;
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent)
	{
		// TODO Auto-generated method stub
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		waypoint = null;
		name = null;
		description = null;
		coordLatDeg = null;
		coordLatMin = null;
		coordLatSec = null;
		coordLonDeg = null;
		coordLonMin = null;
		coordLonSec = null;
	}
}