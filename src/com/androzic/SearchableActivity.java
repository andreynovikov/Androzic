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

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.provider.SuggestionProvider;
import com.androzic.util.CoordinateParser;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class SearchableActivity extends ListActivity
{
	List<Address> addresses;
	List<Map<String, String>> addressData = new ArrayList<Map<String, String>>();

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";

	final DecimalFormat coordFormat = new DecimalFormat("0.000000");

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (Intent.ACTION_SEARCH.equals(getIntent().getAction()))
	    {
	        String query = getIntent().getStringExtra(SearchManager.QUERY);
	        SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE);
	        suggestions.saveRecentQuery(query, null);
	    }

		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		setIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent)
	{
		if (Intent.ACTION_SEARCH.equals(intent.getAction()))
		{
			String query = intent.getStringExtra(SearchManager.QUERY);
			doSearch(query);
		}
	}

	private void doSearch(String query)
	{
		if (query.length() == 0)
		{
			finish();
			return;
		}

		final ProgressDialog pd = new ProgressDialog(this);

		pd.setIndeterminate(true);
		pd.setMessage(getString(R.string.msg_wait));
		pd.show();

		final String q = query;

		new Thread(new Runnable() {
			public void run()
			{
				Androzic application = (Androzic) getApplication();

				addressData.clear();
				Geocoder geocoder = new Geocoder(SearchableActivity.this);

				try
				{
					addresses = geocoder.getFromLocationName(q, 5);
				}
				catch (IOException e)
				{
					runOnUiThread(noConnection);
				}
				
				String lq = q.toLowerCase();
				
				for (Track track : application.getTracks())
				{
					if (track.name.toLowerCase().contains(lq) || track.description.toLowerCase().contains(lq))
					{
						Address address = new Address(Locale.getDefault());
						try
						{
							Track.TrackPoint tp = track.getPoint(0);
							address.setLatitude(tp.latitude);
							address.setLongitude(tp.longitude);
							address.setFeatureName(track.name);
							if (addresses == null)
							{
								addresses = new ArrayList<Address>();
							}
							addresses.add(0, address);
						}
						catch (IndexOutOfBoundsException e)
						{
						}
					}
				}

				for (Route route : application.getRoutes())
				{
					if (route.name.toLowerCase().contains(lq) || route.description.toLowerCase().contains(lq))
					{
						Address address = new Address(Locale.getDefault());
						try
						{
							Waypoint waypoint = route.getWaypoint(0);
							address.setLatitude(waypoint.latitude);
							address.setLongitude(waypoint.longitude);
							address.setFeatureName(route.name);
							if (addresses == null)
							{
								addresses = new ArrayList<Address>();
							}
							addresses.add(0, address);
						}
						catch (IndexOutOfBoundsException e)
						{
						}
					}
				}

				for (Waypoint waypoint : application.getWaypoints())
				{
					if (waypoint.name.toLowerCase().contains(lq) || waypoint.description.toLowerCase().contains(lq))
					{
						Address address = new Address(Locale.getDefault());
						address.setLatitude(waypoint.latitude);
						address.setLongitude(waypoint.longitude);
						address.setFeatureName(waypoint.name);
						if (addresses == null)
						{
							addresses = new ArrayList<Address>();
						}
						addresses.add(0, address);						
					}
				}

				double c[] = CoordinateParser.parse(q);
				if (! Double.isNaN(c[0]) && ! Double.isNaN(c[1]))
				{
					Address address = new Address(Locale.getDefault());
					address.setLatitude(c[0]);
					address.setLongitude(c[1]);
					address.setFeatureName(q);
					if (addresses == null)
					{
						addresses = new ArrayList<Address>();
					}
					addresses.add(0, address);
				}

				if (addresses != null && addresses.isEmpty() == false)
				{
					Map<String, String> group;
					double[] loc = application.getLocation();

					for (Address address : addresses)
					{
						if (address.hasLatitude() && address.hasLongitude())
						{
							group = new HashMap<String, String>();

							String name = address.getFeatureName();

							StringBuilder b = new StringBuilder();
							for (int i = 0; i < address.getMaxAddressLineIndex(); i++)
							{
								b.append(address.getAddressLine(i));
								if (i < (address.getMaxAddressLineIndex() - 1))
								{
									b.append(" ");
								}
							}

							String addr = b.toString();
							if (! addr.contains(name))
								name = name + " " + addr;
							else
								name = addr;

							group.put(KEY_NAME, name);
							double dist = Geo.distance(loc[0], loc[1], address.getLatitude(), address.getLongitude());
							double bearing = Geo.bearing(loc[0], loc[1], address.getLatitude(), address.getLongitude());
							String desc = StringFormatter.coordinates(application.coordinateFormat, " ", address.getLatitude(), address.getLongitude())
									+ " | " + StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
							group.put(KEY_DESC, desc);

							addressData.add(group);
						}
					}
					handler.post(updateResults);
				}
				pd.dismiss();
			}
		}).start();
	}

	final Runnable noConnection = new Runnable() {
		public void run()
		{
			Toast.makeText(getBaseContext(), getString(R.string.err_noconnection), Toast.LENGTH_LONG).show();
		}
	};

	final Runnable updateResults = new Runnable() {
		public void run()
		{
			setListAdapter(new SimpleAdapter(SearchableActivity.this, addressData, android.R.layout.simple_list_item_2, new String[] {
					KEY_NAME, KEY_DESC }, new int[] { android.R.id.text1, android.R.id.text2 }));
			getListView().setTextFilterEnabled(true);
		}
	};

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);

		final Address address = addresses.get(position);
		Androzic application = (Androzic) getApplication();
		application.ensureVisible(new Waypoint(address.getLatitude(), address.getLongitude()));
		finish();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		if (addresses != null)
			addresses.clear();
		addressData.clear();
	}

}
