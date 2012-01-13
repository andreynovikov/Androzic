package com.androzic.util;

import java.io.File;
import java.io.FilenameFilter;

public class TrackFilenameFilter implements FilenameFilter
{

	@Override
	public boolean accept(final File dir, final String filename)
	{
		String lc = filename.toLowerCase();
		return lc.endsWith(".plt") || lc.endsWith(".gpx") || lc.endsWith(".kml");
	}

}
