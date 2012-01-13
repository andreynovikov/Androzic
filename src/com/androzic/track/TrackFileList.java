package com.androzic.track;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.FileListActivity;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.TrackFilenameFilter;

public class TrackFileList extends FileListActivity
{
	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		Toast.makeText(getBaseContext(), getString(R.string.msg_badtrackimplementation), Toast.LENGTH_LONG).show();
	}
	
	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return new TrackFilenameFilter();
	}

	@Override
	protected String getPath()
	{
		Androzic application = (Androzic) getApplication();
		return application.trackPath;
	}

	@Override
	protected void loadFile(File file)
	{
		List<Track> tracks = null;
		try
		{
			String lc = file.getName().toLowerCase();
			if (lc.endsWith(".plt"))
			{
				tracks = new ArrayList<Track>();
			    tracks.add(OziExplorerFiles.loadTrackFromFile(file));
			}
			else if (lc.endsWith(".kml"))
			{
				tracks = KmlFiles.loadTracksFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				tracks = GpxFiles.loadTracksFromFile(file);
			}
			if (tracks.size() > 0)
			{
				Androzic application = (Androzic) getApplication();
				int[] index = new int[tracks.size()];
				int i = 0;
				for (Track track: tracks)
				{
					index[i] = application.addTrack(track);
					i++;
				}
				setResult(Activity.RESULT_OK, new Intent().putExtra("index", index));
			}
			else
			{
				setResult(Activity.RESULT_CANCELED, new Intent());					
			}
			finish();
		}
		catch (IllegalArgumentException e)
		{
			runOnUiThread(wrongFormat);
		}
		catch (SAXException e)
		{
			runOnUiThread(wrongFormat);
			e.printStackTrace();
		}
		catch (IOException e)
		{
			runOnUiThread(readError);
			e.printStackTrace();
		}
		catch (ParserConfigurationException e)
		{
			runOnUiThread(readError);
			e.printStackTrace();
		}
	}
}