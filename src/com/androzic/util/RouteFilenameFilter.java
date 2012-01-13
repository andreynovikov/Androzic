package com.androzic.util;

import java.io.File;
import java.io.FilenameFilter;

public class RouteFilenameFilter implements FilenameFilter
{

	@Override
	public boolean accept(final File dir, final String filename)
	{
		String lc = filename.toLowerCase();
		return lc.endsWith(".rt2") || lc.endsWith(".rte") || lc.endsWith(".gpx") || lc.endsWith(".kml");
	}

}
