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

package com.androzic.map;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import com.jhlabs.Point2D;
import com.jhlabs.map.proj.Projection;

public class Map implements Serializable
{
	private static final long serialVersionUID = 3L;
	
	public int id;
	public String title;
	public String mappath;
	public String imagePath;
	public String datum;
	public int width;
	public int height;
	public double mpp;
	public double scaleFactor;
	public String prjName;
	public Grid llGrid;
	public Grid grGrid;
	protected Projection projection;
	protected MapPoint[] cornerMarkers;
	protected ArrayList<MapPoint> calibrationPoints = new ArrayList<MapPoint>();
	protected double scale;
	private LinearBinding binding = new LinearBinding();
	protected int pixels;
	transient public Throwable loadError;
	transient private OzfReader ozf;
	transient protected TileRAMCache cache;
	transient private Bounds bounds;
	
	public Map(String filepath)
	{
		mappath = filepath;
		id = mappath.hashCode();
		scale = 1.0;
		scaleFactor = 1.0;
		loadError = null;
		cache = null;
	}
	
	public void activate(View view, int pixels) throws IOException, OutOfMemoryError
	{
		this.pixels = pixels;
		Log.d("OZI", "Image file specified: " + imagePath);
		File image = new File(imagePath);
		if (! image.exists())
		{
			imagePath = imagePath.replace("\\", "/");
			image = new File(imagePath);
			File map = new File(mappath);
			image = new File(map.getParentFile(), image.getName());
			if (! image.exists())
			{
				throw new FileNotFoundException("Image file not found: " + imagePath);
			}
		}
		Log.d("OZI", "Image file found: " + image.getCanonicalPath());
		ozf = new OzfReader(image);
		setZoom(scale);
	}
	
	public void deactivate()
	{
		ozf.close();
		ozf = null;
		cache.destroy();
		cache = null;
	}
	
	public void addCalibrationPoint(MapPoint point)
	{
		calibrationPoints.add(point);
	}

	public void setCornersAmount(int num)
	{
		cornerMarkers = new MapPoint[num];
		for (int i = 0; i < num; i++)
		{
			cornerMarkers[i] = new MapPoint();
		}
	}

	public Bounds getBounds()
	{
		if (bounds == null)
		{
			bounds = new Bounds();
			for (MapPoint corner : cornerMarkers)
			{
				if (corner.lat < bounds.minLat) bounds.minLat = corner.lat;
				if (corner.lat > bounds.maxLat) bounds.maxLat = corner.lat;
				// FIXME think how to wrap 180 parallel
				if (corner.lon < bounds.minLon) bounds.minLon = corner.lon;
				if (corner.lon > bounds.maxLon) bounds.maxLon = corner.lon;
			}
		}
		return bounds;
	}
	
	public boolean getXYByLatLon(double lat, double lon, int[] xy)
	{
		double nn, ee;
		
        Point2D.Double src = new Point2D.Double(lon, lat);
        Point2D.Double dst = new Point2D.Double();
		projection.transform(src.x, src.y, dst);
		ee = dst.x;
		nn = dst.y;
		xy[0] = (int) Math.round(binding.Kx[0]*nn + binding.Kx[1]*ee + binding.Kx[2]);
		xy[1] = (int) Math.round(binding.Ky[0]*nn + binding.Ky[1]*ee + binding.Ky[2]);

		return (xy[0] >= 0 && xy[0] < width * scale && xy[1] >= 0 && xy[1] < height * scale);
	}

	// never used
	public boolean getXYByEN(int e, int n, int[] xy)
	{
		xy[0] = (int) Math.round(binding.Kx[0]*n + binding.Kx[1]*e + binding.Kx[2]);
		xy[1] = (int) Math.round(binding.Ky[0]*n + binding.Ky[1]*e + binding.Ky[2]);

		return (xy[0] >= 0 && xy[0] < width * scale && xy[1] >= 0 && xy[1] < height * scale);
	}

	// never used
	public boolean getENByXY(int x, int y, int[] en)
	{
		en[1] = (int) (binding.Klat[0]*x + binding.Klat[1]*y + binding.Klat[2]);
		en[0] = (int) (binding.Klon[0]*x + binding.Klon[1]*y + binding.Klon[2]);

		return (x >= 0 && x < width * scale && y >= 0 || y < height * scale);
	}

	// never used
	public void getENByLatLon(double lat, double lon, int[] en)
	{
        Point2D.Double src = new Point2D.Double(lon, lat);
        Point2D.Double dst = new Point2D.Double();
		projection.transform(src.x, src.y, dst);
		en[0] = (int) dst.x;
		en[1] = (int) dst.y;
	}

