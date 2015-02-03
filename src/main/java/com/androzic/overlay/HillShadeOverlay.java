package com.androzic.overlay;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.androzic.Androzic;
import com.androzic.map.BaseMap;
import com.androzic.ui.Viewport;

public class HillShadeOverlay extends MapOverlay
{
	private Paint paint;
	private RandomAccessFile demReader;

	public HillShadeOverlay()
	{
		super();
		paint = new Paint();
		paint.setAntiAlias(false);
		paint.setStrokeWidth(1);
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(Color.BLACK);
		try
		{
	    	Androzic application = Androzic.getApplication();
			File demFile = new File(application.dataPath, "N44E034.hgt");
			demReader = new RandomAccessFile(demFile, "r");
			enabled = true;
		}
		catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public synchronized void onMapChanged()
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
	}

	@Override
	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		try
		{
			demReader.close();
			demReader = null;
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//TODO This should go into Application
	private int getHeight(double latitude, double longitude)
	{
		int lat = (int) Math.floor(latitude);
		int lon = (int) Math.floor(longitude);
		int x3 = (int) ((latitude - lat) * 1200);
		int y3 = (int) ((longitude - lon) * 1200);
		if (demReader != null)
		{
			try
			{
				demReader.seek(x3 * 1201 * 2 + y3 * 2);
				int height = demReader.readShort();
				return height;
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		Androzic application = Androzic.getApplication();
		final int[] cxy = viewport.mapCenterXY;
		cxy[0] -= viewport.width / 2;
		cxy[1] -= viewport.height / 2;
    	BaseMap map = application.getCurrentMap();
		int w = viewport.width;
		int h = viewport.height;
    	double[] cll = new double[2];
		for (int x = 0; x < w; x++)
			for (int y = 0; y < h; y++)
			{
		    	map.getLatLonByXY(cxy[0] + x, cxy[1] + y, cll);
		    	int height = getHeight(cll[0], cll[1]);
				paint.setAlpha(height);
		    	c.drawPoint(x - viewport.width / 2, y - viewport.height / 2, paint);
			}
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}

}
