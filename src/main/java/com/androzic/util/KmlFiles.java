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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import android.graphics.Color;
import android.util.Log;
import android.util.Xml;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Track.TrackPoint;
import com.androzic.data.Waypoint;

/**
 * Helper class to read and write KML files.
 * 
 * @author Andrey Novikov
 */
public class KmlFiles
{
	public static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";

	/**
	 * Loads waypoints from file.
	 * 
	 * @param file valid <code>File</code> with waypoints
	 * @return <code>List</code> of <code>Waypoint</code>s
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static List<Waypoint> loadWaypointsFromFile(final File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Waypoint> waypoints = new ArrayList<>();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		parser.parse(file, new KmlParser(file.getName(), waypoints, null));
		
		return waypoints;
	}

	/**
	 * Loads tracks from file.
	 * 
	 * @param file valid <code>File</code> with tracks
	 * @return <code>List</code> of <code>Track</code>s
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static List<Track> loadTracksFromFile(final File file) throws SAXException, IOException, ParserConfigurationException
	{
		List<Track> tracks = new ArrayList<>();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		parser.parse(file, new KmlParser(file.getName(), null, tracks));
		return tracks;
	}

	/**
	 * Saves track to file.
	 * 
	 * @param file valid <code>File</code>
	 * @param track <code>Track</code> object containing the list of track points to save
	 * @throws IOException
	 */
	public static void saveTrackToFile(final File file, final Track track) throws IOException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
		XmlSerializer serializer = Xml.newSerializer();
		serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false)));
		serializer.setOutput(writer);
		serializer.startDocument("UTF-8", null);
		serializer.setPrefix("", KML_NAMESPACE);
		serializer.startTag(KML_NAMESPACE, KmlParser.KML);
		serializer.startTag(KML_NAMESPACE, KmlParser.DOCUMENT);
		serializer.startTag(KML_NAMESPACE, KmlParser.STYLE);
		serializer.attribute("", KmlParser.ID, "trackStyle");
		serializer.startTag(KML_NAMESPACE, KmlParser.LINESTYLE);
		serializer.startTag(KML_NAMESPACE, KmlParser.COLOR);
		serializer.text(String.format("%08X", KmlParser.reverseColor(track.color)));
		serializer.endTag(KML_NAMESPACE, KmlParser.COLOR);
		serializer.startTag(KML_NAMESPACE, KmlParser.WIDTH);
		serializer.text(String.valueOf(track.width));
		serializer.endTag(KML_NAMESPACE, KmlParser.WIDTH);
		serializer.endTag(KML_NAMESPACE, KmlParser.LINESTYLE);
		serializer.endTag(KML_NAMESPACE, KmlParser.STYLE);
		serializer.startTag(KML_NAMESPACE, KmlParser.FOLDER);
		serializer.startTag(KML_NAMESPACE, KmlParser.NAME);
		serializer.text(track.name);
		serializer.endTag(KML_NAMESPACE, KmlParser.NAME);
		serializer.startTag(KML_NAMESPACE, KmlParser.OPEN);
		serializer.text("0");
		serializer.endTag(KML_NAMESPACE, KmlParser.OPEN);
		serializer.startTag(KML_NAMESPACE, KmlParser.TIMESPAN);
		serializer.startTag(KML_NAMESPACE, KmlParser.BEGIN);
		serializer.text(sdf.format(new Date(track.getPoint(0).time)));
		serializer.endTag(KML_NAMESPACE, KmlParser.BEGIN);
		serializer.startTag(KML_NAMESPACE, KmlParser.END);
		serializer.text(sdf.format(new Date(track.getLastPoint().time)));
		serializer.endTag(KML_NAMESPACE, KmlParser.END);
		serializer.endTag(KML_NAMESPACE, KmlParser.TIMESPAN);
		serializer.startTag(KML_NAMESPACE, KmlParser.STYLE);
		serializer.startTag(KML_NAMESPACE, KmlParser.LISTSTYLE);
		serializer.startTag(KML_NAMESPACE, KmlParser.LISTITEMTYPE);
		serializer.text("checkHideChildren");
		serializer.endTag(KML_NAMESPACE, KmlParser.LISTITEMTYPE);
		serializer.endTag(KML_NAMESPACE, KmlParser.LISTSTYLE);
		serializer.endTag(KML_NAMESPACE, KmlParser.STYLE);
		
		int part = 1;
		boolean first = true;
		startTrackPart(serializer, part, track.name);
		List<TrackPoint> trackPoints = track.getAllPoints();
		synchronized (trackPoints)
		{
			for (TrackPoint tp : trackPoints)
			{
				if (!tp.continous && !first)
				{
					stopTrackPart(serializer);
					part++;
					startTrackPart(serializer, part, track.name);
				}
				serializer.text(String.format("%f,%f,%f ", tp.longitude, tp.latitude, tp.elevation));
				first = false;
			}
		}
		stopTrackPart(serializer);
		serializer.endTag(KML_NAMESPACE, KmlParser.FOLDER);
		serializer.endTag(KML_NAMESPACE, KmlParser.DOCUMENT);
		serializer.endTag(KML_NAMESPACE, KmlParser.KML);
		serializer.endDocument();
		serializer.flush();
		writer.close();
	}

	private static void startTrackPart(XmlSerializer serializer, int part, String name) throws IllegalArgumentException, IllegalStateException, IOException
	{
		serializer.startTag(KML_NAMESPACE, KmlParser.PLACEMARK);
		serializer.startTag(KML_NAMESPACE, KmlParser.NAME);
		serializer.text(String.format("Part %d - %s", part, name));
		serializer.endTag(KML_NAMESPACE, KmlParser.NAME);
		serializer.startTag(KML_NAMESPACE, KmlParser.STYLEURL);
		serializer.text("#trackStyle");
		serializer.endTag(KML_NAMESPACE, KmlParser.STYLEURL);
		serializer.startTag(KML_NAMESPACE, KmlParser.LINESTRING);
		serializer.startTag(KML_NAMESPACE, KmlParser.TESSELLATE);
		serializer.text("1");
		serializer.endTag(KML_NAMESPACE, KmlParser.TESSELLATE);
		serializer.startTag(KML_NAMESPACE, KmlParser.COORDINATES);
	}

	private static void stopTrackPart(XmlSerializer serializer) throws IllegalArgumentException, IllegalStateException, IOException
	{
		serializer.endTag(KML_NAMESPACE, KmlParser.COORDINATES);
		serializer.endTag(KML_NAMESPACE, KmlParser.LINESTRING);
		serializer.endTag(KML_NAMESPACE, KmlParser.PLACEMARK);
	}

	/**
	 * Loads routes from file.
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
		List<Route> routes = new ArrayList<>();
		for (Track track : tracks)
		{
			Route route = new Route(track.name, track.description, track.show);
			int i = 0;
			for (Track.TrackPoint tp : track.getAllPoints())
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
	static final String TAG = "KmlFiles";
	
	static final String KML = "kml";
	static final String ID = "id";
	static final String DOCUMENT = "Document";
	static final String FOLDER = "Folder";
	static final String OPEN = "open";
	static final String TIMESPAN = "TimeSpan";
	static final String BEGIN = "begin";
	static final String END = "end";
	static final String PLACEMARK = "Placemark";
	static final String POINT = "Point";
	static final String LINESTRING = "LineString";
	static final String TESSELLATE = "tessellate";
	static final String NAME = "name";
	static final String COORDINATES = "coordinates";
	static final String DESCRIPTION = "description";
	static final String STYLE = "Style";
	static final String LINESTYLE = "LineStyle";
	static final String LISTSTYLE = "ListStyle";
	static final String ICONSTYLE = "IconStyle";
	static final String STYLEURL = "styleUrl";
	static final String COLOR = "color";
	static final String WIDTH = "width";
	static final String LISTITEMTYPE = "listItemType";

	private StringBuilder builder;
	
	private Map<String, Style> styles;
	private Style style;
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
		styles = new HashMap<>();
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException
	{
		builder.append(ch, start, length);
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
		else if (localName.equalsIgnoreCase(STYLE))
		{
			style = new Style();
			style.id = attributes.getValue(ID);
		}
		else if (localName.equalsIgnoreCase(LINESTYLE))
		{
			style.lineStyle = new LineStyle();
		}
		else if (localName.equalsIgnoreCase(ICONSTYLE))
		{
			style.iconStyle = new IconStyle();
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		if (localName.equalsIgnoreCase(DOCUMENT))
		{
			if (styles.isEmpty())
				return;
			if (waypoints != null)
			{
				for (Waypoint waypoint : waypoints)
				{
					if (waypoint.style != null)
						applyWaypointStyle(waypoint, styles.get(waypoint.style));
				}
			}
			if (tracks != null)
			{
				for (Track track : tracks)
				{
					if (track.style != null)
						applyTrackStyle(track, styles.get(track.style));
				}
			}
		}
		else if (localName.equalsIgnoreCase(PLACEMARK))
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
				String[] points = builder.toString().split("[\\s\\n]");
				boolean continous = false;
				for (String point : points)
				{
					String[] coords = point.split(",");
					if (coords.length == 3)
					{
						track.addPoint(continous, Double.parseDouble(coords[1].trim()), Double.parseDouble(coords[0].trim()), Double.parseDouble(coords[2].trim()), 0.0, 0.0, 0.0, 0);
						continous = true;
					}
				}
			}
		}
		else if (localName.equalsIgnoreCase(STYLEURL))
		{
			// TODO Only local styles are currently accepted
			String id = builder.toString().trim();
			id = id.substring(id.indexOf("#") + 1);
			if (waypoint != null)
				waypoint.style = id;
			if (track != null)
				track.style = id;
		}
		else if (localName.equalsIgnoreCase(STYLE))
		{
			if (style != null)
			{
				if (style.id != null)
					styles.put(style.id, style);
				style = null;
			}
		}
		else if (localName.equalsIgnoreCase(COLOR))
		{
			if (style != null && style.iconStyle != null)
			{
				try
				{
					style.iconStyle.color = reverseColor((int) Long.parseLong(builder.toString().trim(), 16));
				}
				catch (NumberFormatException e)
				{
					style.iconStyle.color = Color.RED;
					Log.e(TAG, "Color format error", e);
				}
			}
			else if (style != null && style.lineStyle != null)
			{
				try
				{
					style.lineStyle.color = reverseColor((int) Long.parseLong(builder.toString().trim(), 16));
				}
				catch (NumberFormatException e)
				{
					style.lineStyle.color = Color.RED;
					Log.e(TAG, "Color format error", e);
				}
			}
		}
		else if (localName.equalsIgnoreCase(WIDTH))
		{
			if (style != null && style.lineStyle != null)
			{
				try
				{
					style.lineStyle.width = Integer.parseInt(builder.toString().trim());
				}
				catch (NumberFormatException e)
				{
					style.lineStyle.width = 1;
					Log.e(TAG, "Width format error", e);
				}
			}
		}
	}

	private void applyWaypointStyle(Waypoint wpt, Style stl)
	{
		if (stl != null && stl.iconStyle != null)
		{
			wpt.backcolor = stl.iconStyle.color;
		}
	}

	private void applyTrackStyle(Track trk, Style stl)
	{
		if (stl != null && stl.lineStyle != null)
		{
			trk.color = stl.lineStyle.color;
			trk.width = stl.lineStyle.width;
		}
	}
	
	/**
	 * Converts ARGB to ABGR and vice versa
	 */
	static int reverseColor(int color)
	{
		Log.e(TAG, String.format("CB %8X", color));
		int c = ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16) | (color & 0xFF00FF00);
		Log.e(TAG, String.format("CA %8X", c));
		return ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16) | (color & 0xFF00FF00);
	}

	class Style
	{
		String id;
		LineStyle lineStyle;
		IconStyle iconStyle;
	}
	
	class ColorStyle
	{
		int color;
	}

	class LineStyle extends ColorStyle
	{
		int width;
	}

	class IconStyle extends ColorStyle
	{
		//TODO Add processing for this style field
		String icon;
	}

	/*
	<StyleMap id='line-DB4436-1-nodesc'>
		<Pair>
			<key>normal</key>
			<styleUrl>#line-DB4436-1-nodesc-normal</styleUrl>
		</Pair>
		<Pair>
			<key>highlight</key>
			<styleUrl>#line-DB4436-1-nodesc-highlight</styleUrl>
		</Pair>
	</StyleMap>
	*/
}