	/**
	 * Converts pixel coordinates to geodetic coordinates
	 * @param x
	 * @param y
	 * @param ll
	 * @return
	 */
	public boolean getLatLonByXY(int x, int y, double[] ll)
	{
		double nn, ee;

		nn = binding.Klat[0]*x + binding.Klat[1]*y + binding.Klat[2];
		ee = binding.Klon[0]*x + binding.Klon[1]*y + binding.Klon[2];

        Point2D.Double src = new Point2D.Double(ee, nn);
        Point2D.Double dst = new Point2D.Double();
		projection.inverseTransform(src, dst);
		ll[0] = dst.y;
		ll[1] = dst.x;

		return (x >= 0 && x < width * scale && y >= 0 || y < height * scale);
	}
	
	/**
	 * Checks if map covers given coordinates
	 * @param lat latitude in degrees
	 * @param lon longitude in degrees
	 * @return true if coordinates are inside map
	 */
	public boolean coversLatLon(double lat, double lon)
	{
		int[] xy = new int[2];
		boolean inside = getXYByLatLon(lat, lon, xy);
		
		// check corners
		if (inside)
		{
			// rescale to original size
			xy[0] = (int) (xy[0] / scale);
			xy[1] = (int) (xy[1] / scale);
			
			//  Note that division by zero is avoided because the division is protected
			//  by the "if" clause which surrounds it.

			int j = cornerMarkers.length - 1;
			int odd = 0;

			for (int i=0; i < cornerMarkers.length; i++)
			{
				if (cornerMarkers[i].y < xy[1] && cornerMarkers[j].y >= xy[1] || cornerMarkers[j].y < xy[1] && cornerMarkers[i].y >= xy[1])
				{
					if (cornerMarkers[i].x + (xy[1] - cornerMarkers[i].y) / (cornerMarkers[j].y - cornerMarkers[i].y) * (cornerMarkers[j].x - cornerMarkers[i].x) < xy[0])
					{
						odd++;
					}
				}
				j=i;
			}
			
			inside = odd % 2 == 1;
		}

		return inside;
	}
	
	public double getZoom()
	{
		return ozf.getZoom();
	}

	public double getNextZoom()
	{
		return ozf.getNextZoom();
	}

	public double getPrevZoom()
	{
		return ozf.getPrevZoom();
	}
	
	public void zoomBy(double factor)
	{
		setZoom(scale * factor);
	}
	
	public void setZoom(double zoom)
	{
		scale = ozf.setZoom(zoom);
		if (cache != null)
			cache.destroy();
		int cacheSize = (int) (pixels / (ozf.tile_dx() * ozf.tile_dy()) * 3);
		Log.e("OZI", "Cache size: " + cacheSize);
		cache = new TileRAMCache(cacheSize);
		ozf.setCache(cache);
		bind();
	}

	public int getScaledWidth()
	{
		return (int) (width * scale);
	}

	public int getScaledHeight()
	{
		return (int) (height * scale);
	}

	public void drawMap(double[] loc, int[] lookAhead, int width, int height, Canvas c) throws OutOfMemoryError
	{
		if (ozf == null)
			return;
		int[] xy = new int[2];
		getXYByLatLon(loc[0], loc[1], xy);
		xy[0] -= lookAhead[0];
		xy[1] -= lookAhead[1];
		try
		{
			ozf.drawMap(xy, width, height, c);
		}
		catch (OutOfMemoryError err)
		{
			cache.clear();
			throw err;
		}
	}

	public void bind()
	{
		MapPoint[] points = new MapPoint[calibrationPoints.size()];

		int i = 0;
		for (MapPoint mp : calibrationPoints)
		{
			points[i] = new MapPoint();
			points[i].lat = mp.lat;
			points[i].lon = mp.lon;
			points[i].x = (int) (mp.x * scale);
			points[i].y = (int) (mp.y * scale);
	        Point2D.Double src = new Point2D.Double(points[i].lon, points[i].lat);
	        Point2D.Double dst = new Point2D.Double();
			projection.transform(src.x, src.y, dst);
			points[i].n = dst.y;
			points[i].e = dst.x;
//			Log.e("OZI","point transform: "+points[i].lat+" "+points[i].lon+" -> "+points[i].n+" "+points[i].e);
			src.x = dst.x;
			src.y = dst.y;
			projection.inverseTransform(src, dst);
//			Log.e("OZI","point reverse transform: "+src.y+" "+src.x+" -> "+dst.y+" "+dst.x);
			i++;
		}

		getKx(points);
		getKy(points);
		getKLat(points);
		getKLon(points);
	}

