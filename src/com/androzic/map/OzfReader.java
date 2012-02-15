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
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

public class OzfReader
{
	static final double[] zoomLevelsSupported =
	{
		0.030,
		0.060,
		0.100,
		0.250,
		0.500,
		0.750,
		1.000,
		1.250,
		1.500,
		1.750,
		2.000,
		2.500,
		3.000,
		4.000,
		5.000
	};

	private MapScale[] zoomTable;
	private int zoomCurrent;
	private OzfFile ozf;
	private TileRAMCache cache;

	public OzfReader(File file) throws IOException, OutOfMemoryError
	{
		ozf = OzfDecoder.open(file);
		buildZoomTable();
		setZoom(1.0);
	}

	public void setCache(TileRAMCache cache)
	{
		this.cache = cache;
	}

	public double getZoom()
	{
		return zoomTable[zoomCurrent].zoom;
	}
	
	public double getNextZoom()
	{
		if (zoomCurrent < zoomLevelsSupported.length - 1)
		{
	    	return zoomTable[zoomCurrent+1].zoom;
		}
		else
		{
			return 0.0;
		}
	}

	public double getPrevZoom()
	{
		if (zoomCurrent > 0)
		{
	    	return zoomTable[zoomCurrent-1].zoom;
		}
		else
		{
			return 0.0;
		}
	}

	protected double setZoom(double zoom)
	{
		double zoomDelta = Double.MAX_VALUE;

		for (int i = 0; i < zoomLevelsSupported.length; i++)
		{
			double delta = Math.abs(zoomTable[i].zoom - zoom);

			if ( delta < zoomDelta)
			{
				zoomCurrent = i;
				zoomDelta = delta;
			}
		}
		
		return zoomTable[zoomCurrent].zoom;
	}

	public void close()
	{
		OzfDecoder.close(ozf);
	}
	
	private void buildZoomTable()
	{
		zoomTable = new MapScale[zoomLevelsSupported.length];
		for (int i = 0; i < zoomTable.length; i++)
		{
			zoomTable[i] = new MapScale();
		}

		Log.d("OZF", "mapinfo: " + ozf.width() + " " + ozf.height());
		Log.d("OZF", "building scales table");
		
		double b = ozf.height();
		for (int i = 0; i < zoomLevelsSupported.length; i++)
		{
			int k = 0;
			double delta = Double.MAX_VALUE;
			double ozf_zoom = 1;
			
			for (int j = 0; j < ozf.scales; j++)
			{
				double a = OzfDecoder.scale_dy(ozf, j);
			
				double tenpercents = Math.round((a / b) * 1000);
				double zoom = tenpercents / 1000;

				// if current zoom is < 100% - we need to select 
				// nearest upper native zoom
				// otherwize we need to select
				// any nearest zoom

				if (zoomLevelsSupported[i] < 1.0)
					if (zoomLevelsSupported[i] > zoom)
						continue;
				
				double d = zoom - zoomLevelsSupported[i];
				
				d = d < 0 ? -d : d;

				if (d < delta)
				{
					delta = d;
					k = j;
					ozf_zoom = zoom;
				}
			}		
			
			zoomTable[i].zoom = zoomLevelsSupported[i];
			zoomTable[i].source = k;
			zoomTable[i].factor = zoomLevelsSupported[i] / ozf_zoom;

			Log.d("OZF", String.format("scale[%d]: %f, selected source scale: %f (%d), factor: %f", i, zoomTable[i].zoom, ozf_zoom, k, zoomTable[i].zoom/ozf_zoom));
		}
	}

	public int[] map_xy_to_cr(int[] map_xy)
	{
		int[] cr = new int[2];

		cr[0] = (int) (Math.abs(map_xy[0])/(OzfDecoder.OZF_TILE_WIDTH * zoomTable[zoomCurrent].factor));
		cr[1] = (int) (Math.abs(map_xy[1])/(OzfDecoder.OZF_TILE_HEIGHT * zoomTable[zoomCurrent].factor));
		
		return cr;
	}

	public int[] map_xy_to_xy_on_tile(int[] map_xy)
	{
		int[] cr = map_xy_to_cr(map_xy);
		int[] xy = new int[2];

		xy[0] = (int) Math.round(map_xy[0] - cr[0] * (OzfDecoder.OZF_TILE_WIDTH * zoomTable[zoomCurrent].factor));
		xy[1] = (int) Math.round(map_xy[1] - cr[1] * (OzfDecoder.OZF_TILE_HEIGHT * zoomTable[zoomCurrent].factor));
		
		return xy;
	}

	public int tile_dx()
	{
		return tile_dx(0, 0);
	}
	
	public int tile_dy()
	{
		return tile_dy(0, 0);
	}
	
	public int tile_dx(int c, int r)
	{
		if (c > tiles_per_x() - 1 || r > tiles_per_y() - 1)
		{
			return 0;
		}

		double dx = OzfDecoder.OZF_TILE_WIDTH;

		if (c == tiles_per_x() - 1)
		{
			int w = OzfDecoder.scale_dx(ozf, zoomTable[zoomCurrent].source);
			dx = w - (w / OzfDecoder.OZF_TILE_WIDTH) * OzfDecoder.OZF_TILE_WIDTH;
		}
		
		return (int) (dx * zoomTable[zoomCurrent].factor);
	}

