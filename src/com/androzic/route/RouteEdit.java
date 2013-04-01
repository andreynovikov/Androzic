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

package com.androzic.route;

import com.actionbarsherlock.app.SherlockListActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.waypoint.WaypointProperties;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import com.ericharlow.DragNDrop.DragListener;
import com.ericharlow.DragNDrop.DragNDropAdapter;
import com.ericharlow.DragNDrop.DragNDropListView;
import com.ericharlow.DragNDrop.DropListener;
import com.ericharlow.DragNDrop.RemoveListener;

public class RouteEdit extends SherlockListActivity implements DropListener, OnClickListener, RemoveListener, DragListener
{
	private Route route;
	private int index;

	private int backgroundColor = 0x00000000;
	private int defaultBackgroundColor;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_route_edit);

		index = getIntent().getExtras().getInt("INDEX");

		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);
		setTitle(route.name);

		ListView listView = getListView();

		if (listView instanceof DragNDropListView)
		{
			((DragNDropListView) listView).setDropListener(this);
			((DragNDropListView) listView).setRemoveListener(this);
			((DragNDropListView) listView).setDragListener(this);
		}

		findViewById(R.id.done_button).setOnClickListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		List<Waypoint> waypoints = route.getWaypoints();
		ArrayList<String> content = new ArrayList<String>(waypoints.size());
		for (int i = 0; i < waypoints.size(); i++)
		{
			content.add(waypoints.get(i).name);
		}
		setListAdapter(new DragNDropAdapter(this, new int[] { R.layout.dragitem }, new int[] { R.id.TextView01 }, content));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);
		startActivity(new Intent(this, WaypointProperties.class).putExtra("INDEX", position).putExtra("ROUTE", index+1));
	}
	
	@Override
	public void onClick(View v)
	{
		setResult(RESULT_OK);
		finish();
	}

	@Override
	public void onRemove(int which)
	{
		ListAdapter adapter = getListAdapter();
		if (adapter instanceof DragNDropAdapter)
		{
			((DragNDropAdapter) adapter).onRemove(which);
			route.removeWaypoint(route.getWaypoint(which));
			getListView().invalidateViews();
		}
	}

	@Override
	public void onDrop(int from, int to)
	{
		ListAdapter adapter = getListAdapter();
		if (adapter instanceof DragNDropAdapter)
		{
			((DragNDropAdapter) adapter).onDrop(from, to);
			Waypoint wpt = route.getWaypoint(from);
			route.removeWaypoint(wpt);
			route.addWaypoint(from < to ? to - 1 : to, wpt);
			getListView().invalidateViews();
		}
	}

	@Override
	public void onDrag(int x, int y, ListView listView)
	{
	}

	@Override
	public void onStartDrag(View itemView)
	{
		itemView.setVisibility(View.INVISIBLE);
		defaultBackgroundColor = itemView.getDrawingCacheBackgroundColor();
		itemView.setBackgroundColor(backgroundColor);
	}

	@Override
	public void onStopDrag(View itemView)
	{
		itemView.setVisibility(View.VISIBLE);
		itemView.setBackgroundColor(defaultBackgroundColor);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		route = null;
	}
}
