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
import java.io.RandomAccessFile;

public class OzfFile
{
	public static final int OZF_STREAM_DEFAULT = 0;
	public static final int OZF_STREAM_ENCRYPTED = 1;
	
	RandomAccessFile reader;
	long fileptr;
	int type;
	int key;
	long size;

	int scales;
	int[] scales_table;
	OzfImageHeader[] images;
	
	Ozf2Header ozf2;
	Ozf3Header ozf3;
	
	public OzfFile(File file, RandomAccessFile reader, int type)
	{
		this.type = type;
		if (this.type == OZF_STREAM_DEFAULT)
		{
			ozf2 = new Ozf2Header();
		}
		else if (this.type == OZF_STREAM_ENCRYPTED)
		{
			ozf3 = new Ozf3Header();
		}
		size = file.length();
		this.reader = reader;
	}

	public class OzfImageHeader
	{
		int width;
		int height;
		int xtiles;
		int ytiles;

		byte[] palette = new byte[1024];
		int encryption_depth;
	}

	public class Ozf2Header
	{
		int magic;
		int dummy1;
		int dummy2;
		int dummy3;
		int dummy4;

		int width;
		int height;

		int depth;
		int bpp;

		int dummy5;

		int memsiz;

		int dummy6;
		int dummy7;
		int dummy8;
		int version;
	}

	public class Ozf3Header
	{
		int size;
		int width;
		int height;
		int depth;
		int bpp;
	}

	public void newImages()
	{
		images = new OzfImageHeader[scales];
		for (int i = 0; i < scales; i++)
		{
			images[i] = new OzfImageHeader();
		}
	}
	
	public int width()
	{
		if (ozf2 != null) return ozf2.width;
		if (ozf3 != null) return ozf3.width;
		return 0;
	}

	public int height()
	{
		if (ozf2 != null) return ozf2.height;
		if (ozf3 != null) return ozf3.height;
		return 0;
	}
}
