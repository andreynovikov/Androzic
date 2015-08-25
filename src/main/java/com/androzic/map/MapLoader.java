/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.map;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Hashtable;

import android.os.Build;
import android.util.Log;

import com.androzic.map.forge.ForgeMap;
import com.androzic.map.mbtiles.MBTilesMap;
import com.androzic.map.ozf.Grid;
import com.androzic.map.ozf.OzfMap;
import com.androzic.map.rmaps.SQLiteMap;
import com.androzic.util.CSV;
import com.androzic.util.OziExplorerFiles;
import com.jhlabs.Point2D;
import com.jhlabs.map.Datum;
import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.GeodeticPosition;
import com.jhlabs.map.proj.ConicProjection;
import com.jhlabs.map.proj.ProjectionException;
import com.jhlabs.map.proj.ProjectionFactory;
import com.jhlabs.map.proj.UniversalTransverseMercatorProjection;

public class MapLoader
{
	private static Hashtable<String,String> projections;
	private static Ellipsoid[] ellipsoids = new Ellipsoid[]
	                                        {
												Ellipsoid.AIRY,
												Ellipsoid.AIRY_MOD,
												Ellipsoid.AUSTRALIAN,
												Ellipsoid.BESSEL,
												Ellipsoid.CLARKE_1866,
												Ellipsoid.CLARKE_1880,
												Ellipsoid.EVEREST_30,
												Ellipsoid.EVEREST_48,
												Ellipsoid.FISCHER_MOD,
												Ellipsoid.EVEREST_PA,
												Ellipsoid.INDONESIAN,
												Ellipsoid.GRS_1980,
												Ellipsoid.HELMET,
												Ellipsoid.HOUGH,
												Ellipsoid.INTERNATIONAL_1924,
												Ellipsoid.KRASOVSKY,
												Ellipsoid.SA_1969,
												Ellipsoid.EVEREST_69,
												Ellipsoid.EVEREST_SS,
												Ellipsoid.WGS_1972,
												Ellipsoid.WGS_1984,
												Ellipsoid.BESSEL_NAM,
												Ellipsoid.EVEREST_56,
												Ellipsoid.CLARKE_1880_PAL,
												Ellipsoid.CLARKE_1880_IGN,
												Ellipsoid.HAYFORD,
												Ellipsoid.CLARKE_1858,
												Ellipsoid.BESSEL_NOR,
												Ellipsoid.PLESSIS,
												Ellipsoid.HAYFORD
	                                        };

