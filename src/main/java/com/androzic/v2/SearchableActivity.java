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

package com.androzic.v2;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.provider.SuggestionProvider;
import com.androzic.util.CoordinateParser;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;
import com.jhlabs.map.GeodeticPosition;

public class SearchableActivity extends AppCompatActivity implements AdapterView.OnItemClickListener
{
	private static final int MSG_FINISH = 1;

	private ListView listView;
	private ProgressBar progressBar;
	
	private static final List<Object> results = new ArrayList<>();
	private static SearchThread thread;
	private FinishHandler finishHandler;

	private SearchResultsListAdapter adapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		Toolbar toolbar = (Toolbar) findViewById(R.id.action_toolbar);
	    setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		
	    listView = (ListView) findViewById(android.R.id.list);

		finishHandler = new FinishHandler(this);

		if (Intent.ACTION_SEARCH.equals(getIntent().getAction()))
		{
			String query = getIntent().getStringExtra(SearchManager.QUERY);
			SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE);
			suggestions.saveRecentQuery(query, null);
		}

		adapter = new SearchResultsListAdapter(this, results);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
		
		progressBar = (ProgressBar) findViewById(R.id.progressbar);
		
		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.search_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				thread = null;
				finish();
				return true;
			case R.id.action_clear:
				SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE);
				suggestions.clearHistory();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void handleIntent(Intent intent)
	{
		if (Intent.ACTION_SEARCH.equals(intent.getAction()))
		{
			String query = intent.getStringExtra(SearchManager.QUERY);
			doSearch(query);
		}
	}

	private void doSearch(final String query)
	{
		if (query.length() == 0)
		{
			finish();
			return;
		}

		progressBar.setVisibility(View.VISIBLE);

		if (thread == null)
		{
			synchronized (results)
			{
				results.clear();
			}
			adapter.notifyDataSetChanged();
			thread = new SearchThread(finishHandler, query);
			thread.start();
		}
		else if (thread.isAlive())
		{
			thread.setHandler(finishHandler);			
		}
		else
		{
			onSearchFinished();
		}
	}

	@Override
	public void onBackPressed()
	{
		thread = null;
		super.onBackPressed();
	}
	
	private void onSearchFinished()
	{
		TextView emptyView = (TextView) findViewById(android.R.id.empty);
		if (emptyView != null)
		{
			emptyView.setText(R.string.msg_nothing_found);
			listView.setEmptyView(emptyView);
		}
		progressBar.setVisibility(View.GONE);
		adapter.notifyDataSetChanged();
	}

	@SuppressLint("HandlerLeak")
	private class FinishHandler extends Handler
	{
		private final WeakReference<SearchableActivity> target;

		FinishHandler(SearchableActivity activity)
		{
			this.target = new WeakReference<>(activity);
		}

		public void handleMessage(Message msg)
		{
			SearchableActivity activity = target.get();
			switch (msg.what)
			{
				case MSG_FINISH:
					activity.onSearchFinished();
					break;
			}
		}
	}

	private class SearchThread extends Thread
	{
		private Handler handler;
		private String query;

		SearchThread(Handler h, String q)
		{
			handler = h;
			query = q;
		}

		public synchronized void setHandler(Handler h)
		{
			handler = h;
		}

		public void run()
		{
			Androzic application = Androzic.getApplication();
			Locale locale = Locale.getDefault();

			// Coordinates
			double c[] = CoordinateParser.parse(query);
			if (!Double.isNaN(c[0]) && !Double.isNaN(c[1]))
			{
				GeodeticPosition coordinates = new GeodeticPosition(c[0], c[1]);
				synchronized (results)
				{
					results.add(coordinates);
				}
				runOnUiThread(updateResults);
			}

			String lq = query.toLowerCase(locale);

			// Waypoints
			for (Waypoint waypoint : application.getWaypoints())
			{
				if (waypoint.name.toLowerCase(locale).contains(lq) || waypoint.description.toLowerCase(locale).contains(lq))
				{
					synchronized (results)
					{
						results.add(waypoint);
					}
					runOnUiThread(updateResults);
				}
			}

			// Routes
			for (Route route : application.getRoutes())
			{
				if (route.name.toLowerCase(locale).contains(lq) || route.description.toLowerCase(locale).contains(lq))
				{
					synchronized (results)
					{
						results.add(route);
					}
					runOnUiThread(updateResults);
					continue;
				}
				for (Waypoint waypoint : route.getWaypoints())
				{
					if (waypoint.name.toLowerCase(locale).contains(lq) || waypoint.description.toLowerCase(locale).contains(lq))
					{
						synchronized (results)
						{
							results.add(route);
						}
						runOnUiThread(updateResults);
						break;
					}
				}
			}

			// Tracks
			for (Track track : application.getTracks())
			{
				if (track.name.toLowerCase(locale).contains(lq) || track.description.toLowerCase(locale).contains(lq))
				{
					synchronized (results)
					{
						results.add(track);
					}
					runOnUiThread(updateResults);
				}
			}
			
			// Addresses
			try
			{
				Geocoder geocoder = new Geocoder(application);
				List<Address> addresses = geocoder.getFromLocationName(query, 15);
				if (addresses != null && addresses.size() > 0)
				{
					synchronized (results)
					{
						results.addAll(addresses);
					}
					runOnUiThread(updateResults);
				}
			}
			catch (IOException e)
			{
				runOnUiThread(noConnection);
			}

			synchronized (this)
			{
				handler.sendEmptyMessage(MSG_FINISH);
			}
		}
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
			adapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		Object item = adapter.getItem(position);
		Androzic application = (Androzic) getApplication();
		double[] location = application.getLocation();

		if (item instanceof GeodeticPosition)
		{
			GeodeticPosition coordinates = (GeodeticPosition) item;
			location[0] = coordinates.lat;
			location[1] = coordinates.lon;
		}
		else if (item instanceof Waypoint)
		{
			Waypoint waypoint = (Waypoint) item;
			location[0] = waypoint.latitude;
			location[1] = waypoint.longitude;
		}
		else if (item instanceof Route)
		{
			Route route = (Route) item;
			route.show = true;
			Waypoint waypoint = route.getWaypoint(0);
			location[0] = waypoint.latitude;
			location[1] = waypoint.longitude;
		}
		else if (item instanceof Track)
		{
			Track track = (Track) item;
			Track.TrackPoint tp = track.getPoint(0);
			location[0] = tp.latitude;
			location[1] = tp.longitude;
		}
		else if (item instanceof Address)
		{
			Address address = (Address) item;
			location[0] = address.getLatitude();
			location[1] = address.getLongitude();
		}
		
		application.ensureVisible(location[0], location[1]);
		finish();
	}

	public class SearchResultsListAdapter extends BaseAdapter
	{
		List<Object> mItems;
		private LayoutInflater mInflater;
		private Androzic mApplication;
		private double[] mLocation;

		private int mCoordinatesItemLayout;
		private int mWaypointItemLayout;
		private int mRouteItemLayout;
		private int mTrackItemLayout;
		private int mAddressItemLayout;

		private float mDensity;

		private Paint mWaypointBorderPaint;
		private Paint mWaypointFillPaint;
		private int mPointWidth;

		private Path mRouteLinePath;
		private Paint mRouteFillPaint;
		private Paint mRouteLinePaint;
		private Paint mRouteBorderPaint;
		private int mRouteWidth;

		public SearchResultsListAdapter(Context context, List<Object> items)
		{
			mItems = items;

			mCoordinatesItemLayout = R.layout.list_item_coordinates;
			mWaypointItemLayout = R.layout.list_item_waypoint;
			mRouteItemLayout = R.layout.list_item_route;
			mTrackItemLayout = R.layout.list_item_track;
			mAddressItemLayout = R.layout.list_item_address;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mDensity = context.getResources().getDisplayMetrics().density;

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

			mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.getResources().getInteger(R.integer.def_waypoint_width));
			mWaypointFillPaint = new Paint();
			mWaypointFillPaint.setAntiAlias(false);
			mWaypointFillPaint.setStrokeWidth(1);
			mWaypointFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mWaypointFillPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_color), context.getResources().getColor(R.color.waypoint)));
			mWaypointBorderPaint = new Paint();
			mWaypointBorderPaint.setAntiAlias(false);
			mWaypointBorderPaint.setStrokeWidth(1);
			mWaypointBorderPaint.setStyle(Paint.Style.STROKE);
			mWaypointBorderPaint.setColor(context.getResources().getColor(R.color.waypointtext));
			mWaypointBorderPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_namecolor), context.getResources().getColor(R.color.waypointtext)));

			mRouteWidth = settings.getInt(context.getString(R.string.pref_route_linewidth), context.getResources().getInteger(R.integer.def_route_linewidth));
			mRouteLinePath = new Path();
			mRouteLinePath.setLastPoint(12 * mDensity, 5 * mDensity);
			mRouteLinePath.lineTo(24 * mDensity, 12 * mDensity);
			mRouteLinePath.lineTo(15 * mDensity, 24 * mDensity);
			mRouteLinePath.lineTo(28 * mDensity, 35 * mDensity);
			mRouteFillPaint = new Paint();
			mRouteFillPaint.setAntiAlias(false);
			mRouteFillPaint.setStrokeWidth(1);
			mRouteFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mRouteFillPaint.setColor(context.getResources().getColor(R.color.routewaypoint));
			mRouteLinePaint = new Paint();
			mRouteLinePaint.setAntiAlias(true);
			mRouteLinePaint.setStrokeWidth(mRouteWidth * mDensity);
			mRouteLinePaint.setStyle(Paint.Style.STROKE);
			mRouteLinePaint.setColor(context.getResources().getColor(R.color.routeline));
			mRouteBorderPaint = new Paint();
			mRouteBorderPaint.setAntiAlias(true);
			mRouteBorderPaint.setStrokeWidth(1);
			mRouteBorderPaint.setStyle(Paint.Style.STROKE);
			mRouteBorderPaint.setColor(context.getResources().getColor(R.color.routeline));

			mApplication = Androzic.getApplication();
			mLocation = mApplication.getLocation();
		}

		@Override
		public Object getItem(int position)
		{
			return mItems.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return position;
		}

		@Override
		public int getCount()
		{
			return mItems.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			Object item = getItem(position);

			boolean isCoordinates = item instanceof GeodeticPosition;
			boolean isWaypoint = item instanceof Waypoint;
			boolean isRoute = item instanceof Route;
			boolean isTrack = item instanceof Track;
			boolean isAddress = item instanceof Address;

			GeodeticPosition coordinates = null;
			Waypoint waypoint = null;
			Route route = null;
			Track track = null;
			Address address = null;

			int layout;
			if (isCoordinates)
			{
				layout = mCoordinatesItemLayout;
				coordinates = (GeodeticPosition) item;
			}
			else if (isWaypoint)
			{
				layout = mWaypointItemLayout;
				waypoint = (Waypoint) item;
			}
			else if (isRoute)
			{
				layout = mRouteItemLayout;
				route = (Route) item;
			}
			else if (isTrack)
			{
				layout = mTrackItemLayout;
				track = (Track) item;
			}
			else if (isAddress)
			{
				layout = mAddressItemLayout;
				address = (Address) item;
			}
			else
			{
				layout = android.R.layout.simple_list_item_1;
			}

			// TODO Think how to reuse convertView
			View v = mInflater.inflate(layout, parent, false);

			if (isCoordinates)
			{
				TextView textView = (TextView) v.findViewById(R.id.name);
				String coords = StringFormatter.coordinates(" ", coordinates.lat, coordinates.lon);
				textView.setText(coords);
				double dist = Geo.distance(mLocation[0], mLocation[1], coordinates.lat, coordinates.lon);
				double bearing = Geo.bearing(mLocation[0], mLocation[1], coordinates.lat, coordinates.lon);
				String distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
				textView = (TextView) v.findViewById(R.id.distance);
				textView.setText(distance);
			}
			else if (isWaypoint)
			{
				TextView textView = (TextView) v.findViewById(R.id.name);
				textView.setText(waypoint.name);
				String coords = StringFormatter.coordinates(" ", waypoint.latitude, waypoint.longitude);
				textView = (TextView) v.findViewById(R.id.coordinates);
				textView.setText(coords);
				double dist = Geo.distance(mLocation[0], mLocation[1], waypoint.latitude, waypoint.longitude);
				double bearing = Geo.bearing(mLocation[0], mLocation[1], waypoint.latitude, waypoint.longitude);
				String distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
				textView = (TextView) v.findViewById(R.id.distance);
				textView.setText(distance);
				ImageView icon = (ImageView) v.findViewById(R.id.icon);
				Bitmap b = null;
				if (waypoint.drawImage)
				{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inScaled = false;
					b = BitmapFactory.decodeFile(mApplication.markerPath + File.separator + waypoint.marker, options);
				}
				int h = b != null ? b.getHeight() : 30;
				Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), h, Config.ARGB_8888);
				bm.eraseColor(Color.TRANSPARENT);
				Canvas bc = new Canvas(bm);
				if (b != null)
				{
					b.setDensity(Bitmap.DENSITY_NONE);
					int l = (int) ((38 * mDensity - b.getWidth()) / 2);
					bc.drawBitmap(b, null, new Rect(l, 0, b.getWidth() + l, b.getHeight()), null);
				}
				else
				{
					int tc = 0, bgc = 0;
					if (waypoint.textcolor != Integer.MIN_VALUE)
					{
						tc = mWaypointBorderPaint.getColor();
						mWaypointBorderPaint.setColor(waypoint.textcolor);
					}
					if (waypoint.backcolor != Integer.MIN_VALUE)
					{
						bgc = mWaypointFillPaint.getColor();
						mWaypointFillPaint.setColor(waypoint.backcolor);
					}
					@SuppressWarnings("SuspiciousNameCombination")
					Rect rect = new Rect(0, 0, mPointWidth, mPointWidth);
					bc.translate((38 * mDensity - mPointWidth) / 2, (30 - mPointWidth) / 2);
					bc.drawRect(rect, mWaypointBorderPaint);
					rect.inset(1, 1);
					bc.drawRect(rect, mWaypointFillPaint);
					if (waypoint.textcolor != Integer.MIN_VALUE)
					{
						mWaypointBorderPaint.setColor(tc);
					}
					if (waypoint.backcolor != Integer.MIN_VALUE)
					{
						mWaypointFillPaint.setColor(bgc);
					}
				}
				icon.setImageBitmap(bm);
			}
			else if (isRoute)
			{
				TextView textView = (TextView) v.findViewById(R.id.name);
				textView.setText(route.name);
				String distance = StringFormatter.distanceH(route.distance);
				textView = (TextView) v.findViewById(R.id.distance);
				textView.setText(distance);
				textView = (TextView) v.findViewById(R.id.filename);
				if (route.filepath != null)
				{
					String filepath = route.filepath.startsWith(mApplication.dataPath) ? route.filepath.substring(mApplication.dataPath.length() + 1, route.filepath.length()) : route.filepath;
					textView.setText(filepath);
				}
				ImageView icon = (ImageView) v.findViewById(R.id.icon);
				Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), (int) (40 * mDensity), Config.ARGB_8888);
				bm.eraseColor(Color.TRANSPARENT);
				Canvas bc = new Canvas(bm);
				mRouteLinePaint.setColor(route.lineColor);
				mRouteBorderPaint.setColor(route.lineColor);
				bc.drawPath(mRouteLinePath, mRouteLinePaint);				
				int half = Math.round(mPointWidth / 4);				
				bc.drawCircle(12 * mDensity, 5 * mDensity, half, mRouteFillPaint);
				bc.drawCircle(12 * mDensity, 5 * mDensity, half, mRouteBorderPaint);
				bc.drawCircle(24 * mDensity, 12 * mDensity, half, mRouteFillPaint);
				bc.drawCircle(24 * mDensity, 12 * mDensity, half, mRouteBorderPaint);
				bc.drawCircle(15 * mDensity, 24 * mDensity, half, mRouteFillPaint);
				bc.drawCircle(15 * mDensity, 24 * mDensity, half, mRouteBorderPaint);
				bc.drawCircle(28 * mDensity, 35 * mDensity, half, mRouteFillPaint);
				bc.drawCircle(28 * mDensity, 35 * mDensity, half, mRouteBorderPaint);
				icon.setImageBitmap(bm);
			}
			else if (isTrack)
			{
				TextView textView = (TextView) v.findViewById(R.id.name);
				textView.setText(track.name);
				String distance = StringFormatter.distanceH(track.distance);
				textView = (TextView) v.findViewById(R.id.distance);
				textView.setText(distance);
				textView = (TextView) v.findViewById(R.id.filename);
				if (track.filepath != null)
				{
					String filepath = track.filepath.startsWith(mApplication.dataPath) ? track.filepath.substring(mApplication.dataPath.length() + 1, track.filepath.length()) : track.filepath;
					textView.setText(filepath);
				}
				ImageView icon = (ImageView) v.findViewById(R.id.icon);
				Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), (int) (40 * mDensity), Config.ARGB_8888);
				bm.eraseColor(Color.TRANSPARENT);
				Canvas bc = new Canvas(bm);
				mRouteLinePaint.setColor(track.color);
				bc.drawPath(mRouteLinePath, mRouteLinePaint);
				icon.setImageBitmap(bm);
			}
			else if (isAddress)
			{
				String name = address.getFeatureName();
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < address.getMaxAddressLineIndex(); i++)
				{
					sb.append(address.getAddressLine(i));
					if (i < (address.getMaxAddressLineIndex() - 1))
					{
						sb.append(" ");
					}
				}
				String addr = sb.toString();
				if (!addr.contains(name))
					name = name + " " + addr;
				else
					name = addr;
				TextView textView = (TextView) v.findViewById(R.id.name);
				textView.setText(name);
				String coords = StringFormatter.coordinates(" ", address.getLatitude(), address.getLongitude());
				textView = (TextView) v.findViewById(R.id.coordinates);
				textView.setText(coords);
				double dist = Geo.distance(mLocation[0], mLocation[1], address.getLatitude(), address.getLongitude());
				double bearing = Geo.bearing(mLocation[0], mLocation[1], address.getLatitude(), address.getLongitude());
				String distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
				textView = (TextView) v.findViewById(R.id.distance);
				textView.setText(distance);
			}

			return v;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}
	}
}