	private void getKx(MapPoint[] points)
	{
		double[][] a = new double[3][3];
		double[] b = new double[3];
		double[][] p = new double[3][points.length];

		int i = 0;
		for (MapPoint mp : points)
		{
			p[0][i] = mp.n;
			p[1][i] = mp.e;
			p[2][i] = mp.x;
			i++;
		}
		
		init_3x3(a, b, p, points.length);
		gauss(a, b, binding.Kx, 3);
		//Log.e("OZI", "Kx: "+binding.Kx[0]+","+binding.Kx[1]+","+binding.Kx[2]);
	}

	private void getKy(MapPoint[] points)
	{
		double[][] a = new double[3][3];
		double[] b = new double[3];
		double[][] p = new double[3][points.length];

		int i = 0;
		for (MapPoint mp : points)
		{
			p[0][i] = mp.n;
			p[1][i] = mp.e;
			p[2][i] = mp.y;
			i++;
		}

		init_3x3(a, b, p, points.length);
		gauss(a, b, binding.Ky, 3);
		//Log.e("OZI", "Ky: "+binding.Ky[0]+","+binding.Ky[1]+","+binding.Ky[2]);
	}

	private void getKLat(MapPoint[] points)
	{
		double[][] a = new double[3][3];
		double[] b = new double[3];
		double[][] p = new double[3][points.length];

		int i = 0;
		for (MapPoint mp : points)
		{
			p[0][i] = mp.x;
			p[1][i] = mp.y;
			p[2][i] = mp.n;
			i++;
		}
		
		init_3x3(a, b, p, points.length);
		gauss(a, b, binding.Klat, 3);
		//Log.e("OZI", "Klat: "+binding.Klat[0]+","+binding.Klat[1]+","+binding.Klat[2]);
	}

	private void getKLon(MapPoint[] points)
	{
		double[][] a = new double[3][3];
		double[] b = new double[3];
		double[][] p = new double[3][points.length];

		int i = 0;
		for (MapPoint mp : points)
		{
			p[0][i] = mp.x;
			p[1][i] = mp.y;
			p[2][i] = mp.e;
			i++;
		}

		init_3x3(a, b, p, points.length);
		gauss(a, b, binding.Klon, 3);
		//Log.e("OZI", "Klon: "+binding.Klon[0]+","+binding.Klon[1]+","+binding.Klon[2]);
	}

	/**
	 *  Solves linear equation.  Finds vector x such that ax = b.
	 *
	 *	@param a nXn matrix
	 *	@param b vector size n
	 *	@param x vector size n
	 *	@param n number of variables (size of vectors) (must be > 1)
	 *
	 *	This function will alter a and b, and put the solution in x.
	 *	@return true if the solution was found, false otherwise.
	 */
	private boolean gauss(double[][] a, double[] b, double[] x, int n)
	{
		int i,j,k;
		int ip = 0, kk, jj;
		double temp;
		double pivot;
		double q;

		/*
		 *	transform matrix to echelon form.
		 */
		for (i = 0; i < n-1; i++)
		{
			/*
			 *	Find the pivot.
			 */
			pivot = 0.0;
		    for (j = i; j < n; j++)
		    {
		    	temp = Math.abs(a[j][i]);
		    	if (temp > pivot)
		    	{
		    		pivot = temp;
		    		ip = j;
		    	}
		    }

		    if (pivot < 1.E-14)
		    {
		    	/*
		    	 *   Error - singular matrix.
		    	 */
		    	return false;
		    }

		    /*
		     *	Move the pivot row to the ith position
		     */
		    if (ip != i)
		    {
		    	double[] temp_p = a[i];
		    	a[i] = a[ip];
		    	a[ip] = temp_p;
		    	temp = b[i];
		    	b[i] = b[ip];
		    	b[ip] = temp;
		    }

		    /*
		     *	Zero entries below the diagonal.
		     */
		    for (k = i + 1; k < n; k++)
		    {
		    	q = -a[k][i] / a[i][i];

		    	a[k][i] = 0.0;

		    	for (j = i + 1; j < n; j++)
		    		a[k][j] = q * a[i][j] + a[k][j];
		    	b[k] = q * b[i] + b[k];
		    }

		}

		if (Math.abs(a[n-1][n-1]) < 1.E-14)
		{
			return false;
		}

		/*
		 *	Backsolve to obtain solution vector x.
		 */
		kk = n - 1;
		x[kk] = b[kk] / a[kk][kk];
		for (k = 0; k < n - 1; k++)
		{
			kk = n - k - 2;
			q = 0.0;

			for (j = 0; j <= k; j++)
			{
				jj = n - j - 1;
				q = q + a[kk][jj] * x[jj];
			}
			x[kk] = (b[kk] - q) / a[kk][kk];
		}

		return true;
	}

