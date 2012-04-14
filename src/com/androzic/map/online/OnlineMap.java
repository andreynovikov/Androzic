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

package com.androzic.map.online;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import com.androzic.map.Map;
import com.androzic.map.Tile;
import com.androzic.map.TileRAMCache;
import com.jhlabs.map.Ellipsoid;
import com.jhlabs.map.proj.ProjectionFactory;

public class OnlineMap extends Map
{
	private static final long serialVersionUID = 1L;
	
	public static final int TILE_WIDTH = 256;
	public static final int TILE_HEIGHT = 256;
	
	private TileController tileController;
	private TileProvider tileProvider;
	private boolean isActive = false;
	private byte srcZoom;
	private byte defZoom;
	
	public OnlineMap(TileProvider provider, byte z)
	{
		super("http://...");
		datum = "WGS84";
		projection = ProjectionFactory.fromPROJ4Specification("+proj=merc".split(" "));
		projection.setEllipsoid(Ellipsoid.WGS_1984);
	    projection.initialize();
	    
	    tileProvider = provider;
	    tileController = new TileController();

	    title = String.format("%s (%d)", tileProvider.name, z);
		srcZoom = z;
		defZoom = z;
		zoom = 1.0;
	    /*
	     * The distance represented by one pixel (S) is given by
	     * S=C*cos(y)/2^(z+8) 
	     *
	     * where...
	     *
	     * C is the (equatorial) circumference of the Earth 
	     * z is the zoom level 
	     * y is the latitude of where you're interested in the scale. 
	     *
	     * Make sure your calculator is in degrees mode, unless you want to express latitude
	     * in radians for some reason. C should be expressed in whatever scale unit you're
	     * interested in (miles, meters, feet, smoots, whatever). Since the earth is actually
	     * ellipsoidal, there will be a slight error in this calculation. But it's very slight.
	     * (0.3% maximum error) 
	     */
		mpp = projection.getEllipsoid().equatorRadius * Math.PI * 2 * Math.cos(0) / Math.pow(2.0, (srcZoom + 8));
	}

	@Override
	public void activate(View view, int pixels) throws IOException, OutOfMemoryError
	{
		setZoom(savedZoom == 0 ? zoom : savedZoom);
		savedZoom = 0;
		int cacheSize = (int) (pixels / (TILE_WIDTH * TILE_HEIGHT) * 4);
		cache = new TileRAMCache(cacheSize);
		tileController.setView(view);
		tileController.setCache(cache);
		tileController.setProvider(tileProvider);
		isActive = true;
	}
	
	@Override
	public void deactivate()
	{
		isActive = false;
		tileController.interrupt();
		cache.destroy();
		if (savedZoom != 0)
			zoom = savedZoom;
		savedZoom = 0;
		cache = null;
	}
	
	public boolean activated()
	{
		return isActive;
	}
	
	public TileProvider getTileProvider()
	{
		return tileProvider;
	}

	@Override
	public boolean coversLatLon(double lat, double lon)
	{
		if (! isActive)
			mpp = projection.getEllipsoid().equatorRadius * Math.PI * 2 * Math.cos(Math.toRadians(lat)) / Math.pow(2.0, (srcZoom + 8));
		return lat < 85.051129 && lat > -85.047336;
	}

	@Override
	public boolean coversScreen(int[] map_xy, int width, int height)
	{
		// TODO Should check North and South edges
		return true;
	}
	
	@Override
	public boolean containsArea(Bounds area)
	{
		// FIXME disabled online maps in adjacent maps
		return false;
//		return area.minLat < 85.051129 && area.maxLat > -85.047336;
	}
	
