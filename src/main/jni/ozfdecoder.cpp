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

#include <jni.h>
#include <android/log.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <zlib.h>

#define	OZF_STREAM_DEFAULT		0
#define OZF_STREAM_ENCRYPTED	1
#define	OZF_TILE_WIDTH			64
#define	OZF_TILE_HEIGHT			64
#define D1_KEY_CYCLE			0x1A

static unsigned char d1_key[] =
{
	0x2D, 0x4A, 0x43, 0xF1, 0x27, 0x9B, 0x69, 0x4F,
	0x36, 0x52, 0x87, 0xEC, 0x5F, 0x42, 0x53, 0x22,
	0x9E, 0x8B, 0x2D, 0x83, 0x3D, 0xD2, 0x84, 0xBA,
	0xD8, 0x5B, 0x8B, 0xC0
};

typedef struct
{
	long width;
	long height;
	short xtiles;
	short ytiles;

	long palette[256];
} ozf_image_header;

typedef unsigned long DWORD, *PDWORD, *LPDWORD;

template <class T> const T& min ( const T& a, const T& b )
{
  return (a<b)?a:b;
}

extern "C" {
    JNIEXPORT jlong JNICALL Java_com_androzic_map_ozf_OzfDecoder_openImageNative(JNIEnv* env, jclass clazz, jstring path);
    JNIEXPORT void JNICALL Java_com_androzic_map_ozf_OzfDecoder_closeImageNative(JNIEnv* env, jclass clazz, jlong fileptr);
    JNIEXPORT jintArray JNICALL Java_com_androzic_map_ozf_OzfDecoder_getTileNative(JNIEnv* env, jclass clazz, jlong fileptr, jint type, jint key, jint depth, jint offset, jint i, jint w, jint h, jbyteArray p);
};

void ozf_get_tile(FILE*	file, int type, unsigned char key, int encryption_depth, int scale_offset, int i, unsigned char* decompressed);
void Resize_HQ_4ch(unsigned char* src, int w1, int h1, unsigned char* dest, int w2, int h2);

jlong Java_com_androzic_map_ozf_OzfDecoder_openImageNative(JNIEnv* env, jclass clazz, jstring path)
{
    const char* filename_utf8 = env->GetStringUTFChars(path, JNI_FALSE);

	__android_log_print(ANDROID_LOG_INFO, "OZF", "native open image: %s", filename_utf8);

//   	if((*env)->ExceptionOccurred()) 
//   		logstream_write("exception: %s, line: %s", __FILE__, __LINE__);

	FILE* f = fopen(filename_utf8, "rb");

    if (filename_utf8)
    {
        env->ReleaseStringUTFChars(path, filename_utf8);
	}

    jlong ret = (jlong) f;
    return ret;
}

void Java_com_androzic_map_ozf_OzfDecoder_closeImageNative(JNIEnv* env, jclass clazz, jlong fileptr)
{
	__android_log_print(ANDROID_LOG_INFO, "OZF", "native close image");

	FILE* file = (FILE*) fileptr;
	fclose(file);
}

