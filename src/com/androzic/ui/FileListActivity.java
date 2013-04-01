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

package com.androzic.ui;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.androzic.R;
import com.androzic.util.FileList;

public abstract class FileListActivity extends SherlockListActivity
{
	List<File> files = null;
	List<Map<String, String>> fileData = new ArrayList<Map<String, String>>();

	private ProgressDialog dlgWait;
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_FILE = "FILE";
	private final static String KEY_PATH = "DIR";

	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		String state = Environment.getExternalStorageState();
		if (! Environment.MEDIA_MOUNTED.equals(state))
		{
			Toast.makeText(this, R.string.err_nosdcard, Toast.LENGTH_LONG).show();
			this.finish();
		}

		final ProgressDialog pd = new ProgressDialog(this);
		
		pd.setIndeterminate(true);
		pd.setMessage(getString(R.string.msg_scansdcard)); 
		pd.show();
		
		new Thread(new Runnable() 
		{ 
			public void run() 
			{
				File root = new File(getPath());
				files = FileList.getFileListing(root, getFilenameFilter());
/*
				Collections.sort(files, new Comparator()
                        {
                            @Override
                            public int compare(Object o1, Object o2)
                            {
                        	return ((File) o1).getName().compareToIgnoreCase(((File) o2).getName());
                            }
                        });
*/            	
				Map<String, String> group;

				for (File file : files)
				{
					group = new HashMap<String, String>();
					group.put(KEY_FILE, file.getName());
					group.put(KEY_PATH, file.getParent());
					fileData.add( group );
				}

				pd.dismiss();
				
				handler.post(updateResults);
			} 
		}).start();
	}

	final Runnable updateResults = new Runnable() 
	{
		public void run() 
        {
			setListAdapter(new SimpleAdapter(FileListActivity.this, fileData, android.R.layout.simple_list_item_2, new String[] { KEY_FILE, KEY_PATH }, new int[]{ android.R.id.text1, android.R.id.text2 } ));
			getListView().setTextFilterEnabled(true);
        }
	};

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

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) 
	{
		super.onListItemClick(l, v, position, id);

		final File file = files.get(position);

		if (!file.exists())
		{
			Toast.makeText(this, R.string.err_nofile, Toast.LENGTH_LONG).show();
			this.setResult(RESULT_CANCELED);
			finish();
		}

		showDialog(0);

		this.threadPool.execute(new Runnable() 
		{
			public void run() 
			{
				loadFile(file);			    
				dlgWait.dismiss();
			};
		});

	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		files = null;
		fileData.clear();
	}

	abstract protected FilenameFilter getFilenameFilter();

	abstract protected String getPath();

	abstract protected void loadFile(File file);

	protected final Runnable wrongFormat = new Runnable() {
		public void run()
		{
			Toast.makeText(getBaseContext(), R.string.err_wrongformat, Toast.LENGTH_LONG).show();
		}
	};

	protected final Runnable readError = new Runnable() {
		public void run()
		{
			Toast.makeText(getBaseContext(), R.string.err_read, Toast.LENGTH_LONG).show();
		}
	};
}