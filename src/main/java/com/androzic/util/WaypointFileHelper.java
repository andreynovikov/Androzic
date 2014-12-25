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

package com.androzic.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.androzic.Androzic;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;

public class WaypointFileHelper
{
	/**
	 * Load waypoints in Ozi, KML or GPX format.
	 * 
	 * @param file File to load waypoints from
	 * @return Number of loaded waypoints
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public static int loadFile(File file) throws IOException, SAXException, ParserConfigurationException
	{
		Androzic application = Androzic.getApplication();
		List<Waypoint> waypoints = null;

		WaypointSet wptset = new WaypointSet(file);
		String lc = file.getName().toLowerCase(Locale.getDefault());
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
			return count;
		}
		return 0;
	}

}
