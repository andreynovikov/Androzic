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

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.ui.ColorButton;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class RouteProperties extends SherlockActivity
{
	private Route route;
	
	private TextView name;
	//private TextView description;
	private CheckBox show;
	private ColorButton color;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // FIXME Should have its own layout
        setContentView(R.layout.act_track_properties);

        int index = getIntent().getExtras().getInt("index");
        
		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);
		
		name = (TextView) findViewById(R.id.name_text);
		name.setText(route.name);
		/*
		description = (TextView) findViewById(R.id.description_text);
		description.setText(track.description);
		*/
		show = (CheckBox) findViewById(R.id.show_check);
        show.setChecked(route.show);
        color = (ColorButton) findViewById(R.id.color_button);
        color.setColor(route.lineColor, getResources().getColor(R.color.routeline));

		ViewGroup width = (ViewGroup) findViewById(R.id.width_layout);
		width.setVisibility(View.GONE);

	    Button save = (Button) findViewById(R.id.done_button);
	    save.setOnClickListener(saveOnClickListener);

	    Button cancel = (Button) findViewById(R.id.cancel_button);
	    cancel.setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
    }

	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	try
        	{
        		route.name = name.getText().toString();
        		//route.description = description.getText().toString();
        		route.show = show.isChecked();
        		route.lineColor = color.getColor();
    			setResult(RESULT_OK);
        		finish();
        	}
        	catch (Exception e)
        	{
    			Toast.makeText(getBaseContext(), "Error saving route", Toast.LENGTH_LONG).show();        		
        	}
        }
    };

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		route = null;
		name = null;
		show = null;
		color = null;
	}

}
