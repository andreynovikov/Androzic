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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jhlabs.map.GeodeticPosition;
import com.jhlabs.map.ReferenceException;
import com.jhlabs.map.UTMReference;

public class CoordinateParser
{
	/*
	 *  -- DMS --
	 * 	45:26:46N,          65:56:55W
	 *	45:26:46.302N,      65:56:55.903W
	 *	45°26'21"N,         65°58'36"W
	 *  45°26'21.291"N,     65°58'36.012"W
	 *  45° 26' 21.291" N,  65° 58' 36.012" W
	 *  45°26'21",         -65°58'36"
	 *  45°26'21.291",     -65°58'36.012"
	 *  45° 26' 21.291",   -65° 58' 36.012"
	 *	45N26 21,           65W58 36
	 *  45N26 21.015,       65W58 36.289
	 *  -- DM --
	 *	45°26'N,            65°58'36"W
	 *	45°26.7717'N,       65°58.0127'W
	 *	45° 26.7717' N,     65° 58.0127' W
	 *	45°26',            -65°58'
	 *	45°26.7717',       -65°58.0127'
	 *	45° 26.7717',      -65° 58.0127'
	 *  -- D --
	 * N45.446195,         W65.948862
	 *	45.446195N,         65.948862W
	 *	45.446195,         -65.948862
	 * -- UTM --
	 *  37U 414703 6186238
	 */
	public static double[] parse(String string) throws IllegalArgumentException
	{
		if (string == null)
			throw new IllegalArgumentException("Empty string");
		
		String ps;
		Pattern p;
		Matcher m;
		double c[] = new double[]{Double.NaN, Double.NaN};
		
		// 45:26:46N, 65:56:55W
		// 45:26:46.302N, 65:56:55.903W
		ps = "(\\d{1,2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)([NS])\\s*,?\\s+(\\d{1,3}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)([EW])";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			String min1 = m.group(2);
			String sec1 = m.group(3);
			String dir1 = m.group(4);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60 + Double.parseDouble(sec1) / 3600;
			if ("S".equals(dir1))
				c[0] = -c[0];
			String deg2 = m.group(5);
			String min2 = m.group(6);
			String sec2 = m.group(7);
			String dir2 = m.group(8);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60 + Double.parseDouble(sec2) / 3600;
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45°26'21"N, 65°58'36"W
		// 45°26'21.291"N, 65°58'36.012"W
		// 45° 26' 21.291" N, 65° 58' 36.012" W
		ps = "(\\d{1,2})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s?([NS])\\s*,?\\s+(\\d{1,3})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s?([EW])";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			String min1 = m.group(2);
			String sec1 = m.group(3);
			String dir1 = m.group(4);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60 + Double.parseDouble(sec1) / 3600;
			if ("S".equals(dir1))
				c[0] = -c[0];
			String deg2 = m.group(5);
			String min2 = m.group(6);
			String sec2 = m.group(7);
			String dir2 = m.group(8);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60 + Double.parseDouble(sec2) / 3600;
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45°26'21",         -65°58'36"
		// 45°26'21.291",     -65°58'36.012"
		// 45° 26' 21.291",   -65° 58' 36.012"
		ps = "(\\-)?(\\d{1,2})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s*,?\\s+(\\-)?(\\d{1,3})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String sgn1 = m.group(1);
			String deg1 = m.group(2);
			String min1 = m.group(3);
			String sec1 = m.group(4);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60 + Double.parseDouble(sec1) / 3600;
			if ("-".equals(sgn1))
				c[0] = -c[0];
			String sgn2 = m.group(5);
			String deg2 = m.group(6);
			String min2 = m.group(7);
			String sec2 = m.group(8);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60 + Double.parseDouble(sec2) / 3600;
			if ("-".equals(sgn2))
				c[1] = -c[1];
			return c;
		}
		// 45N26 21, 65W58 36
		// 45N26 21.015, 65W58 36.289
		ps = "(\\d{1,2})([NS])(\\d{2})\\s(\\d{1,2}(?:\\.\\d+)?)\\s*,?\\s+(\\d{1,3})([EW])(\\d{2})\\s(\\d{1,2}(?:\\.\\d+)?)";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			String min1 = m.group(3);
			String sec1 = m.group(4);
			String dir1 = m.group(2);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60 + Double.parseDouble(sec1) / 3600;
			if ("S".equals(dir1))
				c[0] = -c[0];
			String deg2 = m.group(5);
			String min2 = m.group(7);
			String sec2 = m.group(8);
			String dir2 = m.group(6);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60 + Double.parseDouble(sec2) / 3600;
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45°26'N, 65°58'36"W
		// 45°26.7717'N, 65°58.0127'W
		// 45° 26.7717' N, 65° 58.0127' W
		ps = "(\\d{1,2})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s?([NS])\\s*,?\\s+(\\d{1,3})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s?([EW])";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			String min1 = m.group(2);
			String dir1 = m.group(3);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60;
			if ("S".equals(dir1))
				c[0] = -c[0];
			String deg2 = m.group(4);
			String min2 = m.group(5);
			String dir2 = m.group(6);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60;
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45°26', -65°58'
		// 45°26.7717', -65°58.0127'
		// 45° 26.7717', -65° 58.0127'
		ps = "(\\-)?(\\d{1,2})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s*,?\\s+(\\-)?(\\d{1,3})°\\s?(\\d{2}(?:\\.\\d+)?)'";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String sgn1 = m.group(1);
			String deg1 = m.group(2);
			String min1 = m.group(3);
			c[0] = Double.parseDouble(deg1) + Double.parseDouble(min1) / 60;
			if ("-".equals(sgn1))
				c[0] = -c[0];
			String sgn2 = m.group(4);
			String deg2 = m.group(5);
			String min2 = m.group(6);
			c[1] = Double.parseDouble(deg2) + Double.parseDouble(min2) / 60;
			if ("-".equals(sgn2))
				c[1] = -c[1];
			return c;
		}
		// N45.446195, W65.948862
		ps = "([NS])(\\d{1,2}\\.\\d+)\\s*,?\\s+([EW])(\\d{1,3}\\.\\d+)";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String dir1 = m.group(1);
			String deg1 = m.group(2);
			c[0] = Double.parseDouble(deg1);
			if ("S".equals(dir1))
				c[0] = -c[0];
			String dir2 = m.group(3);
			String deg2 = m.group(4);
			c[1] = Double.parseDouble(deg2);
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45.446195N, 65.948862W
		ps = "(\\d{1,2}\\.\\d+)([NS])\\s*,?\\s+(\\d{1,3}\\.\\d+)([EW])";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			String dir1 = m.group(2);
			c[0] = Double.parseDouble(deg1);
			if ("S".equals(dir1))
				c[0] = -c[0];
			String deg2 = m.group(3);
			String dir2 = m.group(4);
			c[1] = Double.parseDouble(deg2);
			if ("W".equals(dir2))
				c[1] = -c[1];
			return c;
		}
		// 45.446195, -65.948862
		ps = "(\\-?\\d{1,2}\\.\\d+)\\s*,?\\s+(\\-?\\d{1,3}\\.\\d+)";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String deg1 = m.group(1);
			c[0] = Double.parseDouble(deg1);
			String deg2 = m.group(2);
			c[1] = Double.parseDouble(deg2);
			return c;
		}
		// 37U 414703 6186238
		ps = "(\\d{1,2})([A-HJ-NP-Z])\\s(\\d+)\\s(\\d+)";
		p = Pattern.compile(ps);
		m = p.matcher(string);
		if (m.find())
		{
			String zon = m.group(1);
			String bnd = m.group(2);
			String est = m.group(3);
			String nrt = m.group(4);
			int zone = Integer.parseInt(zon);
			double easting = Double.parseDouble(est);
			double northing = Double.parseDouble(nrt);
			try
			{
				UTMReference utm = new UTMReference(zone, bnd.charAt(0), easting, northing);
				GeodeticPosition pos = utm.toLatLng();
				c[0] = pos.lat;
				c[1] = pos.lon;
				return c;
			}
			catch (ReferenceException e)
			{
			}
		}
		return c;
	}
}
