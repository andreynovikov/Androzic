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
import android.util.Log;

public class OzfReader
{
	private double	zoom;
	private int		source;
	private double	factor;
	private byte	zoomKey;
	private OzfFile ozf;
	private TileRAMCache cache;

	public OzfReader(File file) throws IOException, OutOfMemoryError
	{
		ozf = OzfDecoder.open(file);
		setZoom(1.0);
	}

	public void setCache(TileRAMCache cache)
	{
		this.cache = cache;
	}

	public double getZoom()
	{
		return zoom;
	}
	
	protected double setZoom(double zoom)
	{
		this.zoom = zoom;

		double b = ozf.height();
		int k = 0;
		double delta = Double.MAX_VALUE;
		double ozf_zoom = 1;
		
		for (int i = 0; i < ozf.scales; i++)
		{
			double a = OzfDecoder.scale_dy(ozf, i);
		
			double tenpercents = Math.round((a / b) * 1000);
			double z = tenpercents / 1000;

			// if current zoom is < 100% - we need to select 
			// nearest upper native zoom
			// otherwize we need to select
			// any nearest zoom

			if (this.zoom < 1.0)
				if (this.zoom > z)
					continue;
			
			double d = Math.abs(z - this.zoom);
			if (d < delta)
			{
				delta = d;
				k = i;
				ozf_zoom = z;
			}
		}		
		
		source = k;
		factor = this.zoom / ozf_zoom;
		zoomKey = (byte) (this.zoom * 50);

		Log.d("OZF", String.format("zoom: %f, selected source scale: %f (%d), factor: %f", this.zoom, ozf_zoom, source, factor));
		
		return this.zoom;
	}

	public void close()
	{
		OzfDecoder.close(ozf);
	}

	public double map_x_to_c(int map_x)
	{
		return map_x / (OzfDecoder.OZF_TILE_WIDTH * factor);
	}

	public double map_y_to_r(int map_y)
	{
		return map_y / (OzfDecoder.OZF_TILE_HEIGHT * factor);
	}

	public int[] map_xy_to_cr(int[] map_xy)
	{
		int[] cr = new int[2];

		cr[0] = (int) (Math.abs(map_xy[0])/(OzfDecoder.OZF_TILE_WIDTH * factor));
		cr[1] = (int) (Math.abs(map_xy[1])/(OzfDecoder.OZF_TILE_HEIGHT * factor));
		
		return cr;
	}

	public int[] map_xy_to_xy_on_tile(int[] map_xy)
	{
		int[] cr = map_xy_to_cr(map_xy);
		int[] xy = new int[2];

		xy[0] = (int) Math.round(map_xy[0] - cr[0] * (OzfDecoder.OZF_TILE_WIDTH * factor));
		xy[1] = (int) Math.round(map_xy[1] - cr[1] * (OzfDecoder.OZF_TILE_HEIGHT * factor));
		
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
			int w = OzfDecoder.scale_dx(ozf, source);
			dx = w - (w / OzfDecoder.OZF_TILE_WIDTH) * OzfDecoder.OZF_TILE_WIDTH;
			if (dx == 0)
				dx = OzfDecoder.OZF_TILE_WIDTH;
		}
		
		return (int) (dx * factor);
	}

	public int tile_dy(int c, int r)
	{
		if (c > tiles_per_x() - 1 || r > tiles_per_y() - 1)
			return 0;

		double dy = OzfDecoder.OZF_TILE_HEIGHT;
		
		if ( r == tiles_per_y() - 1)
		{
			int h = OzfDecoder.scale_dy(ozf, source);
			dy = h - (h / OzfDecoder.OZF_TILE_HEIGHT) * OzfDecoder.OZF_TILE_HEIGHT;
			if (dy == 0)
				dy = OzfDecoder.OZF_TILE_HEIGHT;
		}
		
		return (int) (dy * factor);
	}

	public int tiles_per_x()
	{
		return OzfDecoder.num_tiles_per_x(ozf, source);
	}

	public int tiles_per_y()
	{
		return OzfDecoder.num_tiles_per_y(ozf, source);
	}

	public Bitmap tile_get(int c, int r) throws OutOfMemoryError
	{
		if (c < 0 || c > tiles_per_x() - 1)
			return null;

		if (r < 0 || r > tiles_per_y() - 1)
			return null;

		long key = Tile.getKey(c, r, zoomKey);
		Tile tile = new Tile(c, r, zoomKey);
		Bitmap tileBitmap = null;
		
		if (cache != null)
		{
			Tile t = cache.get(key);
			if (t != null)
				tileBitmap = t.bitmap;
		}
		if (tileBitmap == null)
		{
	        int w = OzfDecoder.OZF_TILE_WIDTH;
	        int h = OzfDecoder.OZF_TILE_HEIGHT;
			if (factor < 1.0)
			{
		        w = (int) (factor * w);
		        h = (int) (factor * h);
			}
			int[] data = OzfDecoder.getTile(ozf, source, c, r, w, h);
			if (data != null)
			{
				tileBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
				tileBitmap.setPixels(data, 0, w, 0, 0, w, h);
			}
			if (tileBitmap != null && factor > 1.0)
			{
		        int sw = (int) (factor * OzfDecoder.OZF_TILE_WIDTH);
		        int sh = (int) (factor * OzfDecoder.OZF_TILE_HEIGHT);
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
}
