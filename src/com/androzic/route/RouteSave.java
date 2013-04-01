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

import java.io.File;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.util.FileUtils;
import com.androzic.util.OziExplorerFiles;

public class RouteSave extends SherlockActivity
{
	private TextView filename;
	private Route route;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_save);

		filename = (TextView) findViewById(R.id.filename_text);

		int index = getIntent().getExtras().getInt("index");
        
		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);

		if (route.filepath != null)
		{
			File file = new File(route.filepath);
			filename.setText(file.getName());
		}
		else
		{
			filename.setText(FileUtils.sanitizeFilename(route.name) + ".rt2");
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
    		String fname = filename.getText().toString();
    		fname = fname.replace("../", "");
    		fname = fname.replace("/", "");
    		if ("".equals(fname))
    			return;
    		
    		try
    		{
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
    				OziExplorerFiles.saveRouteToFile(file, application.charset, route);
    				route.filepath = file.getAbsolutePath();
    			}
        		finish();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(RouteSave.this, R.string.err_write, Toast.LENGTH_LONG).show();
    			Log.e("ANDROZIC", e.toString(), e);
    		}
        }
    };

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		route = null;
		filename = null;
	}

}
