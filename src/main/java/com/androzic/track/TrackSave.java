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

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.util.FileUtils;
import com.androzic.util.OziExplorerFiles;

public class TrackSave extends DialogFragment
{
	private TextView filename;
	private Track track;
	
	public TrackSave()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

	//FIXME Fix lint error
	@SuppressLint("ValidFragment")
	public TrackSave(Track track)
	{
		this.track = track;
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.act_save, container);

		filename = (TextView) rootView.findViewById(R.id.filename_text);

		if (track.filepath != null)
		{
			File file = new File(track.filepath);
			filename.setText(file.getName());
		}
		else
		{
			filename.setText(FileUtils.sanitizeFilename(track.name) + ".plt");
		}

		final Dialog dialog = getDialog();

		Button cancelButton = (Button) rootView.findViewById(R.id.cancel_button);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				dialog.cancel();
			}
		});
		Button saveButton = (Button) rootView.findViewById(R.id.save_button);
		saveButton.setOnClickListener(saveOnClickListener);

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
    		String fname = FileUtils.sanitizeFilename(filename.getText().toString());
    		if ("".equals(fname))
    			return;
    		
    		try
    		{
    			Androzic application = Androzic.getApplication();
    			File dir = new File(application.dataPath);
    			if (! dir.exists())
    				dir.mkdirs();
    			File file = new File(dir, fname);
    			if (! file.exists())
    			{
    				file.createNewFile();
    			}
    			if (file.canWrite())
    			{
    				OziExplorerFiles.saveTrackToFile(file, application.charset, track);
    				track.filepath = file.getAbsolutePath();
    			}
        		dismiss();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(getActivity(), R.string.err_write, Toast.LENGTH_LONG).show();
    			Log.e("ANDROZIC", e.toString(), e);
    		}
        }
    };
}
