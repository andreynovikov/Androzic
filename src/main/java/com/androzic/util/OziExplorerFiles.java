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

package com.androzic.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Track.TrackPoint;
import com.androzic.data.Waypoint;
import com.androzic.map.MapLoader;
import com.jhlabs.map.Datum;
import com.jhlabs.map.Ellipsoid;

/**
 * Helper class to read and write OziExplorer files.
 * 
 * @author Andrey Novikov
 */
public class OziExplorerFiles
{
	final static DecimalFormat numFormat = new DecimalFormat("* ###0");
	final static DecimalFormat coordFormat = new DecimalFormat("* ###0.000000", new DecimalFormatSymbols(Locale.ENGLISH));
	
	/**
	 * Loads waypoints from file
	 * 
	 * @param file valid <code>File</code> with waypoints
	 * @return <code>List</code> of <code>Waypoint</code>s
	 * @throws IOException 
	 */
	public static List<Waypoint> loadWaypointsFromFile(final File file, final String charset) throws IOException
	{
		List<Waypoint> waypoints = new ArrayList<Waypoint>();

	    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
	    
	    String line = null;

	    // OziExplorer Waypoint File Version 1.0
		reader.readLine();
	    // WGS 84
		reader.readLine();
	    // Reserved 2
		reader.readLine();
	    // Reserved 3
		reader.readLine();
		//21,PTRS          , -26.636541, 152.449640,35640.91155, 0, 1, 3,  16777215,  16711935,Peach Trees Camping area                , 0, 0
	    while ((line = reader.readLine()) != null)
		{
	    	try
	    	{
	    		String[] fields = CSV.parseLine(line);
		    	if (fields.length >= 11)
		    	{
			    	if ("".equals(fields[1]))
			    		fields[1] = "WPT"+fields[0];
			    	
		    		Waypoint waypoint = new Waypoint(fields[1].replace((char) 209, ','), Entities.XML.unescape(fields[10]), Double.parseDouble(fields[2]), Double.parseDouble(fields[3]));
	
		    		if (! "".equals(fields[4]))
		    		{
		    			try 
		    			{
		    				waypoint.date = TDateTime.dateFromDateTime(Double.parseDouble(fields[4]));
		    			}
			    		catch (NumberFormatException e)
			    		{
			    			e.printStackTrace();
			    		}
		    		}
		    		
			    	if (! "".equals(fields[8]))
			    	{
			    		try
			    		{
			    			int fgcolor = Integer.parseInt(fields[8]);
			    			if (fgcolor != 0)
			    				waypoint.textcolor = bgr2rgb(fgcolor);
			    		}
			    		catch (NumberFormatException e)
			    		{
			    		}
			    	}
			    	if (! "".equals(fields[9]))
			    	{
			    		try
			    		{
			    			int bgcolor = Integer.parseInt(fields[9]);
				    		if (bgcolor != 65535)
				    			waypoint.backcolor = bgr2rgb(bgcolor);
			    		}
			    		catch (NumberFormatException e)
			    		{
			    		}
			    	}
	
		    		if (fields.length >= 14 && ! "".equals(fields[13]))
		    		{
			    		try
			    		{
			    			waypoint.proximity = Integer.parseInt(fields[13]);
			    		}
			    		catch (NumberFormatException e)
			    		{
			    		}
			    	}
	
		    		if (fields.length >= 15 && ! "".equals(fields[14]))
		    		{
			    		try
			    		{
			    			int alt = Integer.parseInt(fields[14]);
			    			waypoint.altitude = alt == -777 ? Integer.MIN_VALUE : alt;
		    			}
			    		catch (NumberFormatException e)
			    		{
			    		}
			    	}
	
		    		if (fields.length >= 22 && ! "".equals(fields[21]))
		    		{
		    			waypoint.marker = fields[21];
			    	}
		    		waypoints.add(waypoint);
		    	}
	    	}
	    	catch (IllegalArgumentException e)
	    	{
	    		//TODO Show error to user
	    		e.printStackTrace();
	    	}
	    }
		reader.close();

		return waypoints;
	}
	