	public static BaseMap load(File file, String charset) throws IOException
	{
		if (projections == null)
		{
            initialize();
        }

		// MapsForge magic length - 20
		// SQLite magic length - 13
		byte[] buffer = new byte[20];
		InputStream is = new FileInputStream(file);
		if (is.read(buffer) != buffer.length) {
			throw new IOException("Unknown map file format");
		}
		is.close();
		if (Arrays.equals(ForgeMap.MAGIC, buffer))
			return new ForgeMap(file.getCanonicalPath());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
		{
			byte[] buffer13 = Arrays.copyOf(buffer, SQLiteMap.MAGIC.length);
			if (Arrays.equals(SQLiteMap.MAGIC, buffer13))
			{
				if (file.getName().endsWith(".mbtiles"))
					return new MBTilesMap(file.getCanonicalPath());
				else
					return new SQLiteMap(file.getCanonicalPath());
			}
		}

	    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
	    
	    OzfMap map = new OzfMap(file.getCanonicalPath());
	    try
	    {
		    String[] fields;
		    String line = reader.readLine();
		    if (line == null || ! line.startsWith("OziExplorer Map Data File"))
		    {
		    	reader.close();
				throw new IllegalArgumentException("Bad map header: " + map.path);
		    }
		    line = reader.readLine();
		    map.title = line;
		    line = reader.readLine();
		    map.imagePath = line;
		    reader.readLine(); // Map Code
		    line = reader.readLine();
		    fields = CSV.parseLine(line);
		    map.datum = fields[0];
		    line = reader.readLine();
		    fields = CSV.parseLine(line);
		    if (fields[0].equals("MSF"))
		    	map.scaleFactor = 1 / Double.parseDouble(fields[1]);
		    //noinspection UnusedAssignment
		    line = reader.readLine(); // Reserved
		    while ((line = reader.readLine()) != null)
			{
				fields = CSV.parseLine(line);
				if (fields.length == 0)
					continue;
				
				if (fields[0].startsWith("Point") && fields.length == 17)
				{
					MapPoint point = parsePoint(map, fields);
					if (point != null)
						map.addCalibrationPoint(point);
				}
				if ("LLGRID".equals(fields[0]) && fields.length == 14)
				{
					parseLLGrid(map, fields);
				}
				if ("GRGRID".equals(fields[0]) && fields.length == 15)
				{
					parseOtherGrid(map, fields);
				}
				if ("IWH".equals(fields[0]))
				{
					map.width = (int) (Integer.parseInt(fields[2]) * map.scaleFactor);
					map.height = (int) (Integer.parseInt(fields[3]) * map.scaleFactor);
				}
				if ("MMPNUM".equals(fields[0]))
				{
					map.setCornersAmount(Integer.parseInt(fields[1]));
				}
				if ("MMPXY".equals(fields[0]))
				{
					try
					{
						int i = Integer.parseInt(fields[1]) - 1;
						int x = (int) (Integer.parseInt(fields[2]) * map.scaleFactor);
						int y = (int) (Integer.parseInt(fields[3]) * map.scaleFactor);
						map.cornerMarkers[i].x = x;
						map.cornerMarkers[i].y = y;
					}
					catch (Exception e)
					{
				    	reader.close();
						e.printStackTrace();
						throw new IllegalArgumentException("Bad XY corner marker: " + map.path);
					}
				}
				if ("MMPLL".equals(fields[0]))
				{
					try
					{
						int i = Integer.parseInt(fields[1]) - 1;
						double lon = Double.parseDouble(fields[2]);
						double lat = Double.parseDouble(fields[3]);
						map.cornerMarkers[i].lat = lat;
						map.cornerMarkers[i].lon = lon;
					}
					catch (Exception e)
					{
				    	reader.close();
						e.printStackTrace();
						throw new IllegalArgumentException("Bad LL corner marker: " + map.path);
					}
				}
				if ("MM1B".equals(fields[0]))
				{
					map.mpp = Double.parseDouble(fields[1]);
				}
				if ("Map Projection".equals(fields[0]))
				{
					map.prjName = fields[1];
					String prj4spec = projections.get(map.prjName);
					if (prj4spec == null)
					{
				    	reader.close();
						throw new ProjectionException("Unimplemented projection: "+map.prjName);
					}
					map.projection = ProjectionFactory.fromPROJ4Specification(prj4spec.split(" "));
				}
				if ("Projection Setup".equals(fields[0]))
				{
					parseProjectionParams(map, fields);
				}
			}
			Datum datum = Datum.get(map.datum);
			if (datum == null)
			{
		    	reader.close();
				throw new IllegalArgumentException("Datum "+map.datum+" not found");
			}
			
			if (! Datum.WGS_1984.equals(datum))
				map.projection.setEllipsoid(datum.getEllipsoid());
		    if ("".equals(map.projection.getEllipsoid().shortName))
		    	map.projection.setEllipsoid(Ellipsoid.WGS_1984);
		    map.projection.initialize();
		    fixCalibration(map);
			fixCoords(map, datum);
		    map.bind();
			fixCornerMarkers(map);
		    map.debug();
			reader.close();
	    }
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
			map.loadError = e;
		}
		catch (ProjectionException e)
		{
			e.printStackTrace();
			map.loadError = e;
		}
		catch (IndexOutOfBoundsException e)
		{
			e.printStackTrace();
			map.loadError = e;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			map.loadError = e;
		}

