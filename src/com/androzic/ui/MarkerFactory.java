package com.androzic.ui;

import java.io.File;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.support.annotation.Nullable;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Marker;

public class MarkerFactory
{
	@Nullable
	public static Bitmap getIcon(Androzic application, String name)
	{
		Bitmap markerIcon = BitmapFactory.decodeFile(application.markerPath + File.separator + name);
		return markerIcon;
	}

	@Nullable
	public static Marker getMarker(Androzic application, String name, int color)
	{
		return getMarker(application, name, color, getIcon(application, name));
	}

	@Nullable
	public static Marker getMarker(Androzic application, String name, int color, @Nullable Bitmap icon)
	{
		if (icon == null)
			return null;
		
		Marker marker = new Marker(name);

		Resources resources = application.getResources();
		int border = (int) (2 * resources.getDisplayMetrics().density);
		Bitmap pin = BitmapFactory.decodeResource(resources, R.drawable.marker_pin_1);
		marker.image = Bitmap.createBitmap(pin.getWidth(), pin.getHeight(), Bitmap.Config.ARGB_8888);

		Paint paint = new Paint();
		paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
		
		Canvas bc = new Canvas(marker.image);
		bc.drawBitmap(pin, 0f, 0f, paint);

		marker.anchorX = marker.image.getWidth() / 2;
		marker.anchorY = marker.image.getHeight();

		int width = marker.image.getWidth() - border * 2;

		int iconWidth = icon.getWidth();
		int iconHeight = icon.getHeight();
		int iconSize = iconWidth > iconHeight ? iconWidth : iconHeight;

		Matrix matrix = new Matrix();

		if (iconSize > width)
		{
			float scale = (float) (1. * width / iconSize);
			matrix.postScale(scale, scale);
			iconWidth = (int) (iconWidth * scale);
			iconHeight = (int) (iconHeight * scale);
		}

		matrix.postTranslate(border + (width - iconWidth) / 2, border + (width - iconHeight) / 2);
		bc.drawBitmap(icon, matrix, null);
		return marker;
	}
}
