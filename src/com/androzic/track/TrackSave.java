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

import java.io.File;
import java.io.FileNotFoundException;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.util.FileUtils;
import com.androzic.util.OziExplorerFiles;

public class TrackSave extends SherlockActivity
{
	private TextView filename;
	private Track track;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_save);

		filename = (TextView) findViewById(R.id.filename_text);

		int index = getIntent().getExtras().getInt("INDEX");
        
		Androzic application = (Androzic) getApplication();
		track = application.getTrack(index);

		if (track.filepath != null)
		{
			File file = new File(track.filepath);
			filename.setText(file.getName());
		}
		else
		{
			filename.setText(FileUtils.sanitizeFilename(track.name) + ".plt");
		}
		
	    Button save = (Button) findViewById(R.id.save_button);
	    save.setOnClickListener(saveOnClickListener);

	    Button cancel = (Button) findViewById(R.id.cancel_button);
	    cancel.setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
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
    			String state = Environment.getExternalStorageState();
    			if (! Environment.MEDIA_MOUNTED.equals(state))
    				throw new FileNotFoundException(getString(R.string.err_nosdcard));
    			
    			Androzic application = (Androzic) getApplication();
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
        		finish();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(TrackSave.this, R.string.err_write, Toast.LENGTH_LONG).show();
    			Log.e("ANDROZIC", e.toString(), e);
    		}
        }
    };

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		track = null;
		filename = null;
	}

}