	@Override
	public boolean drawMap(double[] loc, int[] lookAhead, int width, int height, boolean cropBorder, boolean drawBorder, Canvas c) throws OutOfMemoryError
	{
		int[] map_xy = new int[2];
		getXYByLatLon(loc[0], loc[1], map_xy);
		map_xy[0] -= lookAhead[0];
		map_xy[1] -= lookAhead[1];
		int osm_x = map_xy[0] / TILE_WIDTH;
		int osm_y = map_xy[1] / TILE_HEIGHT;
		
		int x = (int) Math.round(map_xy[0] - osm_x * TILE_WIDTH);
		int y = (int) Math.round(map_xy[1] - osm_y * TILE_HEIGHT);

		int tiles_per_x = Math.round(width * 1.f / TILE_WIDTH / 2 + .5f);
		int tiles_per_y = Math.round(height * 1.f / TILE_HEIGHT / 2 + .5f);

		int c_min = osm_x - tiles_per_x;
		int c_max = osm_x + tiles_per_x + 1;
		
		int r_min = osm_y - tiles_per_y;
		int r_max = osm_y + tiles_per_y + 1;
		
		boolean result = true;
		
		if (c_min < 0)
		{
			c_min = 0;
			result = false;
		}
		if (r_min < 0)
		{
			r_min = 0;
			result = false;
		}
		if (c_max > Math.pow(2.0, srcZoom))
		{
			c_max = (int) (Math.pow(2.0, srcZoom));
			result = false;
		}
		if (r_max > Math.pow(2.0, srcZoom))
		{
			r_max = (int) (Math.pow(2.0, srcZoom));
			result = false;
		}
		
		int txb = width / 2 - x - (osm_x - c_min) * TILE_WIDTH;
		int tyb = height / 2 - y - (osm_y - r_min) * TILE_HEIGHT;
		
		for (int i = r_min; i < r_max; i++)
		{
			for (int j = c_min; j < c_max; j++)
			{
				int tx = txb + (j - c_min) * TILE_WIDTH;
				int ty = tyb + (i - r_min) * TILE_HEIGHT;
			
				Bitmap tile = getTile(j, i);

				if (tile != null && ! tile.isRecycled())
				{
					c.drawBitmap(tile, tx, ty, null);
				}
			}
		}
		return result;
	}

	public Bitmap getTile(int x, int y) throws OutOfMemoryError
	{
		Tile tile = tileController.getTile(x, y, srcZoom);
		return tile.bitmap;
	}

	/**
	 * Calculates the inverse hyperbolic tangent of the number, i.e.
	 * the value whose hyperbolic tangent is number  
	 * @param arg number
	 * @return inverse hyperbolic tangent
	 */
	private static double atanh (double arg)
	{
	    return 0.5 * Math.log((1 + arg) / (1 - arg));
	}
	
	@Override
	public Bounds getBounds()
	{
		if (bounds == null)
		{
			bounds = new Bounds();
			bounds.minLat = -85.047336;
			bounds.maxLat = 85.051129;
			bounds.minLon = -180;
			bounds.maxLon = 180;
		}
		return bounds;
	}

	@Override
	public boolean getLatLonByXY(int x, int y, double[] ll)
	{
		double dx = x * 1.0 / TILE_WIDTH;
		double dy = y * 1.0 / TILE_HEIGHT;
		
		double n = Math.pow(2.0, srcZoom);
		if (tileProvider.ellipsoid)
		{
			ll[0] = (y-TILE_HEIGHT*n/2)/-(TILE_HEIGHT*n/(2*Math.PI));
			ll[0] = (2*Math.atan(Math.exp(ll[0]))-Math.PI/2)*180/Math.PI;

			double Zu = Math.toRadians(ll[0]);
			double Zum1 = Zu+1;
			double yy = (y-TILE_HEIGHT*n/2);
			int i=100000;
			while ((Math.abs(Zum1-Zu)>0.0000001)&&(i!=0))
			{
			  i--;
			  Zum1 = Zu;
			  Zu = Math.asin(1-((1+Math.sin(Zum1))*Math.pow(1-0.0818197*Math.sin(Zum1),0.0818197))
			  /(Math.exp((2*yy)/-(TILE_HEIGHT*n/(2*Math.PI)))*Math.pow(1+0.0818197*Math.sin(Zum1),0.0818197)));
			}
			ll[0]=Math.toDegrees(Zu);
		}
		else
		{
			ll[0] = Math.toDegrees(Math.atan((Math.sinh(Math.PI * (1 - 2 * dy / n)))));
		}
		ll[1] = dx * 360.0 / n - 180.0;		
		
		return true;
	}

