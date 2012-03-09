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
 * Helper class to read and write KML files.
 * 
 * @author Andrey Novikov
 */
public class KmlFiles
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
		parser.parse(file, new KmlParser(file.getName(), waypoints, null));
		
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
		parser.parse(file, new KmlParser(file.getName(), null, tracks));
		
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
	public static List<Route> loadRoutesFromFile(File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Track> tracks = loadTracksFromFile(file);
		List<Route> routes = new ArrayList<Route>();
		for (Track track : tracks)
		{
			Route route = new Route(track.name, track.description, track.show);
			int i = 0;
			for (Track.TrackPoint tp : track.getPoints())
			{
				String name = "RWPT"+i;
				route.addWaypoint(name, tp.latitude, tp.longitude);
				i++;
			}
			routes.add(route);
		}
		return routes;
	}
}

/**
 * Simple SAX parser of KML files. Loads GPX waypoints, tracks and routes.
 * KML format does not have specific entity for route, so user should decide what to treat as track and what as route.
 * 
 * @author Andrey Novikov
 */
class KmlParser extends DefaultHandler
{
	private static final String PLACEMARK = "Placemark";
	private static final String POINT = "Point";
	private static final String LINESTRING = "LineString";
	private static final String NAME = "name";
	private static final String COORDINATES = "coordinates";
	private static final String DESCRIPTION = "description";

	private StringBuilder builder;
	
	private List<Waypoint> waypoints;
	private Waypoint waypoint;
	private boolean ispoint;
	private List<Track> tracks;
	private Track track;
	private boolean istrack;
	private String filename;
	
	public KmlParser(String filename, List<Waypoint> waypoints, List<Track> tracks)
	{
		super();
		builder = new StringBuilder();
		this.waypoints = waypoints;
		this.tracks = tracks;
		this.filename = filename;
		ispoint = false;
		istrack = false;
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
		if (localName.equalsIgnoreCase(PLACEMARK))
		{
			waypoint = new Waypoint();
			track = new Track();
			ispoint = false;
			istrack = false;
		}
		else if (localName.equalsIgnoreCase(POINT))
		{
			ispoint = true;
		}
		else if (localName.equalsIgnoreCase(LINESTRING))
		{
			istrack = true;
		}
		super.startElement(uri, localName, qName, attributes);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		if (localName.equalsIgnoreCase(PLACEMARK))
		{
			if (ispoint && waypoints != null && waypoint != null)
			{
				if (waypoint.name.equals(""))
					waypoint.name = "WPT"+waypoints.size();
				waypoints.add(waypoint);
			}
			if (istrack && tracks != null && track != null)
			{
				if (track.name.equals(""))
				{
					track.name = filename;
					if (tracks.size() > 0)
						track.name += "_"+tracks.size();
				}
				track.show = true;
				tracks.add(track);
			}
			waypoint = null;
			track = null;
		}
		else if (localName.equalsIgnoreCase(NAME))
		{
			if (waypoint != null)
				waypoint.name = builder.toString().trim();
			if (track != null)
				track.name = builder.toString().trim();
		}
		else if (localName.equalsIgnoreCase(DESCRIPTION))
		{
			if (waypoint != null)
				waypoint.description = builder.toString().trim();
			if (track != null)
				track.description = builder.toString().trim();
		}
		else if (localName.equalsIgnoreCase(COORDINATES))
		{
			if (ispoint)
			{
				String[] coords = builder.toString().split(",");
				waypoint.latitude = Double.parseDouble(coords[1].trim());
				waypoint.longitude = Double.parseDouble(coords[0].trim());
			}
			if (istrack)
			{
				String[] lines = builder.toString().split("\n");
				boolean continous = false;
				for (String line : lines)
				{
					String[] coords = line.split(",");
					if (coords.length == 3)
					{
						track.addTrackPoint(continous, Double.parseDouble(coords[1].trim()), Double.parseDouble(coords[0].trim()), Double.parseDouble(coords[2].trim()), 0.0, 0);
						continous = true;
					}
				}
			}
		}
		super.endElement(uri, localName, qName);
	}
}