	private void init_3x3(double[][] a, double[] b, double[][] p, int size)
	{
		for (int i = 0; i < 3; i++)
		{
			b[i] = 0;
			
			for (int j = 0; j < 3; j++)
				a[i][j] = 0;
		}

		for(int i = 0; i < size; i++)
		{
			a[0][0] += p[0][i] * p[0][i];
			a[0][1] += p[0][i] * p[1][i];
			a[0][2] += p[0][i];
			a[1][1] += p[1][i] * p[1][i];
			a[1][2] += p[1][i];
			b[0] += p[2][i] * p[0][i];
			b[1] += p[2][i] * p[1][i];
			b[2] += p[2][i];
		}

		a[1][0] = a[0][1];
		a[2][0] = a[0][2];
		a[2][1] = a[1][2];
		a[2][2] = size;
	}
	
	private class LinearBinding implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		double[] Kx = new double[3];
		double[] Ky = new double[3];
		double[] Klat = new double[3];
		double[] Klon = new double[3];
	}

	public class Grid implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		public boolean enabled;
		public double spacing;
		public boolean autoscale;
		public int color1;
		public int color2;
		public int color3;
		public double labelSpacing;
		public int labelForeground;
		public int labelBackground;
		public int labelSize;
		public boolean labelShowEverywhere;
		public int maxMPP = 0;
	}
	
	public class Bounds
	{
    	public double minLat = Double.MAX_VALUE;
    	public double maxLat = Double.MIN_VALUE;
    	public double minLon = Double.MAX_VALUE;
    	public double maxLon = Double.MIN_VALUE;
	}
	
	public void debug()
	{
		List<String> info = info();
		for (String line : info)
		{
			Log.d("OZI", line);
		}
	}

	public List<String> info()
	{
		ArrayList<String> info = new ArrayList<String>();
		
		info.add("title: " + title);
		if (projection != null)
		{
			info.add("projection: " + prjName + " (" + projection.getEPSGCode() + ")");
			info.add("\t" + projection.getPROJ4Description());
			info.add("ellipsoid: " + projection.getEllipsoid().toString());
		}
		info.add("datum: " + datum);
		info.add("mpp: " + mpp);
		info.add("image width: " + width);
		info.add("image height: " + height);
		info.add("image file: " + imagePath);
		info.add("scale factor: " + 1 / scaleFactor);
		info.add("calibration points:");
		
		int i = 1;
		for (MapPoint mp : calibrationPoints)
		{
			info.add(String.format("\t%02d: x: %d y: %d lat: %f lon: %f", i, mp.x, mp.y, mp.lat, mp.lon));
			i++;
		}
		double[] ll = new double[2];
		getLatLonByXY(width/2, height/2, ll);
		info.add("map center (calibration) test: "+ll[0] + " " + ll[1]);
		
		info.add("corners:");

		i = 1;
		for (MapPoint mp : cornerMarkers)
		{
			info.add(String.format("\t%02d: x: %d y: %d lat: %f lon: %f", i, mp.x, mp.y, mp.lat, mp.lon));
			i++;
		}
		
		if (llGrid != null)
		{
			info.add("lat/lon grid:");
			info.add("  enabled: " + llGrid.enabled);
			info.add("  spacing: " + llGrid.spacing);
			info.add("  autoscale: " + llGrid.autoscale);
			info.add("  deg. color: " + llGrid.color1);
			info.add("  min. color: " + llGrid.color2);
			info.add("  sec. color: " + llGrid.color3);
			info.add("  label spacing: " + llGrid.labelSpacing);
			info.add("  label foreground: " + llGrid.labelForeground);
			info.add("  label background: " + llGrid.labelBackground);
			info.add("  label size: " + llGrid.labelSize);
			info.add("  label everywhere: " + llGrid.labelShowEverywhere);
		}

		if (grGrid != null)
		{
			info.add("other grid:");
			info.add("  enabled: " + grGrid.enabled);
			info.add("  spacing: " + grGrid.spacing);
			info.add("  autoscale: " + grGrid.autoscale);
			info.add("  km color: " + grGrid.color1);
			info.add("  meter color: " + grGrid.color2);
			info.add("  label spacing: " + grGrid.labelSpacing);
			info.add("  label foreground: " + grGrid.labelForeground);
			info.add("  label background: " + grGrid.labelBackground);
			info.add("  label size: " + grGrid.labelSize);
			info.add("  label everywhere: " + grGrid.labelShowEverywhere);
		}
		
		return info;
	}
}
