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
import java.io.RandomAccessFile;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

import android.util.Log;

public class OzfDecoder
{
	public static final int OZFX3_KEY_MAX = 256;
	public static final int OZFX3_MAGIC_OFFSET_0 = 14;
	public static final int OZFX3_MAGIC_OFFSET_1 = 165;
	public static final int OZFX3_MAGIC_OFFSET_2 = 0xA2;
	public static final int OZFX3_MAGIC_BLOCKLENGTH_0 = 150;
	public static final int OZFX3_KEY_BLOCK_SIZE = 4;

	public static final int D0_KEY_CYCLE = 0xD;
	public static final int D1_KEY_CYCLE = 0x1A;
	public static final int OZFX3_ZDATA_ENCRYPTION_LENGTH = 16;

	public static final int OZF_TILE_WIDTH = 64;
	public static final int OZF_TILE_HEIGHT = 64;

	private static final byte[] d0_key =
	{
		(byte) 0x2D, (byte) 0x4A, (byte) 0x43, (byte) 0xF1, (byte) 0x27, (byte) 0x9B, (byte) 0x69, (byte) 0x4F,
		(byte) 0x36, (byte) 0x52, (byte) 0x87, (byte) 0xEC, (byte) 0x5F, (byte) 0x8D, (byte) 0x40, (byte) 0x00 
	};

	private static final byte[] d1_key =
	{
		(byte) 0x2D, (byte) 0x4A, (byte) 0x43, (byte) 0xF1, (byte) 0x27, (byte) 0x9B, (byte) 0x69, (byte) 0x4F,
		(byte) 0x36, (byte) 0x52, (byte) 0x87, (byte) 0xEC, (byte) 0x5F, (byte) 0x42, (byte) 0x53, (byte) 0x22,
		(byte) 0x9E, (byte) 0x8B, (byte) 0x2D, (byte) 0x83, (byte) 0x3D, (byte) 0xD2, (byte) 0x84, (byte) 0xBA,
		(byte) 0xD8, (byte) 0x5B, (byte) 0x8B, (byte) 0xC0
	};
	
	private static ZStream zip = new ZStream();
	private static int[] pixels = new int[OZF_TILE_WIDTH * OZF_TILE_HEIGHT];

	public static boolean useNativeCalls = true;

	public final static byte readByte(RandomAccessFile reader) throws IOException
	{
		return reader.readByte();
	}

	public final static int readShort(RandomAccessFile reader) throws IOException
	{
		short s = reader.readShort();
	    return (s >>> 8 & 0x00FF) | (s << 8 & 0xFF00);
	}

	public final static int readInt(RandomAccessFile reader) throws IOException
	{
		int i = reader.readInt();
		return (i >>> 24) | (i << 24) | ((i << 8) & 0x00FF0000) | ((i >> 8) & 0x0000FF00);

	}
	
	private final static short getShort(byte[] buffer, int pos)
	{
		return (short) ((buffer[pos] & 0xFF) | ((buffer[pos+1] & 0xFF) << 8));
	}

	private final static int getInt(byte[] buffer, int pos)
	{
		return (buffer[pos] & 0xFF) | ((buffer[pos+1] & 0xFF) << 8) | ((buffer[pos+2] & 0xFF) << 16) | ((buffer[pos+3] & 0xFF) << 24);
	}

	public static int scale_dx(OzfFile file, int scale)
	{
		return file.images[scale].width;
	}

	public static int scale_dy(OzfFile file, int scale)
	{
		return file.images[scale].height;
	}

	public static int num_tiles_per_x(OzfFile file, int scale)
	{
		return file.images[scale].xtiles;
	}

	public static int num_tiles_per_y(OzfFile file, int scale)
	{
		return file.images[scale].ytiles;
	}

