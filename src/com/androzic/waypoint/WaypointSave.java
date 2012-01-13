package com.androzic.waypoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.util.OziExplorerFiles;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class WaypointSave extends Activity
{
	private TextView filename;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_save);

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
		String dateString = formatter.format(new Date()); 
		filename = (TextView) findViewById(R.id.filename_text);
		filename.setText(dateString+".wpt");
		
	    Button save = (Button) findViewById(R.id.save_button);
	    save.setOnClickListener(saveOnClickListener);

	    Button cancel = (Button) findViewById(R.id.cancel_button);
	    cancel.setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
    }
	
	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
    		Androzic application = (Androzic) getApplication();
    		List<Waypoint> waypoints = application.getDefaultWaypoints();
    		String fname = filename.getText().toString();
    		fname = fname.replace("../", "");
    		fname = fname.replace("/", "");
    		if ("".equals(fname))
    			return;
    		
    		try
    		{
    			String state = Environment.getExternalStorageState();
    			if (! Environment.MEDIA_MOUNTED.equals(state))
    				throw new FileNotFoundException(getString(R.string.err_nosdcard));
    			
    			File dir = new File(application.waypointPath);
    			if (! dir.exists())
    				dir.mkdirs();
    			File file = new File(dir, fname);
    			if (! file.exists())
    			{
    				file.createNewFile();
    			}
    			if (file.canWrite())
    			{
    				OziExplorerFiles.saveWaypointsToFile(file, waypoints);
    				WaypointSet wptset = new WaypointSet(file);
    				for (Waypoint waypoint : waypoints)
    				{
    					waypoint.set = wptset;
    				}
    				application.addWaypointSet(wptset);
    			}
        		finish();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(WaypointSave.this,  R.string.err_sdwrite, Toast.LENGTH_LONG).show();
    			Log.e("ANDROZIC", e.toString(), e);
    		}
        }
    };

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		filename = null;
	}

}
