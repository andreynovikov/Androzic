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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import com.androzic.Androzic;
import com.androzic.map.Tile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;

public class TileFactory
{
	public static Bitmap downloadTile(TileProvider provider, int x, int y, byte z)
	{
		String url = provider.getTileUri(x, y, z);
		try
		{
			URLConnection c = new URL(url).openConnection();
			c.setConnectTimeout(50000);
			c.connect();
			return BitmapFactory.decodeStream(c.getInputStream());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static void downloadTile(TileProvider provider, Tile t)
	{
		t.bitmap = downloadTile(provider, t.x, t.y, t.zoomLevel);
	}

	public static byte[] loadTile(TileProvider provider, int tx, int ty, byte z)
	{
		Androzic application = Androzic.getApplication();
		if (application == null)
			return null;
		
		String filename = z + File.separator + tx + "-" + ty;
		File file = new File(application.getRootPath() + File.separator + "tiles" + File.separator + provider.code + File.separator + filename);
		if (file.exists() == false)
			return null;
		try
		{
			FileInputStream fileInputStream;
			fileInputStream = new FileInputStream(file);
			byte[] dat = new byte[(int) file.length()];
			fileInputStream.read(dat);
			fileInputStream.close();
			return dat;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static void loadTile(TileProvider provider, Tile t)
	{
		byte[] data = loadTile(provider, t.x, t.y, t.zoomLevel);
		if (data != null)
			t.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
	}

	public static void saveTile(TileProvider provider, byte[] dat, int tx, int ty, byte z)
	{
		Androzic application = Androzic.getApplication();
		if (application == null)
			return;
		
		String filename = tx + "-" + ty;
		File file = new File(application.getRootPath() + File.separator + "tiles" + File.separator + provider.code + File.separator + z + File.separator);
		file.mkdirs();
		file = new File(file.getAbsolutePath() + File.separator + filename);
		if (!file.exists())
		{
			FileOutputStream fileOutputStream;
			try
			{
				fileOutputStream = new FileOutputStream(file);
				fileOutputStream.write(dat);
				fileOutputStream.flush();
				fileOutputStream.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static void saveTile(TileProvider provider, Tile t)
	{
		if (t.bitmap != null && ! t.bitmap.isRecycled())
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			t.bitmap.compress(CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
			byte[] data = bos.toByteArray();
			saveTile(provider, data, t.x, t.y, t.zoomLevel);
		}			
	}
}
