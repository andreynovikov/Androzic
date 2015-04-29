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

package com.androzic.route;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;

public class RouteStart extends DialogFragment
{
	private OnRouteActionListener routeActionsCallback;

    private Route route;

    public RouteStart()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
	public RouteStart(Route route)
	{
		this.route = route;
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
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.routestart_name));
		final View view = getActivity().getLayoutInflater().inflate(R.layout.act_route_start, null);
		builder.setView(view);
		builder.setPositiveButton(R.string.navigate, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				RadioButton forward = (RadioButton) view.findViewById(R.id.forward);
	        	int dir = forward.isChecked() ? NavigationService.DIRECTION_FORWARD : NavigationService.DIRECTION_REVERSE;
	        	routeActionsCallback.onRouteNavigate(route, dir, -1);
				dismiss();
			}
		});
		updateRouteInfo(view);
		return builder.create();
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			routeActionsCallback = (OnRouteActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnRouteActionListener");
		}
	}

	public void updateRouteInfo(View view)
	{
		if (route.length() < 2)
		{
			Toast.makeText(getActivity(), R.string.err_shortroute, Toast.LENGTH_LONG).show();
			// "Close" fragment
			getFragmentManager().popBackStack();
    		return;
		}

		TextView name = (TextView) view.findViewById(R.id.name);
		name.setText(route.name);

		Waypoint start = route.getWaypoint(0);
		Waypoint end = route.getWaypoint(route.length()-1);

		RadioButton forward = (RadioButton) view.findViewById(R.id.forward);
		RadioButton reverse = (RadioButton) view.findViewById(R.id.reverse);

		Resources resources = getResources();
		String from = resources.getString(R.string.from_start);
		String to = resources.getString(R.string.start_to_end);
		forward.setText(from + " " + start.name + " " + to + " " + end.name);
		reverse.setText(from + " " + end.name + " " + to + " " + start.name);

		forward.setChecked(true);
    }

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}
}
