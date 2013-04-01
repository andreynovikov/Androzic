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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.overlay.RouteOverlay;

public class TrackToRoute extends SherlockActivity
{
	private ProgressDialog dlgWait;
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	
	private RadioButton algA;
	private RadioButton algB;
	
	private int index;
	
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_track_to_route);

        index = getIntent().getExtras().getInt("INDEX");

		algA = (RadioButton) findViewById(R.id.alg_a);
		algB = (RadioButton) findViewById(R.id.alg_b);

		algA.setChecked(true);

	    Button generate = (Button) findViewById(R.id.generate_button);
	    generate.setOnClickListener(saveOnClickListener);
    }

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		algA = null;
		algB = null;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) 
	{
		switch (id) 
		{
    		case 0:
    		{
    			dlgWait = new ProgressDialog(this);
    			dlgWait.setMessage(getString(R.string.msg_wait));
    			dlgWait.setIndeterminate(true);
    			dlgWait.setCancelable(false);
    			return dlgWait;
    		}
		}
		return null;
	}

	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	showDialog(0);

        	final Androzic application = (Androzic) getApplication();
			final Track track = application.getTrack(index);
        	final int alg = algA.isChecked() ? 1 : 2;
        	final int s = ((SeekBar) findViewById(R.id.sensitivity)).getProgress();
        	final float sensitivity = (s + 1) / 2f;

    		threadPool.execute(new Runnable() 
    		{
    			public void run() 
    			{
    				Route route = null;
    				switch (alg)
    				{
    					case 1:
    	    				route = application.trackToRoute(track, sensitivity);
    	    				break;
    					case 2:
    	    				route = application.trackToRoute2(track, sensitivity);
    	    				break;
    				}
    				application.addRoute(route);
    				// TODO it's a hack
    				RouteOverlay newRoute = new RouteOverlay(application.mapActivity, route);
    				application.routeOverlays.add(newRoute);
    				dlgWait.dismiss();
    	    		finish();
    			};
    		});
        }
    };

/*
*/
}