	@Override
	public boolean getXYByLatLon(double lat, double lon, int[] xy)
	{
		double n = Math.pow(2.0, srcZoom);
		
		xy[0] = (int) Math.floor((lon + 180.0) / 360.0 * n * TILE_WIDTH);

		if (tileProvider.ellipsoid)
		{
			double z = Math.sin(Math.toRadians(lat));
			xy[1] = (int) Math.floor((1 - (atanh(z)-0.0818197*atanh(0.0818197*z)) / Math.PI) / 2 * n * TILE_HEIGHT);
		}
		else
		{
			xy[1] = (int) Math.floor((1 - (Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI)) / 2 * n * TILE_HEIGHT);
		}
		return true;
	}

	public boolean getOsmXYByLatLon(double lat, double lon, int[] xy)
	{
		double n = Math.pow(2.0, srcZoom);

		xy[0] = (int) Math.floor((lon + 180) / 360 * n);
		if (xy[0] == n)
			xy[0] -= 1;
		if (tileProvider.ellipsoid)
		{
			double z = Math.sin(Math.toRadians(lat));
			xy[1] = (int) Math.floor((1 - (atanh(z)-0.0818197*atanh(0.0818197*z)) / Math.PI) / 2 * n);
		}
		else
		{
			xy[1] = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * n);
		}
		if (xy[1] < 0)
			xy[1] = 0;
		return true;
	}
	
	@Override
	public double getNextZoom()
	{
		if (srcZoom >= tileProvider.maxZoom)
			return 0.0;
		Log.e("ONLINE", "Next zoom: " + Math.pow(2, this.srcZoom + 1 - defZoom));
		return Math.pow(2, this.srcZoom + 1 - defZoom);
	}

	@Override
	public double getPrevZoom()
	{
		if (srcZoom <= tileProvider.minZoom)
			return 0.0;
		Log.e("ONLINE", "Prev zoom: " + Math.pow(2, this.srcZoom - 1 - defZoom));
		return Math.pow(2, this.srcZoom - 1 - defZoom);
	}

	@Override
	public double getZoom()
	{
		return zoom;
	}

	@Override
	public void setZoom(double z)
	{
//		setZoom(srcZoom + Math.log(factor)/Math.log(2));

		int zDiff = (int) (Math.log(z) / Math.log(2));
		Log.e("ONLINE", "Zoom: " + z + " diff: " + zDiff);

		srcZoom = (byte) (defZoom + zDiff);
		
		if (srcZoom > tileProvider.maxZoom)
		{
			zDiff -= srcZoom - tileProvider.maxZoom;
			srcZoom = tileProvider.maxZoom;
		}
		if (srcZoom < tileProvider.minZoom)
		{
			zDiff -= srcZoom - tileProvider.minZoom;
			srcZoom = tileProvider.minZoom;
		}

		zoom = z;
		Log.e("ONLINE", "z: " + srcZoom + " zoom: " + zoom + " diff: " + zDiff);
		
//		zoom = Math.pow(2, this.srcZoom - defZoom);
		tileController.reset();
	    title = String.format("%s (%d)", tileProvider.name, srcZoom);
	}

	public int getScaledWidth()
	{
		return (int) (Math.pow(2.0, srcZoom) * TILE_WIDTH * zoom);
	}

	public int getScaledHeight()
	{
		return (int) (Math.pow(2.0, srcZoom) * TILE_HEIGHT * zoom);
	}

	public List<String> info()
	{
		ArrayList<String> info = new ArrayList<String>();
		
		info.add("title: " + title);
		if (projection != null)
		{
			info.add("projection: " + prjName + " (" + projection.getEPSGCode() + ")");
			info.add("\t" + projection.getPROJ4Description());
		}
		info.add("datum: " + datum);
		info.add("scale (mpp): " + mpp);
/*
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
*/
	
		return info;
	}
}