	public int tile_dy(int c, int r)
	{
		if (c > tiles_per_x() - 1 || r > tiles_per_y() - 1)
			return 0;

		double dy = OzfDecoder.OZF_TILE_HEIGHT;
		
		if ( r == tiles_per_y() - 1)
		{
			int h = OzfDecoder.scale_dy(ozf, zoomTable[zoomCurrent].source);
			dy = h - (h / OzfDecoder.OZF_TILE_HEIGHT) * OzfDecoder.OZF_TILE_HEIGHT;
		}
		
		return (int) (dy * zoomTable[zoomCurrent].factor);
	}

	public int tiles_per_x()
	{
		return OzfDecoder.num_tiles_per_x(ozf, zoomTable[zoomCurrent].source);
	}

	public int tiles_per_y()
	{
		return OzfDecoder.num_tiles_per_y(ozf, zoomTable[zoomCurrent].source);
	}

	public Bitmap tile_get(int c, int r) throws OutOfMemoryError
	{
		if (c < 0 || c > tiles_per_x() - 1)
			return null;

		if (r < 0 || r > tiles_per_y() - 1)
			return null;

		long key = Tile.getKey(c, r, (byte) zoomCurrent);
		Tile tile = new Tile(c, r, (byte) zoomCurrent);
		Bitmap tileBitmap = null;
		
		if (cache != null && cache.containsKey(key))
		{
			tileBitmap = cache.get(key).bitmap;
		}
		else
		{
	        int w = OzfDecoder.OZF_TILE_WIDTH;
	        int h = OzfDecoder.OZF_TILE_HEIGHT;
			if (zoomTable[zoomCurrent].factor < 1.0)
			{
		        w = (int) (zoomTable[zoomCurrent].factor * w);
		        h = (int) (zoomTable[zoomCurrent].factor * h);
			}
			int[] data = OzfDecoder.getTile(ozf, zoomTable[zoomCurrent].source, c, r, w, h);
			if (data != null)
			{
				tileBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
				tileBitmap.setPixels(data, 0, w, 0, 0, w, h);
//				tileBitmap = Bitmap.createBitmap(OzfDecoder.OZF_TILE_WIDTH, OzfDecoder.OZF_TILE_HEIGHT, Bitmap.Config.RGB_565);
//				tileBitmap.setPixels(data, 0, OzfDecoder.OZF_TILE_WIDTH, 0, 0, OzfDecoder.OZF_TILE_WIDTH, OzfDecoder.OZF_TILE_HEIGHT);
			}
			if (tileBitmap != null && zoomTable[zoomCurrent].factor > 1.0)
			{
		        int sw = (int) (zoomTable[zoomCurrent].factor * OzfDecoder.OZF_TILE_WIDTH);
		        int sh = (int) (zoomTable[zoomCurrent].factor * OzfDecoder.OZF_TILE_HEIGHT);
		        Bitmap scaled = Bitmap.createScaledBitmap(tileBitmap, sw, sh, true);
		        tileBitmap = scaled;
			}
		
			if (cache != null && tileBitmap != null)
			{
				tile.bitmap = tileBitmap;
				cache.put(tile);
			}
		}
		
		return tileBitmap;
	}

	public void drawMap(int[] map_xy, int width, int height, Canvas c)
	{
		int[] cr = map_xy_to_cr(map_xy);
		int[] xy = map_xy_to_xy_on_tile(map_xy);
		
		int tile_w = (int) (OzfDecoder.OZF_TILE_WIDTH * zoomTable[zoomCurrent].factor); //tile_dx();
		int tile_h = (int) (OzfDecoder.OZF_TILE_HEIGHT * zoomTable[zoomCurrent].factor); //tile_dy();
		
		if (tile_w == 0 || tile_h == 0)
		{
			c.drawRGB(255, 0, 0);
			return;
		}

		int tiles_per_x = width / tile_w;
		int tiles_per_y = height / tile_h;

		int c_min = cr[0] - tiles_per_x / 2 - 2;
		int c_max = cr[0] + tiles_per_x / 2 + 2;
		
		int r_min = cr[1] - tiles_per_y / 2 - 2;
		int r_max = cr[1] + tiles_per_y / 2 + 2;
		
		if (c_min < 0) c_min = 0;
		if (r_min < 0) r_min = 0;
		
		if (c_max > tiles_per_x())
			c_max = tiles_per_x();

		if (r_max > tiles_per_y())
			r_max = tiles_per_y();

		int txb = width / 2 - xy[0] - (cr[0] - c_min) * tile_w;
		int tyb = height / 2 - xy[1] - (cr[1] - r_min) * tile_h;

		for (int i = r_min; i < r_max; i++)
		{
			for (int j = c_min; j < c_max; j++)
			{
				int tx = txb + (j - c_min) * tile_w;
				int ty = tyb + (i - r_min) * tile_h;
			
				Bitmap tile = tile_get(j, i);

				if (tile != null)
				{
					if (tile_dx(j, i) < tile_w || tile_dy(j, i) < tile_h)
					{
						Rect src = new Rect(0,0,tile_dx(j, i),tile_dy(j, i));
						Rect dst = new Rect(tx, ty, tx + src.right, ty + src.bottom);
						c.drawBitmap(tile, src, dst, null);
					}
					else
					{
						c.drawBitmap(tile, tx, ty, null);
					}
				}
			}
		}
	}
	
	private class MapScale
	{
		double	zoom;
		int		source;
		double	factor;
	}
}
