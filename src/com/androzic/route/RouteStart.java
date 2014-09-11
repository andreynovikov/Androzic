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

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;

public class RouteStart extends DialogFragment
{
	private OnRouteActionListener routeActionsCallback;

    private Route route;
	private RadioButton forward;
	private RadioButton reverse;
	
	public void setRoute(Route route)
	{
		this.route = route;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.act_route_start, container);
		forward = (RadioButton) view.findViewById(R.id.forward);
		reverse = (RadioButton) view.findViewById(R.id.reverse);

	    Button navigate = (Button) view.findViewById(R.id.navigate_button);
	    navigate.setOnClickListener(navigateOnClickListener);
		
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
			routeActionsCallback = (OnRouteActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnRouteActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();
        updateRouteInfo();
	}

	public void updateRouteInfo()
	{
		if (route.length() < 2)
		{
			Toast.makeText(getActivity(), R.string.err_shortroute, Toast.LENGTH_LONG).show();
			// "Close" fragment
			getFragmentManager().popBackStack();
    		return;
		}

		Dialog dialog = getDialog();

		dialog.setTitle(route.name);

		Waypoint start = route.getWaypoint(0);
		Waypoint end = route.getWaypoint(route.length()-1);

		forward.setText(start.name + " to " + end.name);
		reverse.setText(end.name + " to " + start.name);

		forward.setChecked(true);
    }

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private OnClickListener navigateOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	int dir = forward.isChecked() ? NavigationService.DIRECTION_FORWARD : NavigationService.DIRECTION_REVERSE;
        	routeActionsCallback.onRouteNavigate(route, dir, -1);
			dismiss();
        }
    };
}