jintArray Java_com_androzic_map_ozf_OzfDecoder_getTileNative(JNIEnv* env, jclass clazz, jlong fileptr, jint type, jint key, jint depth, jint offset, jint i, jint w, jint h, jbyteArray p)
{
	//__android_log_print(ANDROID_LOG_INFO, "OZF", "native get tile\t%d\t%d\t%d\t%d\t%d\t%d\t%d", type, key, depth, offset, i, w, h);

	FILE* file = (FILE*) fileptr;

	unsigned char* tile = (unsigned char*) malloc(OZF_TILE_WIDTH*OZF_TILE_HEIGHT*4);
	unsigned char* data = (unsigned char*) malloc(OZF_TILE_WIDTH*OZF_TILE_HEIGHT);

	if (tile == NULL || data == NULL)
		return NULL;

	// read tile from ozf file
	ozf_get_tile(file, type, (unsigned char) key, depth, offset, i, data);

	// convert to rgb
	int tile_z = OZF_TILE_WIDTH * (OZF_TILE_HEIGHT - 1) * 4;
	int tile_x = 0;

	jbyte* palette = (jbyte *) env->GetPrimitiveArrayCritical(p, (jboolean *)0);

	for(int j = 0; j < OZF_TILE_WIDTH * OZF_TILE_HEIGHT; j++)
	{
		unsigned char c = data[j];

		// flipping image vertical
		//int tile_y = (OZF_TILE_WIDTH - 1) - (j / OZF_TILE_WIDTH);
		//int tile_x = j % OZF_TILE_WIDTH;
		//int tile_z = tile_y * OZF_TILE_WIDTH + tile_x;

		unsigned char r = palette[c*4 + 2];
		unsigned char g = palette[c*4 + 1];
		unsigned char b = palette[c*4 + 0];
		unsigned char a = 255;

		// applying bgr -> argb
		tile[tile_z + 0] = b; // r
		tile[tile_z + 1] = g; // g
		tile[tile_z + 2] = r; // b
		tile[tile_z + 3] = a; // a

		tile_x ++;
		tile_z += 4;
		if (tile_x == OZF_TILE_WIDTH)
		{
			tile_x = 0;
			tile_z -= OZF_TILE_WIDTH * 4 * 2;
		}
	}
	env->ReleasePrimitiveArrayCritical(p, palette, 0);
	free(data);

	// rescale
	if (w != OZF_TILE_WIDTH || h != OZF_TILE_HEIGHT)
	{
		unsigned char* scaled = (unsigned char*) malloc(w * h * 4);
		Resize_HQ_4ch(tile, OZF_TILE_WIDTH, OZF_TILE_HEIGHT, scaled, w, h);
		free(tile);
		tile = scaled;
	}

	if (tile == NULL)
		return NULL;

	jintArray pixels = env->NewIntArray(w * h);

	if (pixels == NULL)
		return NULL;

	jint* ptr = (jint *) env->GetPrimitiveArrayCritical(pixels, (jboolean *)0);

	if (ptr == NULL)
		return NULL;

	int n = w * h * sizeof(int);

	memcpy(ptr, tile, n);
	
	env->ReleasePrimitiveArrayCritical(pixels, ptr, 0);
	
	free(tile);
	
//	if((*env)->ExceptionOccurred()) 
//		logstream_write("exception: %s, line: %s", __FILE__, __LINE__);
		
	return pixels;
}

void ozf_decode1(unsigned char *s, int n, unsigned char initial)
{
	long j;

	for(j = 0; j < n; j++)
	{
		long k = j % D1_KEY_CYCLE;

		unsigned char c = d1_key[k];
		c += initial;
		unsigned char c1 = s[j];
		c ^= c1;
		s[j] = c;
	}
}

int ozf_decompress_tile(Bytef *dest, uLongf* destLen, const Bytef *source, uLong sourceLen)
{
    z_stream stream;
    int err;

    stream.next_in = (Bytef*)source;
    stream.avail_in = (uInt)sourceLen;

    if ((uLong)stream.avail_in != sourceLen)
		return Z_BUF_ERROR;

    stream.next_out = dest;
    stream.avail_out = (uInt)*destLen;

	if ((uLong)stream.avail_out != *destLen)
		return Z_BUF_ERROR;

    stream.zalloc = (alloc_func)0;
    stream.zfree = (free_func)0;

    err = inflateInit(&stream);

	if (err != Z_OK)
		return err;

    err = inflate(&stream, Z_FINISH);

    if (err != Z_STREAM_END)
	{
        inflateEnd(&stream);
        return err == Z_OK ? Z_BUF_ERROR : err;
    }

	*destLen = stream.total_out;

    err = inflateEnd(&stream);

    return err;
}

