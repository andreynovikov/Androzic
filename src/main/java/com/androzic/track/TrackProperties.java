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

package com.androzic.track;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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
import com.androzic.data.Track;
import com.androzic.ui.ColorButton;

public class TrackProperties extends Fragment
{
	private Track track;

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
		View rootView = inflater.inflate(R.layout.act_track_properties, container, false);
		name = (TextView) rootView.findViewById(R.id.name_text);
		show = (CheckBox) rootView.findViewById(R.id.show_check);
		color = (ColorButton) rootView.findViewById(R.id.color_button);
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

		if (track != null)
			updateTrackProperties();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.trackproperties_name);
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
					track.name = name.getText().toString();
					track.show = show.isChecked();
					track.color = color.getColor();
					String w = (String) width.getItemAtPosition(width.getSelectedItemPosition());
					track.width = Integer.valueOf(w.trim());

					final Androzic application = Androzic.getApplication();
					application.dispatchTrackPropertiesChanged(track);

					// Hide keyboard
					final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
					// "Close" fragment
					getFragmentManager().popBackStack();
				}
				catch (Exception e)
				{
					Log.e("TrackProperties", "Track save error", e);
					Toast.makeText(getActivity(), "Error saving track", Toast.LENGTH_LONG).show();
				}
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void setTrack(Track track)
	{
		this.track = track;
		if (isVisible())
			updateTrackProperties();
	}

	private void updateTrackProperties()
	{
		name.setText(track.name);
		show.setChecked(track.show);
		color.setColor(track.color, getResources().getColor(R.color.currenttrack));

		int sel = -1;
		List<String> widths = new ArrayList<String>(30);
		for (int i = 1; i <= 30; i++)
		{
			widths.add(String.format("   %d    ", i));
			if (track.width == i)
				sel = i - 1;
		}
		if (sel == -1)
		{
			widths.add(String.valueOf(track.width));
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
