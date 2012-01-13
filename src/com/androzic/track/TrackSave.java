package com.androzic.track;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
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

public class TrackSave extends Activity
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
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
			String dateString = formatter.format(new Date());
			filename.setText(dateString+".plt");
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
    			String state = Environment.getExternalStorageState();
    			if (! Environment.MEDIA_MOUNTED.equals(state))
    				throw new FileNotFoundException(getString(R.string.err_nosdcard));
    			
    			Androzic application = (Androzic) getApplication();
    			File dir = new File(application.trackPath);
    			if (! dir.exists())
    				dir.mkdirs();
    			File file = new File(dir, fname);
    			if (! file.exists())
    			{
    				file.createNewFile();
    			}
    			if (file.canWrite())
    			{
    				OziExplorerFiles.saveTrackToFile(file, track);
    			}
        		finish();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(TrackSave.this, R.string.err_sdwrite, Toast.LENGTH_LONG).show();
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
