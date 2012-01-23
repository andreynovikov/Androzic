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

package com.androzic.track;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.ColorPickerDialog;
import com.androzic.ui.OnColorChangedListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class TrackProperties extends Activity
{
	private Track track;
	
	private TextView name;
	//private TextView description;
	private CheckBox show;
	private TextView color;
	private Button colorselect;
	
	private int colorValue;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_track_properties);

        int index = getIntent().getExtras().getInt("INDEX");
        
		Androzic application = (Androzic) getApplication();
		track = application.getTrack(index);
		
		name = (TextView) findViewById(R.id.name_text);
		name.setText(track.name);
		/*
		description = (TextView) findViewById(R.id.description_text);
		description.setText(track.description);
		*/
		show = (CheckBox) findViewById(R.id.show_check);
        show.setChecked(track.show);
        color = (TextView) findViewById(R.id.color_text);
        colorValue = track.color;
        color.setBackgroundColor(track.color);
	    colorselect = (Button) findViewById(R.id.color_button);
	    colorselect.setOnClickListener(colorOnClickListener);
		
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
        		track.name = name.getText().toString();
        		//track.description = description.getText().toString();
        		track.show = show.isChecked();
        		track.color = colorValue;
    			setResult(Activity.RESULT_OK);
        		finish();
        	}
        	catch (Exception e)
        	{
    			Toast.makeText(getBaseContext(), "Error saving track", Toast.LENGTH_LONG).show();        		
        	}
        }
    };

	private OnClickListener colorOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	new ColorPickerDialog(TrackProperties.this, colorChangeListener, colorValue, track.color, false).show();
        }
    };

	private OnColorChangedListener colorChangeListener = new OnColorChangedListener()
	{

		@Override
		public void colorChanged(int newColor)
		{
			colorValue = newColor;
			color.setBackgroundColor(newColor);
		}
		
	};

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		track = null;
		name = null;
		show = null;
		color = null;
		colorselect = null;
	}

}
