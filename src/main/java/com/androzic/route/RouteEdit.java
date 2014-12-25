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

package com.androzic.route;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.waypoint.OnWaypointActionListener;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

public class RouteEdit extends DialogFragment
{
	private OnWaypointActionListener waypointActionsCallback;

	private Route route;

	private DragSortListView listView;
	private ArrayAdapter<String> listAdapter;
	private DragSortController dragController;

	public RouteEdit()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
	public RouteEdit(Route route)
	{
		this.route = route;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	/**
	 * Called in onCreateView. Override this to provide a custom
	 * DragSortController.
	 */
	protected DragSortController buildController(DragSortListView dslv)
	{
		DragSortController controller = new DragSortController(dslv);
		controller.setDragHandleId(R.id.drag_handle);
		// controller.setClickRemoveId(R.id.click_remove);
		controller.setRemoveEnabled(true);
		controller.setSortEnabled(true);
		controller.setDragInitMode(DragSortController.ON_DOWN);
		controller.setRemoveMode(DragSortController.FLING_REMOVE);
		return controller;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		listView = (DragSortListView) inflater.inflate(R.layout.act_route_edit, container, false);

		dragController = buildController(listView);
		listView.setFloatViewManager(dragController);
		listView.setOnTouchListener(dragController);
		listView.setOnItemClickListener(clickListener);
		listView.setDragEnabled(true);

		return listView;
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

		listView.setDropListener(mDropListener);
		listView.setRemoveListener(mRemoveListener);

		List<Waypoint> waypoints = route.getWaypoints();
		List<String> list = new ArrayList<String>(waypoints.size());
		for (int i = 0; i < waypoints.size(); i++)
		{
			list.add(waypoints.get(i).name);
		}

		//TODO Should use custom adapter for proper item Id's
		listAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_sortable, R.id.text, list);
		listView.setAdapter(listAdapter);

		getDialog().setTitle(route.name);
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private AdapterView.OnItemClickListener clickListener = new AdapterView.OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			waypointActionsCallback.onWaypointView(route.getWaypoint(position));
		}
	};

	private final DragSortListView.DropListener mDropListener = new DragSortListView.DropListener() {
		@Override
		public void drop(int from, int to)
		{
			if (from != to)
			{
				String item = listAdapter.getItem(from);
				listAdapter.remove(item);
				listAdapter.insert(item, to);
				Waypoint wpt = route.getWaypoint(from);
				route.removeWaypoint(wpt);
				route.addWaypoint(to, wpt);
			}
		}
	};

	private final DragSortListView.RemoveListener mRemoveListener = new DragSortListView.RemoveListener() {
		@Override
		public void remove(int which)
		{
			listAdapter.remove(listAdapter.getItem(which));
			route.removeWaypoint(route.getWaypoint(which));
		}
	};
}
