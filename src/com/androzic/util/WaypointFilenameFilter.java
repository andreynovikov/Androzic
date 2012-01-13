package com.androzic.util;

import java.io.File;
import java.io.FilenameFilter;

public class WaypointFilenameFilter implements FilenameFilter
{

	@Override
	public boolean accept(final File dir, final String filename)
	{
		String lc = filename.toLowerCase();
		return lc.endsWith(".wpt") || lc.endsWith(".kml") || lc.endsWith(".gpx");
	}

}