		return map;
	}

	private static void fixCoords(OzfMap map, Datum datum)
	{
		Log.d("OZI", "map datum: " + datum);
		
		if (Datum.WGS_1984.equals(datum))
			return;
		
		for (MapPoint mp : map.calibrationPoints)
		{
			GeodeticPosition from = new GeodeticPosition(mp.lat, mp.lon);
			GeodeticPosition to = datum.transformToWGS84(from);
			// TODO MapPoint should extend GeodeticPosition
			mp.lat = to.lat;
			mp.lon = to.lon;
		}

		if (map.cornerMarkers != null)
		{
			for (MapPoint mp : map.cornerMarkers)
			{
				GeodeticPosition from = new GeodeticPosition(mp.lat, mp.lon);
				GeodeticPosition to = datum.transformToWGS84(from);
				// TODO MapPoint should extend GeodeticPosition
				mp.lat = to.lat;
				mp.lon = to.lon;
			}
		}
		map.origDatum = map.datum;
		map.datum = "WGS84";
		Log.d("OZI", "new datum: " + map.datum);
	}

	private static void fixCalibration(OzfMap map)
	{
		for (MapPoint mp : map.calibrationPoints)
		{
		    if (map.projection instanceof UniversalTransverseMercatorProjection)
		    {
		    	if (mp.zone != 0)
		    	{
		    		((UniversalTransverseMercatorProjection) map.projection).setUTMZone(mp.zone);
		    	}
		    	else
		    	{
	    			map.projection.setProjectionLongitudeDegrees(mp.lon);
		    		((UniversalTransverseMercatorProjection) map.projection).clearUTMZone();
		    	}
	    		((UniversalTransverseMercatorProjection) map.projection).setIsSouth(mp.hemisphere == 1);
	    		map.projection.initialize();
		    }
			if (mp.n != 0 && mp.e != 0)
			{
				//Log.e("OZI", "fix: "+map.projection.getPROJ4Description());
		        Point2D.Double src = new Point2D.Double(mp.e, mp.n);
		        Point2D.Double dst = new Point2D.Double();
				map.projection.inverseTransform(src, dst);
				mp.lat = dst.y;
				mp.lon = dst.x;
				//Log.e("OZI", "fix: "+mp.n+" "+mp.e+" | "+mp.lat+" "+mp.lon);
			}		
		}
			
		if (map.calibrationPoints.size() == 2)
		{
			MapPoint mp1 = map.calibrationPoints.get(0);
			MapPoint mp2 = map.calibrationPoints.get(1);			
			MapPoint mp3 = new MapPoint(mp1);
			MapPoint mp4 = new MapPoint(mp2);

			mp3.x = mp2.x;
			mp4.x = mp1.x;

	        Point2D.Double src;

	        src = new Point2D.Double(mp1.lon, mp1.lat);
	        Point2D.Double dst1 = new Point2D.Double();
			map.projection.transform(src.x, src.y, dst1);
	        src = new Point2D.Double(mp2.lon, mp2.lat);
	        Point2D.Double dst2 = new Point2D.Double();
			map.projection.transform(src.x, src.y, dst2);

			mp3.n = dst1.y;
			mp3.e = dst2.x;
			mp4.n = dst2.y;
			mp4.e = dst1.x;

	        Point2D.Double dst = new Point2D.Double();

	        src = new Point2D.Double(mp3.e, mp3.n);
			map.projection.inverseTransform(src, dst);
			mp3.lat = dst.y;
			mp3.lon = dst.x;

	        src = new Point2D.Double(mp4.e, mp4.n);
			map.projection.inverseTransform(src, dst);
			mp4.lat = dst.y;
			mp4.lon = dst.x;

			map.calibrationPoints.add(mp3);
			map.calibrationPoints.add(mp4);
		}
	}
	
	private static void fixCornerMarkers(OzfMap map)
	{
		if (map.cornerMarkers != null)
		{
			// If any of the corner markers is not valid do not use them all
			boolean haveBadCorner = false;
			for (MapPoint mp : map.cornerMarkers)
			{
				if (mp.lat < -90 || mp.lat > 90 || mp.lon < -180 || mp.lon > 180)
					haveBadCorner = true;
			}
			if (haveBadCorner)
				map.cornerMarkers = null;
		}
		if (map.cornerMarkers == null)
		{
			map.setCornersAmount(4);
			map.cornerMarkers[0].x = 0;
			map.cornerMarkers[0].y = 0;
			map.cornerMarkers[1].x = 0;
			map.cornerMarkers[1].y = map.height - 1;
			map.cornerMarkers[2].x = map.width - 1;
			map.cornerMarkers[2].y = map.height - 1;
			map.cornerMarkers[3].x = map.width - 1;
			map.cornerMarkers[3].y = 0;
			double[] ll = new double[2];
			for (int i = 0; i < 4; i++)
			{
				map.getLatLonByXY(map.cornerMarkers[i].x, map.cornerMarkers[i].y, ll);
				map.cornerMarkers[i].lat = ll[0];
				map.cornerMarkers[i].lon = ll[1];
			}
		}
	}
	
	private static void parseProjectionParams(OzfMap map, String[] fields)
	{
		try
		{
			double origin_latitude = Double.parseDouble(fields[1]);
			map.projection.setProjectionLatitudeDegrees(origin_latitude);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double central_meridian = Double.parseDouble(fields[2]);
			map.projection.setProjectionLongitudeDegrees(central_meridian);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double scale_factor = Double.parseDouble(fields[3]);
			map.projection.setScaleFactor(scale_factor);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double false_easting = Double.parseDouble(fields[4]);
			map.projection.setFalseEasting(false_easting);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double false_northing = Double.parseDouble(fields[5]);
			map.projection.setFalseNorthing(false_northing);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double latitude_1 = Double.parseDouble(fields[6]);
	        if (map.projection instanceof ConicProjection)
	        {
	            ((ConicProjection) map.projection).setProjectionLatitude1Degrees(latitude_1);
	        }
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			double latitude_2 = Double.parseDouble(fields[7]);
	        if (map.projection instanceof ConicProjection)
	        {
	            ((ConicProjection) map.projection).setProjectionLatitude2Degrees(latitude_2);
	        }
		}
		catch (NumberFormatException e)
		{
		}
	}

	private static MapPoint parsePoint(OzfMap map, String[] fields)
	{
		MapPoint point = new MapPoint();
		//int n = Integer.parseInt(fields[0].substring("Point".length()));
		if ("ex".equals(fields[4]))
			return null;
		try
		{
			point.x = (int) (Integer.parseInt(fields[2]) * map.scaleFactor);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		try
		{
			point.y = (int) (Integer.parseInt(fields[3]) * map.scaleFactor);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		try
		{
			int dlat = Integer.parseInt(fields[6]);
			double mlat = Double.parseDouble(fields[7]);
			String hlat = fields[8];
			point.lat = dms_to_deg(dlat, mlat, 0);
			if ("S".equals(hlat))
				point.lat = -point.lat;
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			int dlon = Integer.parseInt(fields[9]);
			double mlon = Double.parseDouble(fields[10]);
			String hlon = fields[11];
			point.lon = dms_to_deg(dlon, mlon, 0);
			if ("W".equals(hlon))
				point.lon = -point.lon;
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			point.zone = Integer.parseInt(fields[13]);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			point.e = Double.parseDouble(fields[14]);
		}
		catch (NumberFormatException e)
		{
		}
		try
		{
			point.n = Double.parseDouble(fields[15]);
		}
		catch (NumberFormatException e)
		{
		}
		point.hemisphere = "S".equals(fields[16]) ? 1 : 0;
		return point;
	}

	private static void parseLLGrid(OzfMap map, String[] fields)
	{
		Grid grid = new Grid();
		grid.enabled = "Yes".equals(fields[1]);
		try
		{
			String[] sf = fields[2].split("\\s+");
			grid.spacing = Double.parseDouble(sf[0]);
			if ("Min".equals(sf[1]))
				grid.spacing /= 60;
			if ("Sec".equals(sf[1]))
				grid.spacing /= 3660;
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			return;			
		}
		grid.autoscale = "Yes".equals(fields[3]);
		try
		{
			grid.color1 = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[4]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.color2 = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[5]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.color3 = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[6]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			if (! "No Labels".equals(fields[7]))
			{
				String[] sf = fields[7].split("\\s+");
				grid.labelSpacing = Double.parseDouble(sf[0]);
				if ("Min".equals(sf[1]))
					grid.labelSpacing /= 60;
				if ("Sec".equals(sf[1]))
					grid.labelSpacing /= 3660;
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			return;			
		}
		try
		{
			grid.labelForeground = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[8]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.labelBackground = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[9]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.labelSize = Integer.parseInt(fields[10]);
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			int i = Integer.parseInt(fields[11]);
			grid.labelShowEverywhere = i == 1;
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		map.llGrid = grid;
	}

	private static void parseOtherGrid(OzfMap map, String[] fields)
	{
		Grid grid = new Grid();
		grid.enabled = "Yes".equals(fields[1]);
		try
		{
			String[] sf = fields[2].split("\\s+");
			grid.spacing = Double.parseDouble(sf[0]);
			if ("Km".equals(sf[1]))
				grid.spacing *= 1000;
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			return;			
		}
		grid.autoscale = "Yes".equals(fields[3]);
		try
		{
			grid.color1 = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[4]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.color2 = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[5]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			if (! "No Labels".equals(fields[6]))
			{
				String[] sf = fields[6].split("\\s+");
				grid.labelSpacing = Double.parseDouble(sf[0]);
				if ("Km".equals(sf[1]))
					grid.labelSpacing *= 1000;
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			return;			
		}
		try
		{
			grid.labelForeground = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[7]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.labelBackground = OziExplorerFiles.bgr2rgb(Integer.parseInt(fields[8]));
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			grid.labelSize = Integer.parseInt(fields[9]);
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		try
		{
			int i = Integer.parseInt(fields[10]);
			grid.labelShowEverywhere = i == 1;
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		map.grGrid = grid;
	}

	static double dms_to_deg(double deg, double min, double sec)
	{
		return deg + min / 60 + sec / 3600;
	}
	
	public static Ellipsoid getEllipsoid(int index)
	{
		if (index < 0 || index >= ellipsoids.length)
			return null;
		return ellipsoids[index];
	}

    private static void initialize()
    {
        projections = new Hashtable<String,String>();

		projections.put("Latitude/Longitude", "+proj=longlat");
        projections.put("Mercator", "+proj=merc");
		projections.put("Transverse Mercator", "+proj=tmerc");
		projections.put("(UTM) Universal Transverse Mercator", "+proj=utm");
		projections.put("(BNG) British National Grid", "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.999601 +x_0=400000 +y_0=-100000");
		projections.put("(IG) Irish Grid", "+proj=tmerc +lat_0=53.5 +lon_0=-8 +k=1.000035 +x_0=200000 +y_0=250000 +a=6377340.189 +b=6356034.447938534");
		projections.put("(NZG) New Zealand Grid", "+proj=nzmg +lat_0=-41 +lon_0=173 +x_0=2510000 +y_0=6023150");
		projections.put("(SG) Swedish Grid", "+proj=tmerc +lat_0=0 +lon_0=15.80827777777778 +k=1 +x_0=1500000 +y_0=0");
//		projections.put("(SG) Swedish Grid", "+proj=tmerc +lat_0=0 +lon_0=15.806284529444449 +k=1.00000561024 +x_0=1500064.274 +y_0=-667.711");
//		projections.put("(SUI) Swiss Grid", "+proj=omerc +ellps=bessel +lat_0=46.95240555555556 +lon_0=7.439583333333333 +x_0=600000 +y_0=200000");
//		projections.put("(SUI) Swiss Grid", "+proj=omerc +ellps=bessel +lat_0=46.951083 +lon_0=7.438639 +x_0=600000 +y_0=200000");
//		projections.put("(SUI) Swiss Grid", "+proj=somerc +ellps=bessel +lat_0=46.95240555555556 +lon_0=7.439583333333333 +x_0=600000 +y_0=200000");


		
//		projections.put("(SUI) Swiss Grid", "+proj=somerc +ellps=bessel +lat_0=46.95240555555556 +lon_0=7.439583333333333 +x_0=600000 +y_0=200000");
		projections.put("(SUI) Swiss Grid", "+proj=somerc +ellps=bessel +x_0=600000 +y_0=200000");
		
		
		projections.put("(I) France Zone I", "+proj=lcc +lat_1=48.598523 +lat_2=50.395912 +lat_0=49.5 +lon_0=2.337229 +x_0=600000 +y_0=200000 +a=6378249.2 +b=6356515");
		projections.put("(II) France Zone II", "+proj=lcc +lat_1=45.898919 +lat_2=47.696014 +lat_0=46.8 +lon_0=2.337229 +x_0=600000 +y_0=2200000 +a=6378249.2 +b=6356515");
		projections.put("(III) France Zone III", "+proj=lcc +lat_1=43.199291 +lat_2=44.996094 +lat_0=44.1 +lon_0=2.337229 +x_0=600000 +y_0=200000 +a=6378249.2 +b=6356515");
		projections.put("(IV) France Zone IV", "+proj=lcc +lat_1=41.560388 +lat_2=42.767663 +lat_0=42.165 +lon_0=2.337229 +x_0=234.358 +y_0=4185861.369 +a=6378249.2 +b=6356515");
        projections.put("Lambert Conformal Conic", "+proj=lcc");
		projections.put("(A)Lambert Azimuthual Equal Area", "+proj=laea");
		projections.put("(EQC) Equidistant Conic", "+proj=eqdc");
		projections.put("Sinusoidal", "+proj=sinu");
		projections.put("Polyconic (American)", "+proj=poly");
		projections.put("Albers Equal Area", "+proj=aea");
		projections.put("Van Der Grinten", "+proj=vandg");
		projections.put("Vertical Near-Sided Perspective", "+proj=nsper");
		projections.put("(WIV) Wagner IV", "+proj=wag4");
		projections.put("Bonne", "+proj=bonne");
		projections.put("(MT0) Montana State Plane Zone 2500", "+proj=lcc +lat_1=45 +lat_2=49 +lat_0=44.25 +lon_0=-109.5 +x_0=600000 +y_0=0");
		projections.put("(ITA1) Italy Grid Zone 1", "+proj=tmerc +lat_0=0 +lon_0=-3.45233333333333 +k=0.999600 +x_0=1500000 +y_0=0");
		projections.put("(ITA2) Italy Grid Zone 2", "+proj=tmerc +lat_0=0 +lon_0=2.54766666666666 +k=0.999600 +x_0=2520000 +y_0=0");
		projections.put("(VICMAP-TM) Victoria Aust.(pseudo AMG)", "+proj=tmerc +lat_0=145 +x_0=500000 +y_0=10000000");
		projections.put("(VICGRID) Victoria Australia", "+proj=lcc +lat_1=-36 +lat_2=-38 +lat_0=-37 +lon_0=145 +x_0=2500000 +y_0=4500000");
		projections.put("(VG94) VICGRID94 Victoria Australia", "+proj=lcc +lat_1=-36 +lat_2=-38 +lat_0=-37 +lon_0=145 +x_0=2500000 +y_0=2500000");
    }
}
