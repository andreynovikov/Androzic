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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.ui.ColorButton;

public class RouteProperties extends Fragment
{
	private Route route;
	
	private TextView name;
	private CheckBox show;
	private ColorButton color;
	private Spinner width;
	private ArrayAdapter<String> widthAdapter;

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
        // FIXME Should have its own layout
		View rootView = inflater.inflate(R.layout.act_track_properties, container, false);

		name = (TextView) rootView.findViewById(R.id.name_text);
		name.setText(route.name);
		show = (CheckBox) rootView.findViewById(R.id.show_check);
        show.setChecked(route.show);
        color = (ColorButton) rootView.findViewById(R.id.color_button);
        color.setColor(route.lineColor, getResources().getColor(R.color.routeline));
		width = (Spinner) rootView.findViewById(R.id.width_spinner);
		widthAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item);
		widthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		width.setAdapter(widthAdapter);

		return rootView;
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (route != null)
			updateRouteProperties();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.routeproperties_name);
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
				try
	        	{
	        		route.name = name.getText().toString();
	        		route.show = show.isChecked();
	        		route.lineColor = color.getColor();
					String w = (String) width.getItemAtPosition(width.getSelectedItemPosition());
					route.width = Integer.valueOf(w.trim());

					final Androzic application = Androzic.getApplication();
					application.dispatchRoutePropertiesChanged(route);

					// Hide keyboard
					final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
					// "Close" fragment
					getFragmentManager().popBackStack();
	        	}
	        	catch (Exception e)
	        	{
	    			Toast.makeText(getActivity(), "Error saving route", Toast.LENGTH_LONG).show();        		
	        	}
			default:
				return super.onOptionsItemSelected(item);
		}
    };

	public void setRoute(Route route)
	{
		this.route = route;
		if (isVisible())
			updateRouteProperties();
	}

	private void updateRouteProperties()
	{
		name.setText(route.name);
		show.setChecked(route.show);
		color.setColor(route.lineColor, getResources().getColor(R.color.routeline));

		int sel = -1;
		List<String> widths = new ArrayList<String>(30);
		for (int i = 1; i <= 30; i++)
		{
			widths.add(String.format("   %d    ", i));
			if (route.width == i)
				sel = i - 1;
		}
		if (sel == -1)
		{
			widths.add(String.valueOf(route.width));
			sel = widths.size() - 1;
		}

		widthAdapter.clear();
		for (String w : widths)
		{
			widthAdapter.add(w);
		}
		width.setSelection(sel);
	}
}
