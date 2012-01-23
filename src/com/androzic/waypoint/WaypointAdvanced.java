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

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.WaypointSet;
import com.androzic.ui.ColorPickerDialog;
import com.androzic.ui.OnColorChangedListener;

public class WaypointAdvanced extends Activity
{
	private TextView markercolor;
	private TextView textcolor;
	
	private int markerColorValue;
	private int textColorValue;

	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_waypoint_advanced);

        Bundle extras = getIntent().getExtras();
        String description = extras.getString("description");
        int set = extras.getInt("set");
        markerColorValue = extras.getInt("markercolor");
        textColorValue = extras.getInt("textcolor");
        
		((TextView) findViewById(R.id.description_text)).setText(description);

		Androzic application = (Androzic) getApplication();

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

        markercolor = (TextView) findViewById(R.id.markercolor_text);
        markercolor.setBackgroundColor(markerColorValue);
	    ((Button) findViewById(R.id.markercolor_button)).setOnClickListener(markerColorOnClickListener);

        textcolor = (TextView) findViewById(R.id.textcolor_text);
        textcolor.setBackgroundColor(textColorValue);
	    ((Button) findViewById(R.id.textcolor_button)).setOnClickListener(textColorOnClickListener);

	    ((Button) findViewById(R.id.done_button)).setOnClickListener(doneOnClickListener);
	    ((Button) findViewById(R.id.cancel_button)).setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
    }

	private OnClickListener doneOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	String description = ((TextView) findViewById(R.id.description_text)).getText().toString();
        	int set = ((Spinner) findViewById(R.id.set_spinner)).getSelectedItemPosition();
			setResult(Activity.RESULT_OK, new Intent().putExtra("description", description).putExtra("set", set).putExtra("markercolor", markerColorValue).putExtra("textcolor", textColorValue));
    		finish();
        }
    };

	private OnClickListener markerColorOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	new ColorPickerDialog(WaypointAdvanced.this, markerColorChangeListener, markerColorValue, markerColorValue, false).show();
        }
    };

	private OnColorChangedListener markerColorChangeListener = new OnColorChangedListener()
	{

		@Override
		public void colorChanged(int newColor)
		{
			markerColorValue = newColor;
			markercolor.setBackgroundColor(newColor);
		}
		
	};

	private OnClickListener textColorOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	new ColorPickerDialog(WaypointAdvanced.this, textColorChangeListener, textColorValue, textColorValue, false).show();
        }
    };

	private OnColorChangedListener textColorChangeListener = new OnColorChangedListener()
	{

		@Override
		public void colorChanged(int newColor)
		{
			textColorValue = newColor;
			textcolor.setBackgroundColor(newColor);
		}
		
	};
}
