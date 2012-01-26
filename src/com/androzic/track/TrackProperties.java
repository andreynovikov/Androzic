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
import com.androzic.ui.ColorButton;

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
	private ColorButton color;
	
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
        color = (ColorButton) findViewById(R.id.color_button);
        color.setColor(track.color, getResources().getColor(R.color.currenttrack));
		
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
        		track.color = color.getColor();
    			setResult(Activity.RESULT_OK);
        		finish();
        	}
        	catch (Exception e)
        	{
    			Toast.makeText(getBaseContext(), "Error saving track", Toast.LENGTH_LONG).show();        		
        	}
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
	}

}
