/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic;

import java.lang.ref.WeakReference;

import org.metalev.multitouch.controller.MultiTouchController;
import org.metalev.multitouch.controller.MultiTouchController.MultiTouchObjectCanvas;
import org.metalev.multitouch.controller.MultiTouchController.PointInfo;
import org.metalev.multitouch.controller.MultiTouchController.PositionAndScale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewConfiguration;
import android.widget.Toast;

import com.androzic.map.Map;
import com.androzic.overlay.MapOverlay;
import com.androzic.overlay.OverlayManager;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class MapView extends SurfaceView implements SurfaceHolder.Callback, MultiTouchObjectCanvas<Object>
{
	private static final String TAG = "MapView";
	
	private static final int REFRESH_MESSAGE = 1;

	private static final float MAX_ROTATION_SPEED = 20f;
	private static final float INC_ROTATION_SPEED = 2f;
	private static final float MAX_SHIFT_SPEED = 20f;
	private static final float INC_SHIFT_SPEED = 2f;
	
	private static final int VIEWPORT_EXCESS = 64;

	private static final int GESTURE_THRESHOLD_DP = (int) (ViewConfiguration.get(Androzic.getApplication()).getScaledTouchSlop() * 3);
	private static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

	private static final int SCALE_MOVE_DELAY = 2 * 1000000000;

	private int vectorType = 1;
	private int vectorMultiplier = 10;
	private boolean strictUnfollow = true;
	private boolean loadBestMap = true;
	private int bestMapInterval = 5000; // 5 seconds
	private long drawPeriod = 200 * 1000000; // 200 milliseconds
	/**
	 * True when there is a valid location
	 */
	private boolean isFixed = false;
	/**
	 * True when there is a valid bearing
	 */
	private boolean isMoving = false;
	/**
	 * True when map moves with location cursor
	 */
	private boolean isFollowing = false;

	private long lastBestMap = 0;
	private boolean bestMapEnabled = true;

	private GestureHandler tapHandler;
	private long firstTapTime = 0;
	private boolean wasDoubleTap = false;
	private MotionEvent upEvent = null;
	private int penX = 0;
	private int penY = 0;
	private int penOX = 0;
	private int penOY = 0;
	private int lookAhead = 0;
	private float lookAheadC = 0;
	private float lookAheadS = 0;
	private float lookAheadSS = 0;
	private int lookAheadPst = 0;

	private float lookAheadB = 0;
	private float smoothB = 0;
	private float smoothBS = 0;
	private double mpp = 0;
	private int vectorLength = 0;
	private int proximity = 0;

	// cursor
	private Drawable movingCursor = null;
	private Paint crossPaint = null;
	private Paint pointerPaint = null;
	private PorterDuffColorFilter active = null;

	// scale bar
	private int scaleBarMeters;
	private int scaleBarWidth;
	private Paint scaleLinePaint;
	private Paint scaleTextPaint;
	private Paint scaleFillPaint;
	private boolean drawScaleBackground;
	private long lastScaleMove = 0;
	private int lastScalePos = 1;

	private Androzic application;
	private MapHolder mapHolder;

	private SurfaceHolder cachedHolder;
	private DrawingThread drawingThread;

	private MultiTouchController<Object> multiTouchController;
	private float pinch = 0;
	private float scale = 1;
	private boolean wasMultitouch = false;

	private Viewport currentViewport;
	
	private boolean recreateBuffers;
	private Bitmap bufferBitmap;
	private Bitmap bufferBitmapTmp;
	private Handler renderHandler;
	private Viewport renderViewport;

	public class Viewport
	{
		public double[] mapCenter;
		public int[] mapCenterXY;
		public double[] location;
		public int[] locationXY;
		public int width;
		public int height;
		public Rect viewArea;

		public int[] lookAheadXY;

		public float bearing;
		public float speed = 0;

		public Viewport()
		{
			mapCenter = new double[2];
			mapCenterXY = new int[2];
			location = new double[] {Double.NaN, Double.NaN};
			locationXY = new int[2];
			
			width = 0;
			height = 0;
			
			viewArea = new Rect();
			
			bearing = 0;
			speed = 0;
			
			lookAheadXY = new int[] { 0, 0 };
		}

		private Viewport copy()
		{
			Viewport copy = new Viewport();
			copy.mapCenter[0] = mapCenter[0];
			copy.mapCenter[1] = mapCenter[1];
			copy.mapCenterXY[0] = mapCenterXY[0];
			copy.mapCenterXY[1] = mapCenterXY[1];
			copy.location[0] = location[0];
			copy.location[1] = location[1];
			copy.locationXY[0] = locationXY[0];
			copy.locationXY[1] = locationXY[1];
			copy.width = width;
			copy.height = height;
			copy.viewArea = new Rect(viewArea);
			copy.bearing = bearing;
			copy.speed = speed;
			copy.lookAheadXY[0] = lookAheadXY[0];
			copy.lookAheadXY[1] = lookAheadXY[1];
			return copy;
		}
	}

	public MapView(Context context)
	{
		super(context);
		setWillNotDraw(false);
	}

	public MapView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setWillNotDraw(false);
	}

	public MapView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		setWillNotDraw(false);
	}

	public void initialize(Androzic application, MapHolder holder)
	{
		this.application = application;
		this.mapHolder = holder;

		currentViewport = new Viewport();
		renderHandler = new Handler(application.getRenderingThreadLooper());
		recreateBuffers = false;

		getHolder().addCallback(this);

		Resources resources = getResources();
		
		scaleLinePaint = new Paint();
		scaleLinePaint.setAntiAlias(false);
		scaleLinePaint.setStrokeWidth(2);
		scaleLinePaint.setStyle(Paint.Style.STROKE);
		scaleLinePaint.setColor(resources.getColor(R.color.scalebar));
        scaleTextPaint = new Paint();
        scaleTextPaint.setAntiAlias(true);
        scaleTextPaint.setStrokeWidth(2);
        scaleTextPaint.setStyle(Paint.Style.FILL);
        scaleTextPaint.setTextAlign(Align.CENTER);
        scaleTextPaint.setTextSize(16);
        scaleTextPaint.setTypeface(Typeface.SANS_SERIF);
        scaleTextPaint.setColor(resources.getColor(R.color.scalebar));
		scaleFillPaint = new Paint();
		scaleFillPaint.setAntiAlias(false);
		scaleFillPaint.setStrokeWidth(1);
		scaleFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		scaleFillPaint.setColor(resources.getColor(R.color.scalebarbg));

		drawScaleBackground = true;
		
    	lastScaleMove = 0;
    	lastScalePos = 1;

		crossPaint = new Paint();
		crossPaint.setAntiAlias(true);
		crossPaint.setStrokeWidth(3);
		crossPaint.setStyle(Paint.Style.STROKE);
		crossPaint.setColor(resources.getColor(R.color.mapcross));

		pointerPaint = new Paint();
		pointerPaint.setAntiAlias(true);
		pointerPaint.setStrokeWidth(3);
		pointerPaint.setStyle(Paint.Style.STROKE);
		pointerPaint.setColor(resources.getColor(R.color.cursor));

		if (application.customCursor != null)
		{
			movingCursor = application.customCursor;
		}
		else
		{
			movingCursor = resources.getDrawable(R.drawable.moving);
		}
		movingCursor.setBounds(-movingCursor.getIntrinsicWidth() / 2, 0, movingCursor.getIntrinsicWidth() / 2, movingCursor.getIntrinsicHeight());

		multiTouchController = new MultiTouchController<Object>(this, false);
		tapHandler = new GestureHandler(this);

		Log.d(TAG, "Map initialize");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		Log.i(TAG, "surfaceChanged(" + width + "," + height + ")");
		currentViewport.width = getWidth() + VIEWPORT_EXCESS * 2;
		currentViewport.height = getHeight() + VIEWPORT_EXCESS * 2;
		calculateScaleBar();
		recreateBuffers = true;
		setLookAhead(lookAheadPst);
		refreshBuffer();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		Log.i(TAG, "surfaceCreated(" + holder + ")");
		currentViewport.width = getWidth();
		currentViewport.height = getHeight();
		calculateScaleBar();
		recreateBuffers = true;
		refreshBuffer();
		
		drawingThread = new DrawingThread(holder, this);
		drawingThread.setRunning(true);
		drawingThread.start();
		cachedHolder = null;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		Log.i(TAG, "surfaceDestroyed(" + holder + ")");
		boolean retry = true;
		drawingThread.setRunning(false);
		while (retry)
		{
			try
			{
				drawingThread.join();
				retry = false;
			}
			catch (InterruptedException e)
			{
			}
		}
		if (bufferBitmap != null)
			bufferBitmap.recycle();
		if (bufferBitmapTmp != null)
			bufferBitmapTmp.recycle();
		bufferBitmap = null;
		bufferBitmapTmp = null;
	}

	/**
	 * Pauses map drawing
	 */
	public void pause()
	{
		if (cachedHolder != null || drawingThread == null)
			return;
		cachedHolder = drawingThread.surfaceHolder;
		surfaceDestroyed(cachedHolder);
	}

	/**
	 * Resumes map drawing
	 */
	public void resume()
	{
		if (cachedHolder != null)
			surfaceCreated(cachedHolder);
	}

	/**
	 * Checks if map drawing is paused
	 */
	public boolean isPaused()
	{
		return cachedHolder != null;
	}

	class DrawingThread extends Thread
	{
		private boolean runFlag = false;
		private SurfaceHolder surfaceHolder;
		private MapView mapView;
		private long prevTime;

		public DrawingThread(SurfaceHolder surfaceHolder, MapView mapView)
		{
			this.surfaceHolder = surfaceHolder;
			this.mapView = mapView;
			prevTime = System.nanoTime();
		}

		public void setRunning(boolean run)
		{
			runFlag = run;
		}

		@Override
		public void run()
		{
			Canvas canvas;
			while (runFlag)
			{
				// limit the frame rate to maximum 5 frames per second (200 milliseconds)
				long elapsedTime = System.nanoTime() - prevTime;
				if (elapsedTime < drawPeriod)
				{
					try
					{
						Thread.sleep((drawPeriod - elapsedTime) / 1000000);
					}
					catch (InterruptedException e)
					{
					}
				}
				prevTime = System.nanoTime();
				canvas = null;
				try
				{
					canvas = surfaceHolder.lockCanvas();
					drawPeriod = 1000000 * (mapView.calculateLookAhead() ? 50 : 200);
					if (canvas != null)
						mapView.doDraw(canvas);
				}
				finally
				{
					if (canvas != null)
					{
						surfaceHolder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}
	}

	protected void doDraw(Canvas canvas)
	{
		canvas.translate(-VIEWPORT_EXCESS, -VIEWPORT_EXCESS);
		Matrix matrix = new Matrix();
		
		boolean scaled = scale > 1.1 || scale < 0.9;
		if (scaled)
		{
			float dx = currentViewport.width * (1 - scale) / 2;
			float dy = currentViewport.height * (1 - scale) / 2;
			canvas.translate(dx, dy);
			matrix.postScale(scale, scale);
		}
		
		canvas.drawARGB(255, 255, 255, 255);
		
		synchronized (this)
		{
			if (bufferBitmap != null && !bufferBitmap.isRecycled())
			{
				// Difference between current and buffer map center
				int mcXdiff = renderViewport.mapCenterXY[0] - currentViewport.mapCenterXY[0];
				int mcYdiff = renderViewport.mapCenterXY[1] - currentViewport.mapCenterXY[1];
				// Difference between current and buffer look ahead
				int laXdiff = renderViewport.lookAheadXY[0] - currentViewport.lookAheadXY[0];
				int laYdiff = renderViewport.lookAheadXY[1] - currentViewport.lookAheadXY[1];
				// Adjust buffer bitmap position
				matrix.postTranslate(mcXdiff - laXdiff, mcYdiff - laYdiff);
				// Draw buffer bitmap
				canvas.drawBitmap(bufferBitmap, matrix, null);
			}
		}

		int cx = currentViewport.width / 2;
		int cy = currentViewport.height / 2;

		if (scaled)
			return;

		// Draw scale bar
		if (mpp > 0)
		{
			final int x2 = scaleBarWidth * 2;
			final int x3 = scaleBarWidth * 3;
			final int xd2 = scaleBarWidth / 2;
			final int xd4 = scaleBarWidth / 4;
			
			int scaleX = VIEWPORT_EXCESS;
			int scaleY = VIEWPORT_EXCESS;
			int cty = -10;
	
			int pos;
			if (currentViewport.bearing >= 0 && currentViewport.bearing < 90)
				pos = 1;
			else if (currentViewport.bearing >= 90 && currentViewport.bearing < 180)
				pos = 2;
			else if (currentViewport.bearing >= 180 && currentViewport.bearing < 270)
				pos = 3;
			else
				pos = 4;
	
			if (pos != lastScalePos)
			{
				long now = System.nanoTime();
				if (lastScaleMove == 0)
				{
					pos = lastScalePos;
					lastScaleMove = now;
				}
				else if (now > lastScaleMove + SCALE_MOVE_DELAY)
				{
					lastScalePos = pos;
					lastScaleMove = 0;
				}
				else
				{
					pos = lastScalePos;
				}
			}
	
			if (pos == 1)
			{
				scaleX += 30;
				scaleY += currentViewport.viewArea.bottom - 30;
			}
			else if (pos == 2)
			{
				scaleX += 30;
				scaleY += currentViewport.viewArea.top + 10;
				cty = 30;
			}
			else if (pos == 3)
			{
				scaleX += currentViewport.viewArea.right - x3 - 40;
				scaleY += currentViewport.viewArea.top + 10;
				cty = 30;
			}
			else
			{
				scaleX += currentViewport.viewArea.right - x3 - 40;
				scaleY += currentViewport.viewArea.bottom - 30;
			}
	
			int t = 2000;
			if (scaleBarMeters <= t && scaleBarMeters * 2 > t)
				t = scaleBarMeters * 3;
			String[] d = StringFormatter.distanceC(scaleBarMeters, t);
			String d2 = StringFormatter.distanceH(scaleBarMeters*2, t);
			
			if (drawScaleBackground)
			{
				Rect rect = new Rect();
				scaleTextPaint.getTextBounds(d[0], 0, d[0].length(), rect);
				int htw = rect.width() / 2;
				scaleTextPaint.getTextBounds(d2, 0, d2.length(), rect);
				int httw = rect.width() / 2;
				int th = rect.height();
				int bt = cty > 0 ? scaleY : scaleY + cty - th;
				int bb = cty > 0 ? scaleY + cty : scaleY + 10;
				rect = new Rect(scaleX-htw, bt, scaleX+x3+httw, bb);
				rect.inset(-2, -2);
				canvas.drawRect(rect, scaleFillPaint);
			}
	
			canvas.drawLine(scaleX, scaleY, scaleX+x3, scaleY, scaleLinePaint);
			canvas.drawLine(scaleX, scaleY+10, scaleX+x3, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX, scaleY, scaleX, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+x3, scaleY, scaleX+x3, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+scaleBarWidth, scaleY, scaleX+scaleBarWidth, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+x2, scaleY, scaleX+x2, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+scaleBarWidth, scaleY+5, scaleX+x2, scaleY+5, scaleLinePaint);
			canvas.drawLine(scaleX, scaleY+5, scaleX+xd4, scaleY+5, scaleLinePaint);
			canvas.drawLine(scaleX+xd2, scaleY+5, scaleX+xd2+xd4, scaleY+5, scaleLinePaint);
			canvas.drawLine(scaleX+xd4, scaleY, scaleX+xd4, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+xd2, scaleY, scaleX+xd2, scaleY+10, scaleLinePaint);
			canvas.drawLine(scaleX+xd2+xd4, scaleY, scaleX+xd2+xd4, scaleY+10, scaleLinePaint);
	
			canvas.drawText("0", scaleX+scaleBarWidth, scaleY+cty, scaleTextPaint);
			canvas.drawText(d[0], scaleX+x2, scaleY+cty, scaleTextPaint);
			canvas.drawText(d[0], scaleX, scaleY+cty, scaleTextPaint);
			canvas.drawText(d2, scaleX+x3, scaleY+cty, scaleTextPaint);
		}

		canvas.translate(currentViewport.lookAheadXY[0] + cx, currentViewport.lookAheadXY[1] + cy);

		// draw cursor (it is always topmost)
		if (isMoving)
		{
			int sx = currentViewport.locationXY[0] - currentViewport.mapCenterXY[0];
			int sy = currentViewport.locationXY[1] - currentViewport.mapCenterXY[1];
			
			canvas.save();
			canvas.translate(sx, sy);
			canvas.rotate(currentViewport.bearing, 0, 0);
			movingCursor.draw(canvas);
			if (isFixed)
				canvas.drawLine(0, 0, 0, -vectorLength, pointerPaint);
			canvas.restore();

			sx += cx;
			sy += cy;

			// Draw overflow bearing triangle
			if (sx < VIEWPORT_EXCESS || sy < VIEWPORT_EXCESS || sx > currentViewport.width - VIEWPORT_EXCESS || sy > currentViewport.height - VIEWPORT_EXCESS)
			{
				canvas.save();
				double bearing = Geo.bearing(currentViewport.mapCenter[0], currentViewport.mapCenter[1], currentViewport.location[0], currentViewport.location[1]);
				canvas.rotate((float) bearing, 0, 0);
				canvas.drawLine(-10, -50, 0, -70, pointerPaint);
				canvas.drawLine(0, -70, 10, -50, pointerPaint);
				canvas.drawLine(-10, -50, 10, -50, pointerPaint);
				canvas.restore();
			}
		}

		// Draw map center cross
		if (!isFollowing)
		{
			canvas.drawCircle(0, 0, 1, crossPaint);
			canvas.drawCircle(0, 0, 40, crossPaint);
			canvas.drawLine(20, 0, 120, 0, crossPaint);
			canvas.drawLine(-20, 0, -120, 0, crossPaint);
			canvas.drawLine(0, 20, 0, 120, crossPaint);
			canvas.drawLine(0, -20, 0, -120, crossPaint);
		}
	}
	
	public void refreshMap()
	{
		refreshBuffer();
	}
	
	private void refreshBuffer()
	{
		if (!renderHandler.hasMessages(REFRESH_MESSAGE))
		{
			Message msg = Message.obtain(renderHandler, new Runnable() {
				@Override
				public void run()
				{
					refreshBufferInternal();
				}
			});
			msg.what = REFRESH_MESSAGE;
			renderHandler.sendMessage(msg);
		}
	}

	private void refreshBufferInternal()
	{
		Log.i(TAG, "refreshBufferInternal("+currentViewport.width+","+currentViewport.height+")");

		if (currentViewport.width == 0 || currentViewport.height == 0)
			return;

		if (recreateBuffers || bufferBitmapTmp == null || bufferBitmapTmp.isRecycled())
		{
			synchronized (this)
			{
				if (recreateBuffers && bufferBitmap != null)
				{
					Bitmap t = Bitmap.createBitmap(currentViewport.width, currentViewport.height, Bitmap.Config.RGB_565);
					if (t != bufferBitmap)
						bufferBitmap.recycle();
					bufferBitmap = t;
				}
				if (bufferBitmapTmp != null)
					bufferBitmapTmp.recycle();
				bufferBitmapTmp = Bitmap.createBitmap(currentViewport.width, currentViewport.height, Bitmap.Config.RGB_565);
				recreateBuffers = false;
			}
		}
		
		Canvas canvas = new Canvas(bufferBitmapTmp);
		Viewport viewport = currentViewport.copy();
		
		canvas.drawRGB(0xFF, 0xFF, 0xFF);

		int cx = viewport.width / 2;
		int cy = viewport.height / 2;

		// canvas.rotate(-bearing, lookAheadXY[0] + cx, lookAheadXY[1] + cy);
		application.drawMap(viewport, loadBestMap, canvas);

		canvas.translate(viewport.lookAheadXY[0] + cx, viewport.lookAheadXY[1] + cy);

		// draw overlays
		// FIXME Optimize getOverlays()
		for (MapOverlay mo : application.overlayManager.getOverlays(OverlayManager.ORDER_DRAW_PREFERENCE))
			if (mo.isEnabled())
				mo.onPrepareBuffer(viewport, canvas);
		for (MapOverlay mo : application.overlayManager.getOverlays(OverlayManager.ORDER_DRAW_PREFERENCE))
			if (mo.isEnabled())
				mo.onPrepareBufferEx(viewport, canvas);

		Bitmap t = bufferBitmap;
		synchronized (this)
		{
			renderViewport = viewport;
			bufferBitmap = bufferBitmapTmp;
			bufferBitmapTmp = t;
		}
	}

	public void setLocation(Location loc)
	{
		currentViewport.bearing = loc.getBearing();
		currentViewport.speed = loc.getSpeed();

		currentViewport.location[0] = loc.getLatitude();
		currentViewport.location[1] = loc.getLongitude();
		application.getXYbyLatLon(currentViewport.location[0], currentViewport.location[1], currentViewport.locationXY);

		float turn = lookAheadB - currentViewport.bearing;
		if (Math.abs(turn) > 180)
		{
			turn = turn - Math.signum(turn) * 360;
		}
		if (Math.abs(turn) > 10)
			lookAheadB = currentViewport.bearing;

		long lastLocationMillis = loc.getTime();

		if (isFollowing)
		{
			boolean newMap = false;
			if (bestMapEnabled && bestMapInterval > 0 && lastLocationMillis - lastBestMap >= bestMapInterval)
			{
				newMap = application.setMapCenter(currentViewport.location[0], currentViewport.location[1], true, false, loadBestMap);
				lastBestMap = lastLocationMillis;
			}
			else
			{
				newMap = application.setMapCenter(currentViewport.location[0], currentViewport.location[1], true, false, false);
				if (newMap)
					loadBestMap = bestMapEnabled;
			}
			if (newMap)
				updateMapInfo();
			updateMapCenter();
		}
		calculateVectorLength();
	}

	/**
	 * Clears current location from map.
	 */
	public void clearLocation()
	{
		setFollowingThroughContext(false);
		currentViewport.location[0] = Double.NaN;
		currentViewport.location[1] = Double.NaN;
		currentViewport.bearing = 0;
		currentViewport.speed = 0;
		calculateVectorLength();
	}

	public void updateMapInfo()
	{
		scale = 1;
		Map map = application.getCurrentMap();
		if (map == null)
			mpp = 0;
		else
			mpp = map.mpp / map.getZoom();
		calculateVectorLength();
		calculateScaleBar();
		application.overlayManager.notifyOverlays();
		try
		{
			mapHolder.updateFileInfo();
		}
		finally
		{
		}
	}

	/**
	 * 
	 * @return True if look ahead position was recalculated
	 */
	private boolean calculateLookAhead()
	{
		boolean recalculated = false;
		if (lookAheadC != lookAheadS)
		{
			recalculated = true;

			float diff = lookAheadC - lookAheadS;
			if (Math.abs(diff) > Math.abs(lookAheadSS) * (MAX_SHIFT_SPEED / INC_SHIFT_SPEED))
			{
				lookAheadSS += Math.signum(diff) * INC_SHIFT_SPEED;
				if (Math.abs(lookAheadSS) > MAX_SHIFT_SPEED)
				{
					lookAheadSS = Math.signum(lookAheadSS) * MAX_SHIFT_SPEED;
				}
			}
			else if (Math.signum(diff) != Math.signum(lookAheadSS))
			{
				lookAheadSS += Math.signum(diff) * INC_SHIFT_SPEED * 2;
			}
			else if (Math.abs(lookAheadSS) > INC_SHIFT_SPEED)
			{
				lookAheadSS -= Math.signum(diff) * INC_SHIFT_SPEED * 0.5;
			}
			if (Math.abs(diff) < INC_SHIFT_SPEED)
			{
				lookAheadS = lookAheadC;
				lookAheadSS = 0;
			}
			else
			{
				lookAheadS += lookAheadSS;
			}
		}
		if (lookAheadC > 0 && lookAheadB != smoothB)
		{
			recalculated = true;

			float turn = lookAheadB - smoothB;
			if (Math.abs(turn) > 180)
			{
				turn = turn - Math.signum(turn) * 360;
			}
			if (Math.abs(turn) > Math.abs(smoothBS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED))
			{
				smoothBS += Math.signum(turn) * INC_ROTATION_SPEED;
				if (Math.abs(smoothBS) > MAX_ROTATION_SPEED)
				{
					smoothBS = Math.signum(smoothBS) * MAX_ROTATION_SPEED;
				}
			}
			else if (Math.signum(turn) != Math.signum(smoothBS))
			{
				smoothBS += Math.signum(turn) * INC_ROTATION_SPEED * 2;
			}
			else if (Math.abs(smoothBS) > INC_ROTATION_SPEED)
			{
				smoothBS -= Math.signum(turn) * INC_ROTATION_SPEED * 0.5;
			}
			if (Math.abs(turn) < INC_ROTATION_SPEED)
			{
				smoothB = lookAheadB;
				smoothBS = 0;
			}
			else
			{
				smoothB += smoothBS;
				if (smoothB >= 360)
					smoothB -= 360;
				if (smoothB < 0)
					smoothB = 360 - smoothB;
			}
		}
		if (recalculated)
		{
			currentViewport.lookAheadXY[0] = (int) Math.round(Math.sin(Math.toRadians(smoothB)) * -lookAheadS);
			currentViewport.lookAheadXY[1] = (int) Math.round(Math.cos(Math.toRadians(smoothB)) * lookAheadS);
			refreshBuffer();
		}
		return recalculated;
	}

	private void calculateVectorLength()
	{
		if (mpp == 0)
		{
			vectorLength = 0;
			return;
		}
		switch (vectorType)
		{
			case 0:
				vectorLength = 0;
				break;
			case 1:
				vectorLength = (int) (proximity / mpp);
				break;
			case 2:
				vectorLength = (int) (currentViewport.speed * 60 / mpp);
		}
		vectorLength *= vectorMultiplier;
	}

	private void calculateScaleBar()
	{
		if (mpp == 0)
			return;

		scaleBarMeters = (int) (mpp * (currentViewport.width - VIEWPORT_EXCESS * 2) / 6);
		if (scaleBarMeters < 40)
			scaleBarMeters = scaleBarMeters / 10 * 10;
		else if (scaleBarMeters < 80)
			scaleBarMeters = 50;
		else if (scaleBarMeters < 130)
			scaleBarMeters = 100;
		else if (scaleBarMeters < 300)
			scaleBarMeters = 200;
		else if (scaleBarMeters < 700)
			scaleBarMeters = 500;
		else if (scaleBarMeters < 900)
			scaleBarMeters = 800;
		else if (scaleBarMeters < 1300)
			scaleBarMeters = 1000;
		else if (scaleBarMeters < 3000)
			scaleBarMeters = 2000;
		else if (scaleBarMeters < 7000)
			scaleBarMeters = 5000;
		else if (scaleBarMeters < 10000)
			scaleBarMeters = 8000;
		else if (scaleBarMeters < 80000)
			scaleBarMeters = (int) (Math.ceil(scaleBarMeters * 1. / 10000) * 10000);
		else
			scaleBarMeters = (int) (Math.ceil(scaleBarMeters * 1. / 100000) * 100000);
		
		scaleBarWidth = (int) (scaleBarMeters / mpp);
		
		if (scaleBarWidth > (currentViewport.width - VIEWPORT_EXCESS * 2) / 4)
		{
			scaleBarWidth /= 2;
			scaleBarMeters /= 2;
		}
	}

	public void setMoving(boolean moving)
	{
		isMoving = moving;
		setLookAhead();
	}

	public boolean isMoving()
	{
		return isMoving;
	}

	public void setFollowing(boolean follow)
	{
		if (Double.isNaN(currentViewport.location[0]))
			return;

		if (isFollowing != follow)
		{
			if (follow)
			{
				Toast.makeText(getContext(), R.string.following_enabled, Toast.LENGTH_SHORT).show();
				
				boolean newMap = application.setMapCenter(currentViewport.location[0], currentViewport.location[1], true, true, false);
				if (newMap)
					updateMapInfo();
				
				int dx = currentViewport.locationXY[0] - currentViewport.mapCenterXY[0];
				int dy = currentViewport.locationXY[1] - currentViewport.mapCenterXY[1];
				int sx = dx + currentViewport.width / 2;
				int sy = dy + currentViewport.height / 2;
				
				if (sx >= 0 && sy >= 0 && sx <= currentViewport.width && sy <= currentViewport.height)
				{
					// Location is inside current viewport
					/*
					lookAheadS = (float) Math.sqrt(dx * dx + dy * dy);
					smoothB = (float) Math.toDegrees(Math.atan(1. * dx / dy));
					
					if (dx > 0)
						smoothB = 90 + smoothB;
					if (dx < 0)
						smoothB = 270 + smoothB;
					smoothB -= 90;
					if (smoothB < 0)
						smoothB = 360 - smoothB;
					currentViewport.lookAheadXY[0] = dx;
					currentViewport.lookAheadXY[1] = dy;
					*/
				}
			}
			else
			{
				Toast.makeText(getContext(), R.string.following_disabled, Toast.LENGTH_SHORT).show();
				boolean mapChanged = application.scrollMap(-currentViewport.lookAheadXY[0], -currentViewport.lookAheadXY[1], true);
				if (mapChanged)
					updateMapInfo();
			}
			updateMapCenter();
			isFollowing = follow;
			setLookAhead();
		}
	}

	private void setFollowingThroughContext(boolean follow)
	{
		if (isFollowing != follow)
		{
			try
			{
				mapHolder.setFollowing(!isFollowing);
			}
			catch (Exception e)
			{
				setFollowing(false);
			}
		}
	}

	public boolean isFollowing()
	{
		return isFollowing;
	}

	public void setStrictUnfollow(boolean mode)
	{
		strictUnfollow = mode;
	}

	public boolean getStrictUnfollow()
	{
		return strictUnfollow;
	}

	public void setBestMapEnabled(boolean best)
	{
		bestMapEnabled = best;
	}

	public void suspendBestMap()
	{
		loadBestMap = false;
	}

	public boolean isBestMapEnabled()
	{
		return loadBestMap;
	}

	public void setBestMapInterval(int best)
	{
		bestMapInterval = best;
	}

	public void setFixed(boolean fixed)
	{
		isFixed = fixed;
		movingCursor.setColorFilter(isFixed ? active : null);
		setLookAhead();
	}

	public boolean isFixed()
	{
		return isFixed;
	}

	/**
	 * Set the amount of screen intended for looking ahead
	 * 
	 * @param ahead
	 *            % of the smaller dimension of screen
	 */
	public void setLookAhead(final int ahead)
	{
		lookAheadPst = ahead;
		final int w = getWidth();
		final int h = getHeight();
		final int half = w > h ? h / 2 : w / 2;
		lookAhead = (int) (half * ahead * 0.01);
	}

	/**
	 * Set current look ahead amount based on map conditions
	 */
	private void setLookAhead()
	{
		if (isMoving && isFollowing && isFixed)
		{
			lookAheadC = lookAhead;
		}
		else
		{
			lookAheadC = 0;
			lookAheadS = 0;
			lookAheadSS = 0;
			currentViewport.lookAheadXY[0] = 0;
			currentViewport.lookAheadXY[1] = 0;
		}
	}

	public void setScaleBarColor(final int color)
	{
		scaleLinePaint.setColor(color);
		scaleTextPaint.setColor(color);
	}
	
	public void setScaleBarBackgroundColor(final int color)
	{
		scaleFillPaint.setColor(color);
	}
		
	public void setDrawScaleBarBackground(final boolean draw)
	{
		drawScaleBackground = draw;
	}
		
	public void setCrossColor(final int color)
	{
		crossPaint.setColor(color);
	}

	public void setCursorColor(final int color)
	{
		active = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
		movingCursor.setColorFilter(isFixed ? active : null);
		pointerPaint.setColor(color);
	}

	public void setCursorVector(final int type, final int multiplier)
	{
		vectorType = type;
		vectorMultiplier = multiplier;
	}

	public void setProximity(final int proximity)
	{
		this.proximity = proximity;
	}

	public void updateViewArea(Rect area)
	{
		Log.e(TAG, "updateViewArea()");
		currentViewport.viewArea.set(area);
	}

	public void updateMapCenter()
	{
		currentViewport.mapCenter = application.getMapCenter();
		application.getXYbyLatLon(currentViewport.mapCenter[0], currentViewport.mapCenter[1], currentViewport.mapCenterXY);
		
		refreshBuffer();
		
		try
		{
			mapHolder.updateCoordinates(currentViewport.mapCenter);
		}
		finally
		{
		}
	}

	private final void onDrag(int deltaX, int deltaY)
	{
		application.scrollMap(-deltaX, -deltaY, false);
		updateMapCenter();
	}

	private final void onDragFinished(int deltaX, int deltaY)
	{
		// double rad = Math.toRadians(-bearing);
		// int dX = (int) (deltaX * Math.cos(rad) + deltaY * Math.sin(rad));
		// int dY = (int) (deltaX * Math.sin(-rad) + deltaY * Math.cos(rad));
		boolean mapChanged = application.scrollMap(-deltaX, -deltaY, true);
		if (mapChanged)
			updateMapInfo();
		updateMapCenter();
	}

	private void onSingleTap(int x, int y)
	{
		int mapTapX = x + currentViewport.mapCenterXY[0] - getWidth() / 2;
		int mapTapY = y + currentViewport.mapCenterXY[1] - getHeight() / 2;

		if (isMoving && isFollowing && isFixed)
		{
			mapTapX -= currentViewport.lookAheadXY[0];
			mapTapY -= currentViewport.lookAheadXY[1];
		}

		int dt = GESTURE_THRESHOLD_DP / 2;
		Rect tap = new Rect(mapTapX - dt, mapTapY - dt, mapTapX + dt, mapTapY + dt);
		for (MapOverlay mo : application.overlayManager.getOverlays(OverlayManager.ORDER_SHOW_PREFERENCE))
			if (mo.onSingleTap(upEvent, tap, this))
				break;
	}

	private void onDoubleTap(int x, int y)
	{
		setFollowingThroughContext(!isFollowing);
	}

	private static final int TAP = 1;
	private static final int CANCEL = 2;

	@SuppressLint("HandlerLeak")
	private class GestureHandler extends Handler
	{
		private final WeakReference<MapView> target;
		
		GestureHandler(MapView view)
		{
			super();
			this.target = new WeakReference<MapView>(view);
		}

		@Override
		public void handleMessage(Message msg)
		{
			MapView mapView = target.get();
			if (mapView == null)
				return;
			switch (msg.what)
			{
				case TAP:
					mapView.onSingleTap(penOX, penOY);
					mapView.cancelMotionEvent();
					break;
				case CANCEL:
					mapView.cancelMotionEvent();
					break;
				default:
					throw new RuntimeException("Unknown message " + msg); // never
			}
		}
	}

	private void cancelMotionEvent()
	{
		tapHandler.removeMessages(TAP);
		tapHandler.removeMessages(CANCEL);
		if (upEvent != null)
			upEvent.recycle();
		upEvent = null;
		penX = 0;
		penY = 0;
		penOX = 0;
		penOY = 0;
		firstTapTime = 0;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (multiTouchController.onTouchEvent(event))
		{
			wasMultitouch = true;
			return true;
		}

		int action = event.getAction();

		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
				boolean hadTapMessage = tapHandler.hasMessages(TAP);
				if (hadTapMessage)
					tapHandler.removeMessages(TAP);
				tapHandler.removeMessages(CANCEL);

				if (event.getEventTime() - firstTapTime <= DOUBLE_TAP_TIMEOUT)
				{
					onDoubleTap(penOX, penOY);
					cancelMotionEvent();
					wasDoubleTap = true;
				}
				else
				{
					firstTapTime = event.getDownTime();
				}

				penOX = penX = (int) event.getX();
				penOY = penY = (int) event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				if (!wasMultitouch && (!isFollowing || !strictUnfollow))
				{
					int x = (int) event.getX();
					int y = (int) event.getY();

					int dx = -(penX - x);
					int dy = -(penY - y);

					if (!isFollowing && (Math.abs(dx) > 0 || Math.abs(dy) > 0))
					{
						penX = x;
						penY = y;
						onDrag(dx, dy);
					}
					if (Math.abs(dx) > GESTURE_THRESHOLD_DP || Math.abs(dy) > GESTURE_THRESHOLD_DP)
					{
						if (!strictUnfollow)
							setFollowingThroughContext(false);
					}
				}
				break;
			case MotionEvent.ACTION_UP:
				if (upEvent != null)
					upEvent.recycle();
				upEvent = MotionEvent.obtain(event);

				int x = (int) event.getX();
				int y = (int) event.getY();

				int dx = -penOX + x;
				int dy = -penOY + y;
				if (!wasMultitouch && !wasDoubleTap && Math.abs(dx) < GESTURE_THRESHOLD_DP && Math.abs(dy) < GESTURE_THRESHOLD_DP)
				{
					tapHandler.sendEmptyMessageDelayed(TAP, DOUBLE_TAP_TIMEOUT);
				}
				else if (wasMultitouch || wasDoubleTap)
				{
					wasMultitouch = false;
					wasDoubleTap = false;
					cancelMotionEvent();
				}
				else
				{
					onDragFinished(0, 0);
					tapHandler.sendEmptyMessageDelayed(CANCEL, DOUBLE_TAP_TIMEOUT);
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				wasMultitouch = false;
				wasDoubleTap = false;
				cancelMotionEvent();
				break;
		}

		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_DPAD_CENTER:
				setFollowingThroughContext(!isFollowing);
				return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (!isFollowing || !strictUnfollow)
				{
					int dx = 0;
					int dy = 0;
					switch (keyCode)
					{
						case KeyEvent.KEYCODE_DPAD_DOWN:
							dy -= 10;
							break;
						case KeyEvent.KEYCODE_DPAD_UP:
							dy += 10;
							break;
						case KeyEvent.KEYCODE_DPAD_LEFT:
							dx += 10;
							break;
						case KeyEvent.KEYCODE_DPAD_RIGHT:
							dx -= 10;
							break;
					}
					if (isFollowing)
						setFollowingThroughContext(false);
					onDragFinished(dx, dy);
					return true;
				}
		}

		/*
		 * for (MapOverlay mo : application.getOverlays()) if
		 * (mo.onKeyDown(keyCode, event, this)) return true;
		 */

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		/*
		 * for (MapOverlay mo : application.getOverlays()) if
		 * (mo.onKeyUp(keyCode, event, this)) return true;
		 */

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event)
	{
		int action = event.getAction();
		switch (action)
		{
			case MotionEvent.ACTION_UP:
				setFollowingThroughContext(!isFollowing);
				break;
			case MotionEvent.ACTION_MOVE:
				if (!isFollowing)
				{
					int n = event.getHistorySize();
					final float scaleX = event.getXPrecision();
					final float scaleY = event.getYPrecision();
					int dx = (int) (-event.getX() * scaleX);
					int dy = (int) (-event.getY() * scaleY);
					for (int i = 0; i < n; i++)
					{
						dx += -event.getHistoricalX(i) * scaleX;
						dy += -event.getHistoricalY(i) * scaleY;
					}
					if (Math.abs(dx) > 0 || Math.abs(dy) > 0)
					{
						onDragFinished(dx, dy);
					}
				}
				break;
		}

		/*
		 * for (MapOverlay mo : this.overlays) if (mo.onTrackballEvent(event,
		 * this)) return true;
		 */

		return true;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (state instanceof Bundle)
		{
			Bundle bundle = (Bundle) state;
			super.onRestoreInstanceState(bundle.getParcelable("instanceState"));

			vectorType = bundle.getInt("vectorType");
			vectorMultiplier = bundle.getInt("vectorMultiplier");
			isFollowing = bundle.getBoolean("autoFollow");
			strictUnfollow = bundle.getBoolean("strictUnfollow");
			loadBestMap = bundle.getBoolean("loadBestMap");
			bestMapInterval = bundle.getInt("bestMapInterval");

			isFixed = bundle.getBoolean("isFixed");
			isMoving = bundle.getBoolean("isMoving");
			lastBestMap = bundle.getLong("lastBestMap");

			penX = bundle.getInt("penX");
			penY = bundle.getInt("penY");
			penOX = bundle.getInt("penOX");
			penOY = bundle.getInt("penOY");
			currentViewport.lookAheadXY = bundle.getIntArray("lookAheadXY");
			lookAhead = bundle.getInt("lookAhead");
			lookAheadC = bundle.getFloat("lookAheadC");
			lookAheadS = bundle.getFloat("lookAheadS");
			lookAheadSS = bundle.getFloat("lookAheadSS");
			lookAheadPst = bundle.getInt("lookAheadPst");
			lookAheadB = bundle.getFloat("lookAheadB");
			smoothB = bundle.getFloat("smoothB");
			smoothBS = bundle.getFloat("smoothBS");

			currentViewport.mapCenter = bundle.getDoubleArray("mapCenter");
			currentViewport.location = bundle.getDoubleArray("currentLocation");
			currentViewport.mapCenterXY = bundle.getIntArray("mapCenterXY");
			currentViewport.locationXY = bundle.getIntArray("currentLocationXY");
			currentViewport.bearing = bundle.getFloat("bearing");
			currentViewport.speed = bundle.getFloat("speed");
			mpp = bundle.getDouble("mpp");
			vectorLength = bundle.getInt("vectorLength");
			proximity = bundle.getInt("proximity");

			// TODO Should be somewhere else?
			movingCursor.setColorFilter(isFixed ? active : null);
		}
		else
		{
			super.onRestoreInstanceState(state);
		}
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		Bundle bundle = new Bundle();
		bundle.putParcelable("instanceState", super.onSaveInstanceState());

		bundle.putInt("vectorType", vectorType);
		bundle.putInt("vectorMultiplier", vectorMultiplier);
		bundle.putBoolean("autoFollow", isFollowing);
		bundle.putBoolean("strictUnfollow", strictUnfollow);
		bundle.putBoolean("loadBestMap", loadBestMap);
		bundle.putInt("bestMapInterval", bestMapInterval);

		bundle.putBoolean("isFixed", isFixed);
		bundle.putBoolean("isMoving", isMoving);
		bundle.putLong("lastBestMap", lastBestMap);

		bundle.putInt("penX", penX);
		bundle.putInt("penY", penY);
		bundle.putInt("penOX", penOX);
		bundle.putInt("penOY", penOY);
		bundle.putIntArray("lookAheadXY", currentViewport.lookAheadXY);
		bundle.putInt("lookAhead", lookAhead);
		bundle.putFloat("lookAheadC", lookAheadC);
		bundle.putFloat("lookAheadS", lookAheadS);
		bundle.putFloat("lookAheadSS", lookAheadSS);
		bundle.putInt("lookAheadPst", lookAheadPst);
		bundle.putFloat("lookAheadB", lookAheadB);
		bundle.putFloat("smoothB", smoothB);
		bundle.putFloat("smoothBS", smoothBS);

		bundle.putDoubleArray("mapCenter", currentViewport.mapCenter);
		bundle.putDoubleArray("currentLocation", currentViewport.location);
		bundle.putIntArray("mapCenterXY", currentViewport.mapCenterXY);
		bundle.putIntArray("currentLocationXY", currentViewport.locationXY);
		bundle.putFloat("bearing", currentViewport.bearing);
		bundle.putFloat("speed", currentViewport.speed);
		bundle.putDouble("mpp", mpp);
		bundle.putInt("vectorLength", vectorLength);
		bundle.putInt("proximity", proximity);

		return bundle;
	}

	@Override
	public Object getDraggableObjectAtPoint(PointInfo touchPoint)
	{
		pinch = 0;
		scale = 1;
		return this;
	}

	@Override
	public void getPositionAndScale(Object obj, PositionAndScale objPosAndScaleOut)
	{
	}

	@Override
	public void selectObject(Object obj, PointInfo touchPoint)
	{
		if (obj == null)
		{
			pinch = 0;
			Log.e(TAG, "Scale: " + scale);
			try
			{
				mapHolder.zoomMap(scale);
			}
			finally
			{
			}
		}
	}

	@Override
	public boolean setPositionAndScale(Object obj, PositionAndScale newObjPosAndScale, PointInfo touchPoint)
	{
		if (touchPoint.isDown() && touchPoint.getNumTouchPoints() == 2)
		{
			if (pinch == 0)
			{
				pinch = touchPoint.getMultiTouchDiameterSq();
			}
			scale = touchPoint.getMultiTouchDiameterSq() / pinch;
			if (scale > 1)
			{
				scale = (float) (Math.log10(scale) + 1);
			}
			else
			{
				scale = (float) (1 / (Math.log10(1 / scale) + 1));
			}
		}
		return true;
	}
}