void ozf_get_tile(FILE*	file, int type, unsigned char key, int encryption_depth, int scale_offset, int i, unsigned char* decompressed)
{
	fseek(file, scale_offset, SEEK_SET);
	fseek(file, sizeof(ozf_image_header), SEEK_CUR);
	fseek(file, i * sizeof(long), SEEK_CUR);

	unsigned long tile_pos, tile_pos1;

	fread(&tile_pos, sizeof(long), 1, file);
	fread(&tile_pos1, sizeof(long), 1, file);

	if (type == OZF_STREAM_ENCRYPTED)
	{
		ozf_decode1((unsigned char*)&tile_pos, sizeof(long), key);
		ozf_decode1((unsigned char*)&tile_pos1, sizeof(long), key);
	}

	unsigned long tilesize = tile_pos1 - tile_pos;

	unsigned char* tile = (unsigned char*) malloc(tilesize);

	if (tile == NULL)
		return;

	fseek(file, tile_pos, SEEK_SET);
	fread(tile, tilesize, 1, file);

	if (type == OZF_STREAM_ENCRYPTED)
	{
		if (encryption_depth == -1)
			ozf_decode1(tile, tilesize, key);
		else
			ozf_decode1(tile, encryption_depth, key);
	}

	if (!(tile[0] == 0x78 && tile[1] == 0xda))  // zlib signature
	{
		__android_log_print(ANDROID_LOG_ERROR, "OZF", "zlib signature verification failed");
		return;
	}

	unsigned long decompressed_size = OZF_TILE_WIDTH * OZF_TILE_HEIGHT;

	long n = ozf_decompress_tile((Bytef*) decompressed, (uLongf*) &decompressed_size, (const Bytef*) tile, (uLong) tilesize);

	free(tile);
}

/*
  http://www.geisswerks.com/ryan/FAQS/resize.html
  Copyright (c) 2008+ Ryan M. Geiss

  The code below performs a fairly-well-optimized high-quality resample 
  (smooth resize) of a 3-channel image that is padded to 4 bytes per 
  pixel.  The pixel format is assumed to be ARGB.  If you want to make 
  it handle an alpha channel, the changes should be very straightforward.
  
  In general, if the image is being enlarged, bilinear interpolation
  is used; if the image is being downsized, all input pixels are weighed
  appropriately to produce the correct result.
  
  In order to be efficient, it actually performs 1 of 4 routines.  First, 
  if you are cutting the image size *exactly* in half (common when generating 
  mipmap levels), it will use a specialized routine to do just that.  There
  are actually two versions of this routine - an MMX one and a non-MMX one.
  It detects if MMX is present and chooses the right one.
  
  If you're not cutting the image perfectly in half, it executes one
  of two general resize routines.  If upsampling (increasing width and height)
  on both X and Y, then it executes a faster resize algorithm that just performs
  a 2x2 bilinear interpolation of the appropriate input pixels, for each output 
  pixel.  
  
  If downsampling on either X or Y (or both), though, the general-purpose 
  routine gets run.  It iterates over every output pixel, and for each one, it 
  iterates over the input pixels that map to that output pixel [there will 
  usually be more than 1 in this case].  Each input pixel is properly weighted
  to produce exactly the right image.  There's a little bit of extra bookkeeping,
  but in general, it's pretty efficient.
  
  Note that on extreme downsizing (2,800 x 2,800 -> 1x1 or greater ratio),
  the colors can overflow.  If you want to fix this lazily, just break
  your resize into two passes.
  
  Also note that when your program exits, or when you are done using this 
  function, you should delete [] g_px1a and g_px1ab if they have been 
  allocated.
  
  I posted this here because this is pretty common code that is a bit of
  a pain to write; I've written it several times over the years, and I really
  don't feel like writing it again.  So - here it is - for my reference, and
  for yours.  Enjoy!
*/