	public static int[] getTile(OzfFile file, int scale, int x, int y, int w, int h)
	{
		if (scale > file.scales - 1)
			return null;
		
		if (x > file.images[scale].xtiles - 1)
			return null;

		if (y > file.images[scale].ytiles - 1)
			return null;

		if (x < 0)
			return null;

		if (y < 0)
			return null;
		
		int i = y * file.images[scale].xtiles + x;

		if (useNativeCalls)
		{
			return getTileNative(file.fileptr, file.type, file.key, file.images[scale].encryption_depth, file.scales_table[scale], i, w, h, file.images[scale].palette);
		}
		
		int tilesize;
		byte[] tile;
		
		try
		{
			file.reader.seek(file.scales_table[scale]);
			file.reader.skipBytes(1036);
			file.reader.skipBytes(i * 4);
	
			int tilepos, tilepos1;
	
			if (file.type == OzfFile.OZF_STREAM_ENCRYPTED)
			{
				byte[] buffer = new byte[4];
				file.reader.read(buffer);
				ozf_decode1(buffer, buffer.length, (byte) file.key);
				tilepos = getInt(buffer, 0);
				file.reader.read(buffer);
				ozf_decode1(buffer, buffer.length, (byte) file.key);
				tilepos1 = getInt(buffer, 0);			
			}
			else
			{
				tilepos = readInt(file.reader);
				tilepos1 = readInt(file.reader);
			}
	
			tilesize = tilepos1 - tilepos;
	
			tile = new byte[tilesize];
			
			file.reader.seek(tilepos);
			file.reader.read(tile);
		}
		catch (IOException e)
		{
			Log.e("OZF", "Tile read io error");
			e.printStackTrace();
			return null;
		}
		
		if (file.type == OzfFile.OZF_STREAM_ENCRYPTED)
		{
			if (file.images[scale].encryption_depth == -1)
				ozf_decode1(tile, tilesize, (byte) file.key);
			else
				ozf_decode1(tile, file.images[scale].encryption_depth, (byte) file.key);
		}
		
		if (!(tile[0] == 0x78 && (tile[1] & 0xFF) == 0xDA))  // zlib signature
		{
			Log.w("OZF", "zlib signature verification failed");
			return null;
		}
	
		int decompressed_size = OZF_TILE_WIDTH * OZF_TILE_HEIGHT;
		byte[] decompressed = new byte[decompressed_size];
		
	    zip.next_in=tile;
	    zip.avail_in = tilesize;
	    zip.next_in_index=0;
	    zip.next_out=decompressed;
	    zip.avail_out = decompressed_size;
	    zip.next_out_index=0;

	    zip.inflateInit();
	    int err = zip.inflate(JZlib.Z_FINISH);
	    if (err != JZlib.Z_OK)
	    {
	    	if (zip.msg != null) Log.e("OZF", zip.msg + " " + err);		          
	    }
	    decompressed_size = (int) zip.total_out;
	    zip.inflateEnd();

		byte[] palette = file.images[scale].palette;

		int tile_z = OZF_TILE_WIDTH * (OZF_TILE_HEIGHT - 1) * 4;
		int tile_x = 0;

		for(int j = 0; j < OZF_TILE_WIDTH * OZF_TILE_HEIGHT; j++)
		{
			int c = decompressed[j] & 0xFF;
			
			int r = palette[c*4 + 2];
			int g = palette[c*4 + 1];
			int b = palette[c*4 + 0];
			int a = 255;
			
			int k = ((OZF_TILE_WIDTH - 1) - (j / OZF_TILE_WIDTH)) * OZF_TILE_WIDTH + tile_x;
			pixels[k] = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);

			tile_x++;
			tile_z += 4;
			if (tile_x == OZF_TILE_WIDTH)
			{
				tile_x = 0;
				tile_z -= OZF_TILE_WIDTH * 4 * 2;
			}
		}

