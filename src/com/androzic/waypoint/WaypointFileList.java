package com.androzic.waypoint;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;

import com.androzic.Androzic;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.ui.FileListActivity;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.WaypointFilenameFilter;

public class WaypointFileList extends FileListActivity
{
	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return new WaypointFilenameFilter();
	}

	@Override
	protected String getPath()
	{
		Androzic application = (Androzic) getApplication();
		return application.waypointPath;
	}

	@Override
	protected void loadFile(File file)
	{
		List<Waypoint> waypoints = null;

		try
		{
			WaypointSet wptset = new WaypointSet(file);
			String lc = file.getName().toLowerCase();
			if (lc.endsWith(".wpt"))
			{
				waypoints = OziExplorerFiles.loadWaypointsFromFile(file);
			}
			else if (lc.endsWith(".kml"))
			{
				waypoints = KmlFiles.loadWaypointsFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				waypoints = GpxFiles.loadWaypointsFromFile(file);
			}
			if (waypoints != null)
			{
				for (Waypoint waypoint : waypoints)
				{
					waypoint.set = wptset;
				}
				Androzic application = (Androzic) getApplication();
				int count = application.addWaypoints(waypoints, wptset);
				setResult(Activity.RESULT_OK, new Intent().putExtra("count", count));
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