	/**
	 * Saves waypoints to file.
	 * 
	 * @param file valid <code>File</code>
	 * @param waypoints <code>List</code> of <code>Waypoint</code>s to save
	 * @throws IOException
	 */
	public static void saveWaypointsToFile(final File file, final String charset, final List<Waypoint> waypoints) throws IOException
	{
	    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), charset));

		writer.write("OziExplorer Waypoint File Version 1.1\n" +
				  "WGS 84\n" +
				  "Reserved 2\n" +
				  "Reserved 3\n");
		
	    //*  One line per waypoint
	    //* each field separated by a comma
	    //* comma's not allowed in text fields, character 209 can be used instead and a comma will be substituted.
	    //* non essential fields need not be entered but comma separators must still be used (example ,,)
	    //  defaults will be used for empty fields
	    //* Any number of the last fields in a data line need not be included at all not even the commas.

	    //Field 1 : Number - this is the location in the array (max 1000), must be unique, usually start at 1 and increment. Can be set to -1 (minus 1) and the number will be auto generated.
		//Field 2 : Name - the waypoint name, use the correct length name to suit the GPS type.
		//Field 3 : Latitude - decimal degrees
		//Field 4 : Longitude - decimal degrees
		//Field 5 : Date - see Date Format below, if blank a preset date will be used
		//Field 6 : Symbol - 0 to number of symbols in GPS
		//Field 7 : Status - always set to 1
		//Field 8 : Map Display Format
		//Field 9 : Foreground Color (RGB value)
		//Field 10 : Background Color (RGB value)
		//Field 11 : Description (max 40), no commas
		//Field 12 : Pointer Direction
		//Field 13 : Garmin Display Format
		//Field 14 : Proximity Distance - 0 is off any other number is valid
		//Field 15 : Altitude - in feet (-777 if not valid)
		//Field 16 : Font Size - in points
		//Field 17 : Font Style - 0 is normal, 1 is bold.
		//Field 18 : Symbol Size - 17 is normal size
		//Field 19 : Proximity Symbol Position
		//Field 20 : Proximity Time
		//Field 21 : Proximity or Route or Both
		//Field 22 : File Attachment Name
		//Field 23 : Proximity File Attachment Name
		//Field 24 : Proximity Symbol Name 
	
        synchronized (waypoints)
        {
	        for (Waypoint wpt : waypoints)
	        {
	        	writer.write("-1,");
	        	writer.write(wpt.name.replace(',', (char) 209)+",");
	        	writer.write(coordFormat.format(wpt.latitude)+","+coordFormat.format(wpt.longitude)+",");
	        	writer.write((wpt.date == null ? "" : TDateTime.toDateTime(wpt.date))+",");
	        	writer.write("0,1,3,");
	        	writer.write((wpt.textcolor != Integer.MIN_VALUE ? rgb2bgr(wpt.textcolor) : "") + ",");
	        	writer.write((wpt.backcolor != Integer.MIN_VALUE ? rgb2bgr(wpt.backcolor) : "") + ",");
	        	writer.write(Entities.XML.escape(wpt.description) + ",");
	        	writer.write("2,0,");
	        	writer.write(wpt.proximity + ",");
	        	writer.write((wpt.altitude == Integer.MIN_VALUE ? -777 : wpt.altitude) + ",");
	        	writer.write(",,,,,,");
	        	writer.write(wpt.marker+",,");
	        	writer.write("\n");
	        }
        }
		writer.close();
	}
	
	/**
	 * Loads track from file.
	 * 
	 * @param file valid <code>File</code> with track points
	 * @return <code>Track</code> with track points
	 * @throws IOException on file read error
	 * @throws IllegalArgumentException if file format is not plt
	 */
	public static Track loadTrackFromFile(final File file, final String charset) throws IllegalArgumentException, IOException
	{
		return loadTrackFromFile(file, charset, 0);
	}
	
	/**
	 * Loads track from file.
	 * 
	 * @param file valid <code>File</code> with track points
	 * @param lines number of last lines to read
	 * @return <code>Track</code> with track points
	 * @throws IOException on file read error
	 * @throws IllegalArgumentException if file format is not plt
	 */
	public static Track loadTrackFromFile(final File file, final String charset, final long lines) throws IllegalArgumentException, IOException
	{
		Track track = new Track();
		
		long skip = 0;
		if (lines > 0)
		{
			skip = file.length() - 35 * lines; // 35 - average line length in conventional track file
		}

	    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));

	    String line = null;

	    // OziExplorer Track Point File Version 2.0
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
	    // WGS 84
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
	    // Altitude is in Feet
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
	    // Reserved 3
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
	    // 0,2,255,OziCE Track Log File,1
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
		String[] fields = CSV.parseLine(line);
	    if (fields.length < 4)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
		track.width=Integer.parseInt(fields[1]);
		track.color=bgr2rgb(Integer.parseInt(fields[2]));
		track.name=fields[3];
	    // 0
	    if ((line = reader.readLine()) == null)
	    {
	    	reader.close();
	    	throw new IllegalArgumentException("Bad track file");
	    }
	    skip -= line.length();
	    skip -= 12; // new line characters
		
		if (skip > 0)
		{
			reader.skip(skip);
			reader.readLine(); // skip broken line
		}

	    //   55.6384683,  37.3516133,0,    583.0,    0.0000000 ,290705,185332.996
	    while ((line = reader.readLine()) != null)
		{
			fields = CSV.parseLine(line);
			long time = fields.length > 4 ? TDateTime.fromDateTime(Double.parseDouble(fields[4])): 0L;
			double elevation = fields.length > 3 ? Double.parseDouble(fields[3]) * 0.3048: 0;
			if (fields.length >= 3)
				track.addPoint("0".equals(fields[2]) ? true : false, Double.parseDouble(fields[0]), Double.parseDouble(fields[1]), elevation, 0.0, 0.0, 0.0, time);
	    }
		reader.close();
		
		track.show = true;
		track.filepath = file.getCanonicalPath();
		if ("".equals(track.name))
			track.name = track.filepath;

		return track;
	}

	/**
	 * Saves track to file.
	 * 
	 * @param file valid <code>File</code>
	 * @param charset the string describing the desired character encoding
	 * @param track <code>Track</code> object containing the list of track points to save
	 * @throws IOException
	 */
	public static void saveTrackToFile(final File file, final String charset, final Track track) throws IOException
	{
	    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), charset));

	    writer.write("OziExplorer Track Point File Version 2.1\n" +
				"WGS 84\n" +
				"Altitude is in Feet\n" +
				"Reserved 3\n");

		// Field 1 : always zero (0)
		// Field 2 : width of track plot line on screen - 1 or 2 are usually the best
		// Field 3 : track color (RGB)
		// Field 4 : track description (no commas allowed)
		// Field 5 : track skip value - reduces number of track points plotted, usually set to 1
		// Field 6 : track type - 0 = normal , 10 = closed polygon , 20 = Alarm Zone
		// Field 7 : track fill style - 0 =bsSolid; 1 =bsClear; 2 =bsBdiagonal; 3 =bsFdiagonal; 4 =bsCross;
		// 5 =bsDiagCross; 6 =bsHorizontal; 7 =bsVertical;
		// Field 8 : track fill color (RGB)
		writer.write("0,"+String.valueOf(track.width)+","+
				String.valueOf(rgb2bgr(track.color))+","+
	        	track.name.replace(',', (char) 209)+",0,0\n"+
				"0\n");
	
		//Field 1 : Latitude - decimal degrees
		//Field 2 : Longitude - decimal degrees
		//Field 3 : Code - 0 if normal, 1 if break in track line
		//Field 4 : Altitude in feet (-777 if not valid)
		//Field 5 : Date - see Date Format below, if blank a preset date will be used
		//Field 6 : Date as a string
		//Field 7 : Time as a string
		// Note that OziExplorer reads the Date/Time from field 5, the date and time in fields 6 & 7 are ignored.
	
		//-27.350436, 153.055540,1,-777,36169.6307194, 09-Jan-99, 3:08:14 
	
        List<TrackPoint> trackPoints = track.getAllPoints();
        synchronized (trackPoints)
        {  
	        for (TrackPoint tp : trackPoints)
	        {
	        	writer.write(coordFormat.format(tp.latitude)+","+coordFormat.format(tp.longitude)+",");
	        	if (tp.continous)
	        		writer.write("0");
	        	else
	        		writer.write("1");
	        	writer.write(","+String.valueOf(Math.round(tp.elevation * 3.2808399)));
	        	if (tp.time > 0)
	        	{
		        	writer.write(","+String.valueOf(TDateTime.toDateTime(tp.time)));
	        	}
	        	writer.write("\n");
	        }
        }
        writer.close();
	}
	
	/**
	 * Loads routes from file.
	 * 
	 * @param file valid <code>File</code> with route waypoints
	 * @return <code>List<Route></code> the list of routes
	 * @throws IOException on file read error
	 * @throws IllegalArgumentException if file format is not rt2 or rte
	 */
	public static List<Route> loadRoutesFromFile(final File file, final String charset) throws IOException, IllegalArgumentException
	{
		List<Route> routes = new ArrayList<Route>();
		
	    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
	    
	    String line = reader.readLine();
		String[] fields = CSV.parseLine(line);
	    
		if ("H1".equals(fields[0]))
		{
			// rt2 format

			// H1,OziExplorer CE Route2 File Version 1.0
			// H2,WGS 84
		    line = reader.readLine();
			fields = CSV.parseLine(line);
			if (! "H2".equals(fields[0]))
			{
				reader.close();
				throw new IllegalArgumentException("Bad rt2 header");
			}
			// H3,My route,,0
		    line = reader.readLine();
			fields = CSV.parseLine(line);
			if (! "H3".equals(fields[0]))
			{
				reader.close();
				throw new IllegalArgumentException("Bad rt2 header");
			}
			Route route = new Route();
			routes.add(route);
			route.name = fields[1].replace((char) 209, ',');
    		try
    		{
    			int width = Integer.parseInt(fields[2]);
    			if (width > 0)
    				route.width = width;
    		}
    		catch (NumberFormatException e)
    		{
    		}
    		try
    		{
    			int color = Integer.parseInt(fields[3]);
    			if (color != 0)
    				route.lineColor = bgr2rgb(color);
    		}
    		catch (NumberFormatException e)
    		{
    		}
			// W,Tsapelka,  58.0460242,  28.9465437,0
		    while ((line = reader.readLine()) != null)
			{
				fields = CSV.parseLine(line);
				if (! "W".equals(fields[0]))
					continue;
				route.addWaypoint(fields[1].replace((char) 209, ','), Double.parseDouble(fields[2]), Double.parseDouble(fields[3]));
	        	// Format extension (probably not compatible with OziExplorer)
	        	if (fields.length > 5)
	        	{
	        		try
	        		{
	        			int proximity = Integer.parseInt(fields[5]);
	        			if (proximity > 0)
	        				route.getWaypoint(route.length() - 1).proximity = proximity;
	        		}
	        		catch (NumberFormatException e)
	        		{
	        		}
	        	}

		    }
			reader.close();
			
			route.show = true;
			route.filepath = file.getCanonicalPath();
			if ("".equals(route.name))
				route.name = route.filepath;
		}
		else if ("OziExplorer Route File Version 1.0".equals(fields[0]))
		{
			// rte format

			//OziExplorer Route File Version 1.0
			//WGS 84
		    line = reader.readLine();
			//Reserved 1
		    line = reader.readLine();
			//Reserved 2
		    line = reader.readLine();
		    Route route = null;
		    int routeNum = -1;
		    int wptNum = 0;
			//R,  0,ROUTE 1         ,Description,255
			//W,  0,  1, 29,29              , -26.568702, 152.369428,35640.9202400, 0, 1, 0,   8388608,     65535,, 0, 0
		    //W,  1,  2, 35,35              , -26.550290, 152.416844,35641.5077900, 0, 1, 0,   8388608,     65535,, 0, 0
		    while ((line = reader.readLine()) != null)
			{
				fields = CSV.parseLine(line);
				int rtn = Integer.valueOf(fields[1]);
				if ("R".equals(fields[0]))
				{
					if (rtn == routeNum + 1)
					{
						if (route != null)
						{
							if (route.length() > 0)
							{
								route.show = true;
								if (routeNum == 0)
									route.filepath = file.getCanonicalPath();
								if ("".equals(route.name))
									route.name = "R"+routeNum;
								routes.add(route);
							}
							route = null;							
						}
						route = new Route();
						route.name = fields[2].replace((char) 209, ',');
						route.description = fields[3].replace((char) 209, ',');
						route.show = true;
						route.filepath = file.getCanonicalPath();
			    		try
			    		{
			    			int color = Integer.parseInt(fields[4]);
			    			if (color != 0)
			    				route.lineColor = bgr2rgb(color);
			    		}
			    		catch (NumberFormatException e)
			    		{
			    		}
						routeNum = rtn;
						wptNum = 0;
					}
					else
					{
						reader.close();
						throw new IllegalArgumentException("Bad route file");
					}
				}
				else if ("W".equals(fields[0]))
				{
					if (rtn != routeNum)
					{
						reader.close();
						throw new IllegalArgumentException("Bad route file");
					}
					int wpn = Integer.valueOf(fields[2]);
					if (wpn != wptNum + 1)
					{
						reader.close();
						throw new IllegalArgumentException("Bad route file");
					}
					wptNum++;
				    //W, 1, 2, 35,35              , -26.550290, 152.416844,35641.5077900, 0, 1, 0,   8388608,     65535,, 0, 0
			    	if ("".equals(fields[4]))
			    		fields[4] = "RWPT"+wptNum;
					route.addWaypoint(new Waypoint(fields[4].replace((char) 209, ','), fields[13].replace((char) 209, ','), Double.parseDouble(fields[5]), Double.parseDouble(fields[6])));
				}
			}
		}
		else
		{
			reader.close();
			throw new IllegalArgumentException("Bad route file");
		}

		return routes;
	}

	public static void saveRouteToFile(final File file, final String charset, final Route route) throws IOException
	{
	    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), charset));
		
		writer.write("H1,OziExplorer CE Route2 File Version 1.0\n" +
				"H2,WGS 84\n");

		// Field 1 : H3
		// Field 2 : route name (no commas allowed)
		// Field 3 : ??? (we use it for route line width)
		// Field 4 : route color (RGB)
		writer.write("H3,"+route.name.replace(',', (char) 209)+","+
				String.valueOf(route.width)+","+
				String.valueOf(rgb2bgr(route.lineColor))+"\n");
	
		//Field 1 : W
		//Field 2 : Name
		//Field 3 : Latitude - decimal degrees
		//Field 4 : Longitude - decimal degrees
		//Field 5 : Code - 0 if normal, 1 if silent
	
		// W,Tsapelka,  58.0460242,  28.9465437,0
	
        List<Waypoint> waypoints = route.getWaypoints();
        synchronized (waypoints)
        {  
	        for (Waypoint wpt : waypoints)
	        {
	        	writer.write("W,");
	        	writer.write(wpt.name.replace(',', (char) 209)+",");
	        	writer.write(coordFormat.format(wpt.latitude)+","+coordFormat.format(wpt.longitude)+",");
	        	if (wpt.silent)
	        		writer.write("1");
	        	else
	        		writer.write("0");
	        	// Format extension (probably not compatible with OziExplorer)
	        	if (wpt.proximity > 0)
	        		writer.write("," + String.valueOf(wpt.proximity));
	        	writer.write("\n");
	        }
        }
        writer.close();
	}

	public static int bgr2rgb(int bgr)
	{
		return 0xFF000000 | ((bgr & 0x00FF0000) >>> 16) | ((bgr & 0x000000FF) << 16) | (bgr & 0x0000FF00);
	}

	public static int rgb2bgr(int rgb)
	{
		return ((rgb & 0x00FF0000) >>> 16) | ((rgb & 0x000000FF) << 16) | (rgb & 0x0000FF00);
	}

	public static void loadDatums(File file) throws IOException
	{
	    BufferedReader reader = new BufferedReader(new FileReader(file));
	    String line;
	    while ((line = reader.readLine()) != null)
		{
			String[] fields = CSV.parseLine(line);
			if (fields.length == 5)
			{
				try
				{
					int e = Integer.parseInt(fields[1]);
					Ellipsoid ellipsoid = MapLoader.getEllipsoid(e);
					double dx = Double.parseDouble(fields[2]);
					double dy = Double.parseDouble(fields[3]);
					double dz = Double.parseDouble(fields[4]);
					if (ellipsoid != null)
					{
						// no need to get object reference because it is registered in constructor
						new Datum(fields[0], ellipsoid, dx, dy, dz);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	    reader.close();
	}
}