		return pixels;
	}

	private static void ozf_decode1(byte[] source, int n, byte key)
	{
		for(int j = 0; j < n; j++)
		{
			int k = j % D1_KEY_CYCLE;
			
			byte c = d1_key[k];
			c += (key & 0xFF);
			byte c1	= source[j];
			c ^= c1;
			source[j] = c;
		}
	}

	public static OzfFile open(File file) throws IOException, OutOfMemoryError
	{
		Log.d("OZF", "opening " + file.getName());
		
		RandomAccessFile reader = new RandomAccessFile(file, "r");
		int magic = readShort(reader);
		Log.d("OZF", String.format("magic: %#x", magic));
		int type = -1;
		switch (magic)
		{
			case 0x7778:
				type = OzfFile.OZF_STREAM_DEFAULT;
				break;
			case 0x7780:
				type = OzfFile.OZF_STREAM_ENCRYPTED;
				break;
		}
		OzfFile ozfFile = new OzfFile(file, reader, type);
		Log.d("OZF", "file size: "+ozfFile.size+" bytes");

		if (ozfFile.type == OzfFile.OZF_STREAM_ENCRYPTED)
		{
			Log.d("OZF", file.getName() + " is an encrypted stream");

			ozfFile.key = calculateKey(ozfFile.reader);
				
			Log.d("OZF", "stream key = " + String.format("%#x", ozfFile.key));

			initEncryptedStream(ozfFile);
		}
		else if (ozfFile.type == OzfFile.OZF_STREAM_DEFAULT)
		{
			Log.d("OZF", file.getName() + " is an raw stream");
			initRawStream(ozfFile);			
		}
		else
		{
			throw new IllegalArgumentException("Unsupported map image format");
		}
		
		if (useNativeCalls)
		{
			ozfFile.fileptr = openImageNative(file.getAbsolutePath());
		}
		
		return ozfFile;
	}
	
	private static void initRawStream(OzfFile ozfFile) throws IOException, OutOfMemoryError
	{
		long offset;
		int scales_table_offset;
		
		Log.d("OZF", "processing raw stream");

		offset = 0;
		
		RandomAccessFile reader = ozfFile.reader;

		reader.seek(offset);
		
		ozfFile.ozf2.magic = readShort(reader);
		ozfFile.ozf2.dummy1 = readInt(reader);
		ozfFile.ozf2.dummy2 = readInt(reader);
		ozfFile.ozf2.dummy3 = readInt(reader);
		ozfFile.ozf2.dummy4 = readInt(reader);

		ozfFile.ozf2.width = readInt(reader);
		ozfFile.ozf2.height = readInt(reader);

		ozfFile.ozf2.depth = readShort(reader); 
		ozfFile.ozf2.bpp = readShort(reader); 

		ozfFile.ozf2.dummy5 = readInt(reader);

		ozfFile.ozf2.memsiz = readInt(reader); 

		ozfFile.ozf2.dummy6 = readInt(reader); 
		ozfFile.ozf2.dummy7 = readInt(reader); 
		ozfFile.ozf2.dummy8 = readInt(reader); 
		ozfFile.ozf2.version = readInt(reader); 
		
		Log.d("OZF", "decoded ozf2 header:");
		Log.d("OZF", "\twidth:\t" + ozfFile.ozf2.width);
		Log.d("OZF", "\theight:\t" + ozfFile.ozf2.height);
		Log.d("OZF", "\tdepth:\t" + ozfFile.ozf2.depth);
		Log.d("OZF", "\tbpp:\t" + ozfFile.ozf2.bpp);

		offset = ozfFile.size - 4;
		Log.d("OZF", "Offset:" + offset);
		reader.seek(offset);
		scales_table_offset = readInt(reader);
		
		Log.d("OZF", "scales table starts at: " + scales_table_offset);

	 	ozfFile.scales = (int) ((ozfFile.size - scales_table_offset - 4) / 4);

	 	Log.d("OZF", "scales total: " + ozfFile.scales);

		ozfFile.scales_table = new int[ozfFile.scales];
		ozfFile.newImages();

		reader.seek(scales_table_offset);
		for (int i = 0; i < ozfFile.scales; i++)
		{
			ozfFile.scales_table[i] = readInt(reader);
		}
		
		for (int i = 0; i < ozfFile.scales; i++)
		{
			Log.d("OZF", "scale " + i + " header starts at: " + ozfFile.scales_table[i]);

			reader.seek(ozfFile.scales_table[i]);
			
			ozfFile.images[i].width = readInt(reader);
			ozfFile.images[i].height = readInt(reader);
			ozfFile.images[i].xtiles = readShort(reader);
			ozfFile.images[i].ytiles = readShort(reader);

			Log.d("OZF", "\twidth:\t" + ozfFile.images[i].width);
			Log.d("OZF", "\theight:\t" + ozfFile.images[i].height);
			Log.d("OZF", "\ttiles per x:\t" + ozfFile.images[i].xtiles);
			Log.d("OZF", "\ttiles per y:\t" + ozfFile.images[i].ytiles);
		
			ozfFile.images[i].palette = new byte[256*4];
			reader.read(ozfFile.images[i].palette);
		}
	}

	private static void initEncryptedStream(OzfFile ozfFile) throws IOException, OutOfMemoryError
	{
		int bytes_per_infoblock;
		int offset;
		int scales_table_offset;
		byte[] buffer;
		
		Log.d("OZF", "processing encrypted stream\n");

		RandomAccessFile reader = ozfFile.reader;

		reader.seek(OZFX3_MAGIC_OFFSET_0);
		bytes_per_infoblock = readByte(reader) & 0xFF;

		Log.d("OZF", "bytes per info block: " + bytes_per_infoblock);

		offset = OZFX3_MAGIC_OFFSET_1 + bytes_per_infoblock - OZFX3_MAGIC_BLOCKLENGTH_0 + 4;

		reader.seek(offset);

		buffer = new byte[4+4+4+2+2];
		reader.read(buffer);
		ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);

		ozfFile.ozf3.size = getInt(buffer, 0);
		ozfFile.ozf3.width = getInt(buffer, 4);
		ozfFile.ozf3.height = getInt(buffer, 8);
		ozfFile.ozf3.depth = getShort(buffer, 12);
		ozfFile.ozf3.bpp = getShort(buffer, 14);
		

		Log.d("OZF", "decoded ozf3 header:");
		Log.d("OZF", "\tsize:\t" + ozfFile.ozf3.size);
		Log.d("OZF", "\twidth:\t" + ozfFile.ozf3.width);
		Log.d("OZF", "\theight:\t" + ozfFile.ozf3.height);
		Log.d("OZF", "\tdepth:\t" + ozfFile.ozf3.depth);
		Log.d("OZF", "\tbpp:\t" + ozfFile.ozf3.bpp);

		offset = (int) (ozfFile.size - 4);
		
		reader.seek(offset);
		
		buffer = new byte[4];
		reader.read(buffer);
		ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);

		scales_table_offset = getInt(buffer, 0);
		
		Log.d("OZF", "scales table starts at: " + scales_table_offset);

	 	ozfFile.scales = (int) ((ozfFile.size - scales_table_offset - 4) / 4);

	 	Log.d("OZF", "scales total: " + ozfFile.scales);

	 	// FIXME it's a hack, need better detection
	 	if (ozfFile.ozf3.size < 0 ||
	 		ozfFile.ozf3.width < 0 ||
	 		ozfFile.ozf3.height < 0 ||
	 		ozfFile.ozf3.bpp < 0 ||
	 		ozfFile.ozf3.depth < 0) throw new IOException("Couldn't decode OZFX3 file");
	 	
		ozfFile.scales_table = new int[ozfFile.scales];
		ozfFile.newImages();

		reader.seek(scales_table_offset);

		for (int i = 0; i < ozfFile.scales; i++)
		{
			buffer = new byte[4];
			reader.read(buffer);
			ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
			ozfFile.scales_table[i] = getInt(buffer, 0);
		}
		
		for (int i = 0; i < ozfFile.scales; i++)
		{
			Log.d("OZF", "scale " + i + " header starts at: " + ozfFile.scales_table[i]);

			reader.seek(ozfFile.scales_table[i]);

			buffer = new byte[4];
			reader.read(buffer);
			ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
			ozfFile.images[i].width = getInt(buffer, 0);
			reader.read(buffer);
			ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
			ozfFile.images[i].height = getInt(buffer, 0);
			buffer = new byte[2];
			reader.read(buffer);
			ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
			ozfFile.images[i].xtiles = getShort(buffer, 0) & 0xFFFF;
			reader.read(buffer);
			ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
			ozfFile.images[i].ytiles = getShort(buffer, 0) & 0xFFFF;

			Log.d("OZF", "\twidth:\t" + ozfFile.images[i].width);
			Log.d("OZF", "\theight:\t" + ozfFile.images[i].height);
			Log.d("OZF", "\ttiles per x:\t" + ozfFile.images[i].xtiles);
			Log.d("OZF", "\ttiles per y:\t" + ozfFile.images[i].ytiles);

			ozfFile.images[i].palette = new byte[256*4];
			reader.read(ozfFile.images[i].palette);
			ozf_decode1(ozfFile.images[i].palette, 256*4, (byte) ozfFile.key);
			
			int[] tiles = new int[2];
			
			buffer = new byte[4];
			for (int j = 0; j < 2; j++)
			{
				reader.read(buffer);
				ozf_decode1(buffer, buffer.length, (byte) ozfFile.key);
				tiles[j] = getInt(buffer, 0);
			}

			int tilesize = tiles[1] - tiles[0];

			byte[] tile = new byte[tilesize];
			
			reader.seek(tiles[0]);
			reader.read(tile);
												
			ozfFile.images[i].encryption_depth = getEncyptionDepth(tile, tilesize, ozfFile.key);
						
			Log.d("OZF", "\tencryption depth:\t" + ozfFile.images[i].encryption_depth);
		}
	}

	private static int calculateKey(RandomAccessFile reader) throws IOException
	{
		int key = 0;
		int initial = 0;
		byte[] keyblock = new byte[OZFX3_KEY_BLOCK_SIZE];
		int bytes_per_info = 0;

		reader.seek(OZFX3_MAGIC_OFFSET_0);
		
		bytes_per_info = readByte(reader) & 0xFF;
		
		reader.seek(OZFX3_MAGIC_OFFSET_2);
		initial = readByte(reader) & 0xFF;

		long offset = OZFX3_MAGIC_OFFSET_1 + bytes_per_info - OZFX3_MAGIC_BLOCKLENGTH_0;
		reader.seek(offset);
		reader.read(keyblock);

		ozf_decode1(keyblock, OZFX3_KEY_BLOCK_SIZE, (byte) initial);
		
		Log.d("OZF", "key block: " + String.format("%#x", keyblock[0] & 0xFF));

		switch (keyblock[0] & 0xFF)
		{
			case 0xf1:
				initial +=0x8a;
				break;
			case 0x18:
			case 0x54:
				initial +=0xa0;
				break;
			case 0x56:
				initial +=0xb9;
				break;
			case 0x43:
				initial +=0x6a;
				break;
			case 0x83:
				initial +=0xa4;
				break;
			case 0xc5:
				initial +=0x7e;
				break;
			case 0x38:
				initial +=0xc1;
				break;
			case 0x76:
				throw new IOException("Couldn't decode OZFX3 file");
		}

		key = initial;

		return key;
	}

	private static int getEncyptionDepth(byte[] data, int size, int key)
	{
		int nEncryptionDepth = -1;

		byte[] p = new byte[size];
		int nDecompressed = OzfDecoder.OZF_TILE_WIDTH * OzfDecoder.OZF_TILE_HEIGHT;
		
		byte[] pDecompressed = new byte[nDecompressed];

		for (int i = 4; i <= size; i++)
		{
			System.arraycopy(data, 0, p, 0, size);
			ozf_decode1(p, i, (byte) key);

			nEncryptionDepth = i;
			
			if (decompressTile(pDecompressed, p))
				break;
		}

		if (nEncryptionDepth == size)
			nEncryptionDepth = -1;

		return nEncryptionDepth;
	}

	private static boolean decompressTile(byte[] dest, byte[] source)
	{
	    zip.next_in = source;
	    zip.avail_in = source.length;
	    zip.next_in_index = 0;
	    zip.next_out = dest;
	    zip.avail_out = dest.length;
	    zip.next_out_index=0;

	    zip.inflateInit();
	    int err = zip.inflate(JZlib.Z_FINISH);
	    if (err != JZlib.Z_OK && err != JZlib.Z_STREAM_END)
	    {
	    	return false;
	    }
//	    decompressed_size = (int) zip.total_out;
	    zip.inflateEnd();
	    return true;
	}

	public static void close(OzfFile file)
	{
		try
		{
			file.reader.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if (useNativeCalls)
		{
			closeImageNative(file.fileptr);
		}
	}
	
	private synchronized static native long openImageNative(String path);
	private synchronized static native void closeImageNative(long ptr);
	private synchronized static native int[] getTileNative(long ptr, int type, int key, int depth, int offset, int i, int w, int h, byte[] palette);	

    static {
        System.loadLibrary("ozfdecoder");
    }
}
