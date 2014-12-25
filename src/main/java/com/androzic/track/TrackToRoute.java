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

package com.androzic.track;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Track;

public class TrackToRoute extends DialogFragment
{
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	
	private RadioButton algA;
	//private RadioButton algB;
	private SeekBar sensitivity;
	
	private Track track;
	
	public TrackToRoute()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

	//FIXME Fix lint error
	@SuppressLint("ValidFragment")
	public TrackToRoute(Track track)
	{
		this.track = track;
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.act_track_to_route, container);

		algA = (RadioButton) rootView.findViewById(R.id.alg_a);
		//algB = (RadioButton) rootView.findViewById(R.id.alg_b);
		algA.setChecked(true);

    	sensitivity = (SeekBar) rootView.findViewById(R.id.sensitivity);

	    Button generate = (Button) rootView.findViewById(R.id.generate_button);
	    generate.setOnClickListener(saveOnClickListener);

		final Dialog dialog = getDialog();

	    dialog.setTitle(R.string.savetrack_name);
		dialog.setCanceledOnTouchOutside(false);
	    
	    return rootView;
    }

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	final Androzic application = Androzic.getApplication();
        	final int alg = algA.isChecked() ? 1 : 2;
        	final int s = sensitivity.getProgress();
        	final float sensitivity = (s + 1) / 2f;

    		ProgressBar progress = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleLarge);
    		progress.setIndeterminate(true);
    		
    		ViewGroup rootView = (ViewGroup) getView();
    		rootView.setMinimumWidth(rootView.getWidth());
    		rootView.setMinimumHeight(rootView.getHeight());
    		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rootView.getLayoutParams();
    		params.gravity = Gravity.CENTER;
    		rootView.removeAllViews();
    		rootView.setLayoutParams(params);
    		
    		params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
    		params.gravity = Gravity.CENTER;
    		rootView.addView(progress, params);

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
    	    		dismiss();
    			};
    		});
        }
    };
}
