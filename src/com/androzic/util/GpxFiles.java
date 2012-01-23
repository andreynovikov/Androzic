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

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;

/**
 * Helper class to read and write GPX files.
 * 
 * @author Andrey Novikov
 */
public class GpxFiles
{	
	/**
	 * Loads waypoints from file
	 * 
	 * @param file valid <code>File</code> with waypoints
	 * @return <code>List</code> of <code>Waypoint</code>s
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static List<Waypoint> loadWaypointsFromFile(final File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Waypoint> waypoints = new ArrayList<Waypoint>();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = null;

		parser = factory.newSAXParser();
		parser.parse(file, new GpxParser(file.getName(), waypoints, null, null));
		
		return waypoints;
	}

	/**
	 * Loads tracks from file
	 * 
	 * @param file valid <code>File</code> with tracks
	 * @return <code>List</code> of <code>Track</code>s
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public static List<Track> loadTracksFromFile(final File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Track> tracks = new ArrayList<Track>();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = null;

		parser = factory.newSAXParser();
		parser.parse(file, new GpxParser(file.getName(), null, tracks, null));
		
		if (tracks.size() > 0)
		{
			tracks.get(0).filepath = file.getCanonicalPath();
		}
		
		return tracks;
	}

	/**
	 * Loads routes from file
	 * 
	 * @param file valid <code>File</code> with routes
	 * @return <code>List</code> of <code>Route</code>s
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static List<Route> loadRoutesFromFile(final File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Route> routes = new ArrayList<Route>();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = null;

		parser = factory.newSAXParser();
		parser.parse(file, new GpxParser(file.getName(), null, null, routes));
		
		if (routes.size() > 0)
		{
			routes.get(0).filepath = file.getCanonicalPath();
		}
		
		return routes;
	}
}

/**
 * Simple SAX parser of GPX files. Loads GPX waypoints, tracks and routes.
 * 
 * @author Andrey Novikov
 */
class GpxParser extends DefaultHandler
{
	private static final String LAT = "lat";
	private static final String LON = "lon";
	private static final String NAME = "name";
	private static final String DESC = "desc";
	private static final String ELE = "ele";
	private static final String TIME = "time";
	private static final String WPT = "wpt";
	private static final String RTE = "rte";
	private static final String RTEPT = "rtept";
	private static final String TRK = "trk";
	private static final String TRKSEG = "trkseg";
	private static final String TRKPT = "trkpt";
	
	private static final DateFormat trktime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private StringBuilder builder;
	
	private List<Waypoint> waypoints;
	private Waypoint waypoint;
	private List<Track> tracks;
	private Track track;
	private Track.TrackPoint trkpt;
	private boolean continous;
	private List<Route> routes;
	private Route route;
	private Waypoint rtwpt;
	private String filename;
	
	public GpxParser(String filename, List<Waypoint> waypoints, List<Track> tracks, List<Route> routes)
	{
		super();
		builder = new StringBuilder();
		this.waypoints = waypoints;
		this.tracks = tracks;
		this.routes = routes;
		this.filename = filename;
		continous = false;
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException
	{
		builder.append(ch, start, length);
		super.characters(ch, start, length);
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
	{
		builder.delete(0, builder.length());
		// <wpt>
		if (localName.equalsIgnoreCase(WPT) && waypoints != null)
		{
			waypoint = new Waypoint();
			waypoint.latitude = Double.parseDouble(attributes.getValue(LAT));
			waypoint.longitude = Double.parseDouble(attributes.getValue(LON));
		}
		// <rte>
		if (localName.equalsIgnoreCase(RTE) && routes != null)
		{
			route = new Route();
		}
		// <rtept>
		if (localName.equalsIgnoreCase(RTEPT) && route != null)
		{
			rtwpt = new Waypoint();
			rtwpt.latitude = Double.parseDouble(attributes.getValue(LAT));
			rtwpt.longitude = Double.parseDouble(attributes.getValue(LON));
		}
		// <trk>
		if (localName.equalsIgnoreCase(TRK) && tracks != null)
		{
			track = new Track();
		}
		// <trkseg>
		if (localName.equalsIgnoreCase(TRKSEG))
		{
			continous = false;
		}
		// <trkpt>
		if (localName.equalsIgnoreCase(TRKPT) && track != null)
		{
			track.addTrackPoint(continous, Double.parseDouble(attributes.getValue(LAT)), Double.parseDouble(attributes.getValue(LON)), 0.0, 0.0, 0);
			trkpt = track.getLastPoint();
			continous = true;
		}
		super.startElement(uri, localName, qName, attributes);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		// </wpt>
		if (waypoint != null && localName.equalsIgnoreCase(WPT))
		{
			if (waypoint.name.equals(""))
				waypoint.name = "WPT"+waypoints.size();
			waypoints.add(waypoint);
			waypoint = null;
		}
		// </rte>
		else if (route != null && localName.equalsIgnoreCase(RTE))
		{
			if (route.name.equals(""))
			{
				route.name = filename;
				if (routes.size() > 0)
					route.name += "_"+routes.size();
			}
			route.show = true;
			routes.add(route);
			route = null;
		}
		// <rtept>
		else if (rtwpt != null && localName.equalsIgnoreCase(RTEPT))
		{
			if (rtwpt.name.equals(""))
				rtwpt.name = "RWPT"+route.length();
			route.addWaypoint(rtwpt);
			rtwpt = null;
		}
		// </trk>
		else if (track != null && localName.equalsIgnoreCase(TRK))
		{
			if (track.name.equals(""))
			{
				track.name = filename;
				if (tracks.size() > 0)
					track.name += "_"+tracks.size();
			}
			track.show = true;
			tracks.add(track);
			track = null;
		}
		// </trkpt>
		else if (trkpt != null && localName.equalsIgnoreCase(TRKPT))
		{
			trkpt = null;
		}
		// </name>
		else if (localName.equalsIgnoreCase(NAME))
		{
			if (waypoint != null)
				waypoint.name = builder.toString().trim();
			if (route != null)
				route.name = builder.toString().trim();
			if (rtwpt != null)
				rtwpt.name = builder.toString().trim();
			if (track != null)
				track.name = builder.toString().trim();
		}
		// </desc>
		else if (localName.equalsIgnoreCase(DESC))
		{
			if (waypoint != null)
				waypoint.description = builder.toString().trim();
			if (route != null)
				route.description = builder.toString().trim();
			if (rtwpt != null)
				rtwpt.description = builder.toString().trim();
			if (track != null)
				track.description = builder.toString().trim();
		}
		// </ele>
		else if (localName.equalsIgnoreCase(ELE))
		{
			if (trkpt != null)
				trkpt.elevation = Double.parseDouble(builder.toString().trim());
		}
		// </time>
		else if (localName.equalsIgnoreCase(TIME))
		{
			if (trkpt != null)
			{
				try
				{
					trkpt.time = trktime.parse(builder.toString().trim()).getTime();
				}
				catch (ParseException e)
				{
					e.printStackTrace();
				}
			}
		}
		
		super.endElement(uri, localName, qName);
	}
}