void Resize_HQ_4ch(unsigned char* src, int w1, int h1, unsigned char* dest, int w2, int h2)
{
    // Both buffers must be in ARGB format, and a scanline should be w*4 bytes.

    // NOTE: THIS WILL OVERFLOW for really major downsizing (2800x2800 to 1x1 or more)
    // (2800 ~ sqrt(2^23)) - for a lazy fix, just call this in two passes.

	int* g_px1a    = NULL;
	int  g_px1a_w  = 0;
	int* g_px1ab   = NULL;
	int  g_px1ab_w = 0;

    if (w2*2==w1 && h2*2==h1)
    {
        // perfect 2x2:1 case - faster code
        // (especially important because this is common for generating low (large) mip levels!)
        DWORD *dsrc  = (DWORD*)src;
        DWORD *ddest = (DWORD*)dest;

		DWORD remainder = 0;
		int i = 0;
		for (int y2=0; y2<h2; y2++)
		{
			int y1 = y2*2;
			DWORD* temp_src = &dsrc[y1*w1];
			for (int x2=0; x2<w2; x2++)
			{
				DWORD xUL = temp_src[0];
				DWORD xUR = temp_src[1];
				DWORD xLL = temp_src[w1];
				DWORD xLR = temp_src[w1 + 1];
				// note: DWORD packing is 0xAARRGGBB

				DWORD redblue = (xUL & 0x00FF00FF) + (xUR & 0x00FF00FF) + (xLL & 0x00FF00FF) + (xLR & 0x00FF00FF) + (remainder & 0x00FF00FF);
				DWORD green   = (xUL & 0x0000FF00) + (xUR & 0x0000FF00) + (xLL & 0x0000FF00) + (xLR & 0x0000FF00) + (remainder & 0x0000FF00);
				// redblue = 000000rr rrrrrrrr 000000bb bbbbbbbb
				// green   = xxxxxx00 000000gg gggggggg 00000000
				remainder =  (redblue & 0x00030003) | (green & 0x00000300);
				ddest[i++]   = ((redblue & 0x03FC03FC) | (green & 0x0003FC00)) >> 2;

				temp_src += 2;
			}
		}
    }
    else
    {
        // arbitrary resize.
        unsigned int *dsrc  = (unsigned int *)src;
        unsigned int *ddest = (unsigned int *)dest;

        bool bUpsampleX = (w1 < w2);
        bool bUpsampleY = (h1 < h2);

        // If too many input pixels map to one output pixel, our 32-bit accumulation values
        // could overflow - so, if we have huge mappings like that, cut down the weights:
        //    256 max color value
        //   *256 weight_x
        //   *256 weight_y
        //   *256 (16*16) maximum # of input pixels (x,y) - unless we cut the weights down...
        int weight_shift = 0;
        float source_texels_per_out_pixel = (   (w1/(float)w2 + 1)
                                              * (h1/(float)h2 + 1)
                                            );
        float weight_per_pixel = source_texels_per_out_pixel * 256 * 256;  //weight_x * weight_y
        float accum_per_pixel = weight_per_pixel*256; //color value is 0-255
        float weight_div = accum_per_pixel / 4294967000.0f;
        if (weight_div > 1)
            weight_shift = (int)ceilf( logf((float)weight_div)/logf(2.0f) );
        weight_shift = min(15, weight_shift);  // this could go to 15 and still be ok.

        float fh = 256*h1/(float)h2;
        float fw = 256*w1/(float)w2;

        if (bUpsampleX && bUpsampleY)
        {
            // faster to just do 2x2 bilinear interp here

            // cache x1a, x1b for all the columns:
            // ...and your OS better have garbage collection on process exit :)
            if (g_px1a_w < w2)
            {
                if (g_px1a) delete [] g_px1a;
                g_px1a = new int[w2*2 * 1];
                g_px1a_w = w2*2;
            }
            for (int x2=0; x2<w2; x2++)
            {
                // find the x-range of input pixels that will contribute:
                int x1a = (int)(x2*fw);
                x1a = min(x1a, 256*(w1-1) - 1);
                g_px1a[x2] = x1a;
            }

            // FOR EVERY OUTPUT PIXEL
            for (int y2=0; y2<h2; y2++)
            {
                // find the y-range of input pixels that will contribute:
                int y1a = (int)(y2*fh);
                y1a = min(y1a, 256*(h1-1) - 1);
                int y1c = y1a >> 8;

                unsigned int *ddest = &((unsigned int *)dest)[y2*w2 + 0];

                for (int x2=0; x2<w2; x2++)
                {
                    // find the x-range of input pixels that will contribute:
                    int x1a = g_px1a[x2];//(int)(x2*fw);
                    int x1c = x1a >> 8;

                    unsigned int *dsrc2 = &dsrc[y1c*w1 + x1c];

                    // PERFORM BILINEAR INTERPOLATION on 2x2 pixels
                    unsigned int r=0, g=0, b=0, a=0;
                    unsigned int weight_x = 256 - (x1a & 0xFF);
                    unsigned int weight_y = 256 - (y1a & 0xFF);
                    for (int y=0; y<2; y++)
                    {
                        for (int x=0; x<2; x++)
                        {
                            unsigned int c = dsrc2[x + y*w1];
                            unsigned int r_src = (c    ) & 0xFF;
                            unsigned int g_src = (c>> 8) & 0xFF;
                            unsigned int b_src = (c>>16) & 0xFF;
                            unsigned int w = (weight_x * weight_y) >> weight_shift;
                            r += r_src * w;
                            g += g_src * w;
                            b += b_src * w;
                            weight_x = 256 - weight_x;
                        }
                        weight_y = 256 - weight_y;
                    }

                    unsigned int c = ((r>>16)) | ((g>>8) & 0xFF00) | (b & 0xFF0000);
                    *ddest++ = c;//ddest[y2*w2 + x2] = c;
                }
            }
        }
        else
        {
            // cache x1a, x1b for all the columns:
            // ...and your OS better have garbage collection on process exit :)
            if (g_px1ab_w < w2)
            {
                if (g_px1ab) delete [] g_px1ab;
                g_px1ab = new int[w2*2 * 2];
                g_px1ab_w = w2*2;
            }
            for (int x2=0; x2<w2; x2++)
            {
                // find the x-range of input pixels that will contribute:
                int x1a = (int)((x2  )*fw);
                int x1b = (int)((x2+1)*fw);
                if (bUpsampleX) // map to same pixel -> we want to interpolate between two pixels!
                    x1b = x1a + 256;
                x1b = min(x1b, 256*w1 - 1);
                g_px1ab[x2*2+0] = x1a;
                g_px1ab[x2*2+1] = x1b;
            }

            // FOR EVERY OUTPUT PIXEL
            for (int y2=0; y2<h2; y2++)
            {
                // find the y-range of input pixels that will contribute:
                int y1a = (int)((y2  )*fh);
                int y1b = (int)((y2+1)*fh);
                if (bUpsampleY) // map to same pixel -> we want to interpolate between two pixels!
                    y1b = y1a + 256;
                y1b = min(y1b, 256*h1 - 1);
                int y1c = y1a >> 8;
                int y1d = y1b >> 8;

                for (int x2=0; x2<w2; x2++)
                {
                    // find the x-range of input pixels that will contribute:
                    int x1a = g_px1ab[x2*2+0];    // (computed earlier)
                    int x1b = g_px1ab[x2*2+1];    // (computed earlier)
                    int x1c = x1a >> 8;
                    int x1d = x1b >> 8;

                    // ADD UP ALL INPUT PIXELS CONTRIBUTING TO THIS OUTPUT PIXEL:
                    unsigned int r=0, g=0, b=0, a=0;
                    for (int y=y1c; y<=y1d; y++)
                    {
                        unsigned int weight_y = 256;
                        if (y1c != y1d)
                        {
                            if (y==y1c)
                                weight_y = 256 - (y1a & 0xFF);
                            else if (y==y1d)
                                weight_y = (y1b & 0xFF);
                        }

                        unsigned int *dsrc2 = &dsrc[y*w1 + x1c];
                        for (int x=x1c; x<=x1d; x++)
                        {
                            unsigned int weight_x = 256;
                            if (x1c != x1d)
                            {
                                if (x==x1c)
                                    weight_x = 256 - (x1a & 0xFF);
                                else if (x==x1d)
                                    weight_x = (x1b & 0xFF);
                            }

                            unsigned int c = *dsrc2++;//dsrc[y*w1 + x];
                            unsigned int r_src = (c    ) & 0xFF;
                            unsigned int g_src = (c>> 8) & 0xFF;
                            unsigned int b_src = (c>>16) & 0xFF;
                            unsigned int w = (weight_x * weight_y) >> weight_shift;
                            r += r_src * w;
                            g += g_src * w;
                            b += b_src * w;
                            a += w;
                        }
                    }

                    // write results
                    unsigned int c = ((r/a)) | ((g/a)<<8) | ((b/a)<<16);
                    *ddest++ = c;//ddest[y2*w2 + x2] = c;
                }
            }
        }
    }

	if (g_px1a) delete [] g_px1a;
	if (g_px1ab) delete [] g_px1ab;
}
