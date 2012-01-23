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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.jhlabs.map.GeodeticPosition;
import com.jhlabs.map.ReferenceException;
import com.jhlabs.map.UTMReference;

public class StringFormatter
{
	// http://code.google.com/p/android/issues/detail?id=2626
	final static DecimalFormat coordDegFormat = new DecimalFormat("#0.000000", new DecimalFormatSymbols(Locale.ENGLISH));
	final static DecimalFormat coordIntFormat = new DecimalFormat("00", new DecimalFormatSymbols(Locale.ENGLISH));
	final static DecimalFormat coordMinFormat = new DecimalFormat("00.0000", new DecimalFormatSymbols(Locale.ENGLISH));
	final static DecimalFormat coordSecFormat = new DecimalFormat("00.000", new DecimalFormatSymbols(Locale.ENGLISH));
	
	final static DecimalFormat timeFormat = new DecimalFormat("00");

	public static double distanceFactor = 1000.0;
	public static String distanceAbbr = "km";
	public static double distanceShortFactor = 1.0;
	public static String distanceShortAbbr = "m";
	
	public static final String distanceH(final double distance)
	{
		return distanceH(distance, 2000);
	}

	public static final String distanceH(double distance, int threshold)
	{
		String[] dist = distanceC(distance, threshold);
		return dist[0] + " " + dist[1];
	}

	public static final String distanceH(double distance, String format, int threshold)
	{
		String[] dist = distanceC(distance, format, threshold);
		return dist[0] + " " + dist[1];
	}

	public static final String[] distanceC(final double distance)
	{
		return distanceC(distance, 2000);
	}

	public static final String[] distanceC(final double distance, int threshold)
	{
//		return new String[] {String.valueOf(Math.round(dist)), distunit};
		return distanceC(distance, "%.0f", threshold);
	}

	public static final String[] distanceC(final double distance, final String format, int threshold)
	{
		double dist = distance * distanceShortFactor;
		String distunit = distanceShortAbbr;
		if (Math.abs(dist) > threshold)
		{
			dist = dist / distanceShortFactor / 1000 * distanceFactor;
			distunit = distanceAbbr;
		}

		return new String[] {String.format(format, dist), distunit};
	}

	public static final String coordinate(int format, double coordinate)
	{
		switch (format)
		{
			case 0:
			{
				return coordDegFormat.format(coordinate);
			}
			case 1:
			{
				final double sign = Math.signum(coordinate);
				final double coord = Math.abs(coordinate);
				final int degrees = (int) Math.floor(coord);
				final double minutes = (coord - degrees) * 60;
				return coordIntFormat.format(sign*degrees) + "° "
						+ coordMinFormat.format(minutes) + "'";
			}
			case 2:
			{
				final double sign = Math.signum(coordinate);
				final double coord = Math.abs(coordinate);
				final int degrees = (int) Math.floor(coord);
				final double min = (coord - degrees) * 60;
				final int minutes = (int) Math.floor(min);
				final double seconds = (min - minutes) * 60;
				return coordIntFormat.format(sign*degrees) + "° "
						+ coordIntFormat.format(minutes) + "' "
						+ coordSecFormat.format(seconds) + "\"";
			}
		}
		return String.valueOf(coordinate);
	}

	public static final String coordinates(int format, String delimeter, double latitude, double longitude)
	{
		switch (format)
		{
			case 0:
			case 1:
			case 2:
			{
				return coordinate(format, latitude) + delimeter + coordinate(format, longitude);
			}
			case 3:
			{
				String coords; 
				try
				{
					coords = UTMReference.toUTMRefString(new GeodeticPosition(latitude, longitude));
					return coords;
				}
				catch (ReferenceException ex)
				{
				}
				
			}
		}
		return String.valueOf(latitude) + delimeter + String.valueOf(longitude);
	}

	public static String bearingH(double bearing)
	{
		return String.format("%.0f", bearing)+"°";
	}

	public static final String bearingSimpleH(double bearing)
	{
		if (bearing <  22 || bearing >= 338) return "N";
		if (bearing <  67 && bearing >=  22) return "NE";
		if (bearing < 112 && bearing >=  67) return "E";
		if (bearing < 158 && bearing >= 112) return "SE";
		if (bearing < 202 && bearing >= 158) return "S";
		if (bearing < 248 && bearing >= 202) return "SW";
		if (bearing < 292 && bearing >= 248) return "W";
		if (bearing < 338 && bearing >= 292) return "NW";
		return ".";
	}
	
	public static final String timeH(int minutes)
	{
		String[] time = timeC(minutes);
		return time[0] + " " + time[1];
	}
	
	public static final String[] timeC(int minutes)
	{
		int hour = 0;
		int min = minutes;

		if (min <= 1)
			return new String[] {"< 1", "min"};
		
		if (min > 59)
		{
			hour = (int) Math.floor(min / 60);
			min = min - hour * 60;
		}
		if (hour > 23)
			return new String[] {"> 24", "h"};
		
		return new String[] {timeFormat.format(hour)+":"+timeFormat.format(min), "min"};
	}
}
