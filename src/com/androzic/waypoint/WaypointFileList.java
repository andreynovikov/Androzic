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
		return application.dataPath;
	}

	@Override
	protected void loadFile(File file)
	{
		Androzic application = (Androzic) getApplication();
		List<Waypoint> waypoints = null;

		try
		{
			WaypointSet wptset = new WaypointSet(file);
			String lc = file.getName().toLowerCase();
			if (lc.endsWith(".wpt"))
			{
				waypoints = OziExplorerFiles.loadWaypointsFromFile(file, application.charset);
			}
			else if (lc.endsWith(".kml"))
			{
				wptset.path = null;
				waypoints = KmlFiles.loadWaypointsFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				wptset.path = null;
				waypoints = GpxFiles.loadWaypointsFromFile(file);
			}
			if (waypoints != null)
			{
				for (Waypoint waypoint : waypoints)
				{
					waypoint.set = wptset;
				}
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