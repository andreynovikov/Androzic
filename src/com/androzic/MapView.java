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
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
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
import com.androzic.util.Geo;

public class MapView extends SurfaceView implements SurfaceHolder.Callback, MultiTouchObjectCanvas<Object>
{
	private static final String TAG = "MapView";

	private static final float MAX_ROTATION_SPEED = 20f;
	private static final float INC_ROTATION_SPEED = 0.5f;
	private static final float MAX_SHIFT_SPEED = 20f;
	private static final float INC_SHIFT_SPEED = 2f;

	private static final int GESTURE_THRESHOLD_DP = (int) (ViewConfiguration.get(Androzic.getApplication()).getScaledTouchSlop() * 3);
	private static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

	private int vectorType = 1;
	private int vectorMultiplier = 10;
	private boolean strictUnfollow = true;
	private boolean hideOnDrag = true;
	private boolean loadBestMap = true;
	private int bestMapInterval = 5000;
	private long drawPeriod = 200;
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
	public int[] lookAheadXY = new int[] { 0, 0 };
	private int lookAhead = 0;
	private float lookAheadC = 0;
	private float lookAheadS = 0;
	private float lookAheadSS = 0;
	private int lookAheadPst = 0;
	public Rect viewArea;

	public double[] mapCenter;
	public int[] mapCenterXY;
	public double[] currentLocation;
	public int[] currentLocationXY;
	private float lookAheadB = 0;
	private float smoothB = 0;
	private float smoothBS = 0;
	public float bearing = 0;
	private float speed = 0;
	private double mpp = 0;
	private int vectorLength = 0;
	private int proximity = 0;

	private Drawable movingCursor = null;
	private Paint crossPaint = null;
	private Paint pointerPaint = null;
	private PorterDuffColorFilter active = null;

	private Androzic application;

	private SurfaceHolder cachedHolder;
	private DrawingThread drawingThread;
	private Object lock = new Object();

	private MultiTouchController<Object> multiTouchController;
	private float pinch = 0;
	private float scale = 1;
	private boolean wasMultitouch = false;

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

	public void initialize(Androzic application)
	{
		this.application = application;

		getHolder().addCallback(this);

		crossPaint = new Paint();
		crossPaint.setAntiAlias(true);
		crossPaint.setStrokeWidth(3);
		crossPaint.setStyle(Paint.Style.STROKE);
		crossPaint.setColor(getResources().getColor(R.color.mapcross));

		pointerPaint = new Paint();
		pointerPaint.setAntiAlias(true);
		pointerPaint.setStrokeWidth(3);
		pointerPaint.setStyle(Paint.Style.STROKE);
		pointerPaint.setColor(getResources().getColor(R.color.cursor));

		Resources resources = getResources();

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

		viewArea = new Rect();
		
		Log.d(TAG, "Map initialize");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		Log.i(TAG, "surfaceChanged(" + width + "," + height + ")");
		synchronized (lock)
		{
			setLookAhead(lookAheadPst);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		Log.i(TAG, "surfaceCreated(" + holder + ")");
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
			prevTime = System.currentTimeMillis();
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
				// limit the frame rate to maximum 5 frames per second (200 miliseconds)
				long elapsedTime = System.currentTimeMillis() - prevTime;
				if (elapsedTime < drawPeriod)
				{
					try
					{
						Thread.sleep(drawPeriod - elapsedTime);
					}
					catch (InterruptedException e)
					{
					}
				}
				prevTime = System.currentTimeMillis();
				canvas = null;
				try
				{
					canvas = surfaceHolder.lockCanvas();
					synchronized (lock)
					{
						drawPeriod = mapView.calculateLookAhead() ? 50 : 200;
						if (canvas != null)
							mapView.doDraw(canvas);
					}
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
		boolean scaled = scale > 1.1 || scale < 0.9;
		if (scaled)
		{
			float dx = getWidth() * (1 - scale) / 2;
			float dy = getHeight() * (1 - scale) / 2;
			canvas.translate(dx, dy);
			Matrix matrix = new Matrix();
			matrix.postScale(scale, scale);
			canvas.concat(matrix);
		}
		canvas.drawRGB(0xFF, 0xFF, 0xFF);

		int cx = getWidth() / 2;
		int cy = getHeight() / 2;

		// canvas.rotate(-bearing, lookAheadXY[0] + cx, lookAheadXY[1] + cy);
		application.drawMap(mapCenter, lookAheadXY, loadBestMap, getWidth(), getHeight(), canvas);

		canvas.translate(lookAheadXY[0] + cx, lookAheadXY[1] + cy);
		// canvas.translate(cx, cy);

		// draw overlays
		if (!scaled && ((penOX == 0 && penOY == 0) || !hideOnDrag))
		{
			// TODO Optimize getOverlays()
			for (MapOverlay mo : application.getOverlays(Androzic.ORDER_DRAW_PREFERENCE))
				mo.onManagedDraw(canvas, this, cx, cy);
		}

		// draw cursor (it is always topmost)
		if (!scaled && currentLocation != null)
		{
			canvas.save();
			canvas.translate(-mapCenterXY[0] + currentLocationXY[0], -mapCenterXY[1] + currentLocationXY[1]);
			if (isMoving)
			{
				canvas.rotate(bearing, 0, 0);
				movingCursor.draw(canvas);
				if (isFixed)
					canvas.drawLine(0, 0, 0, -vectorLength, pointerPaint);
			}
			else
			{
				canvas.drawCircle(0, 0, 1, pointerPaint);
				canvas.drawCircle(0, 0, 40, pointerPaint);
				canvas.drawLine(20, 0, 60, 0, pointerPaint);
				canvas.drawLine(-20, 0, -60, 0, pointerPaint);
				canvas.drawLine(0, 20, 0, 60, pointerPaint);
				canvas.drawLine(0, -20, 0, -60, pointerPaint);
			}
			canvas.restore();

			int sx = currentLocationXY[0] - mapCenterXY[0] + cx;
			int sy = currentLocationXY[1] - mapCenterXY[1] + cy;

			if (sx < 0 || sy < 0 || sx > getWidth() || sy > getHeight())
			{
				canvas.save();
				double bearing = Geo.bearing(mapCenter[0], mapCenter[1], currentLocation[0], currentLocation[1]);
				canvas.rotate((float) bearing, 0, 0);
				canvas.drawLine(-10, -50, 0, -70, pointerPaint);
				canvas.drawLine(0, -70, 10, -50, pointerPaint);
				canvas.drawLine(-10, -50, 10, -50, pointerPaint);
				canvas.restore();
			}

		}

		if (!scaled && !isFollowing)
		{
			canvas.drawCircle(0, 0, 1, crossPaint);
			canvas.drawCircle(0, 0, 40, crossPaint);
			canvas.drawLine(20, 0, 120, 0, crossPaint);
			canvas.drawLine(-20, 0, -120, 0, crossPaint);
			canvas.drawLine(0, 20, 0, 120, crossPaint);
			canvas.drawLine(0, -20, 0, -120, crossPaint);
		}

		if (isMoving && isFollowing && isFixed)
		{
			lookAheadC = lookAhead;
		}
		else
		{
			lookAheadC = 0;
		}
	}

	public void setLocation(Location loc)
	{
		synchronized (lock)
		{
			bearing = loc.getBearing();
			speed = loc.getSpeed();

			if (currentLocation == null)
			{
				currentLocation = new double[2];
			}
			currentLocation[0] = loc.getLatitude();
			currentLocation[1] = loc.getLongitude();
			currentLocationXY = application.getXYbyLatLon(currentLocation[0], currentLocation[1]);

			lookAheadB = Math.round(bearing / 10) * 10;

			long lastLocationMillis = loc.getTime();

			if (isFollowing)
			{
				boolean newMap = false;
				if (bestMapEnabled && bestMapInterval > 0 && lastLocationMillis - lastBestMap >= bestMapInterval)
				{
					newMap = application.setMapCenter(currentLocation[0], currentLocation[1], false, loadBestMap);
					lastBestMap = lastLocationMillis;
				}
				else
				{
					newMap = application.setMapCenter(currentLocation[0], currentLocation[1], false, false);
					if (newMap)
						loadBestMap = bestMapEnabled;
				}
				if (newMap)
					updateMapInfo();
			}
		}
		calculateVectorLength();
	}

	/**
	 * Clears current location from map.
	 */
	public void clearLocation()
	{
		setFollowingThroughContext(false);
		synchronized (lock)
		{
			currentLocation = null;
			bearing = 0;
			speed = 0;
		}
		calculateVectorLength();
	}

	public void updateMapInfo()
	{
		synchronized (lock)
		{
			scale = 1;
			Map map = application.getCurrentMap();
			if (map == null)
				mpp = 0;
			else
				mpp = map.mpp / map.getZoom();
		}
		calculateVectorLength();
		application.notifyOverlays();
		try
		{
			MapActivity androzic = (MapActivity) getContext();
			androzic.updateFileInfo();
		}
		finally
		{
		}
	}

	private boolean calculateLookAhead()
	{
		boolean recalculated = false;
		synchronized (lock)
		{
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
			if (lookAheadB != smoothB)
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
				lookAheadXY[0] = (int) Math.round(Math.sin(Math.toRadians(smoothB)) * -lookAheadS);
				lookAheadXY[1] = (int) Math.round(Math.cos(Math.toRadians(smoothB)) * lookAheadS);
			}
		}
		return recalculated;
	}

	private void calculateVectorLength()
	{
		synchronized (lock)
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
					vectorLength = (int) (speed * 60 / mpp);
			}
			vectorLength *= vectorMultiplier;
		}
	}

	public void setMoving(boolean moving)
	{
		isMoving = moving;
	}

	public boolean isMoving()
	{
		return isMoving;
	}

	public void setFollowing(boolean follow)
	{
		if (currentLocation == null)
			return;

		if (isFollowing != follow)
		{
			synchronized (lock)
			{
				if (follow)
				{
					Toast.makeText(getContext(), R.string.following_enabled, Toast.LENGTH_SHORT).show();
					boolean newMap = application.setMapCenter(currentLocation[0], currentLocation[1], true, false);
					if (newMap)
						updateMapInfo();
				}
				else
				{
					Toast.makeText(getContext(), R.string.following_disabled, Toast.LENGTH_SHORT).show();
				}

				isFollowing = follow;
			}
			update();
		}
	}

	private void setFollowingThroughContext(boolean follow)
	{
		if (isFollowing != follow)
		{
			try
			{
				MapActivity androzic = (MapActivity) getContext();
				androzic.setFollowing(!isFollowing);
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

	public void setHideOnDrag(boolean hide)
	{
		hideOnDrag = hide;
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
		update();
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
		synchronized (lock)
		{
			lookAheadPst = ahead;
			final int w = getWidth();
			final int h = getHeight();
			final int half = w > h ? h / 2 : w / 2;
			lookAhead = (int) (half * ahead * 0.01);
		}
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

	/*
	 * @Override
	 * protected void onSizeChanged(int w, int h, int oldw, int oldh)
	 * {
	 * Log.d(TAG, "Size: " + w + "," + h + "," + oldw + "," + oldh);
	 * super.onSizeChanged(w, h, oldw, oldh);
	 * if ((w != oldw || h != oldh))
	 * {
	 * setLookAhead(lookAheadPst);
	 * updateViewArea(new Rect(0, 0, w, h));
	 * update();
	 * }
	 * }
	 */

	public void updateViewArea(Rect area)
	{
		Log.e(TAG, "updateViewArea()");
		viewArea.set(area);
	}

	public void update()
	{
		synchronized (lock)
		{
			mapCenter = application.getMapCenter();
			mapCenterXY = application.getXYbyLatLon(mapCenter[0], mapCenter[1]);
			if (currentLocation != null)
				currentLocationXY = application.getXYbyLatLon(currentLocation[0], currentLocation[1]);
		}
		try
		{
			MapActivity activity = (MapActivity) getContext();
			activity.updateCoordinates(mapCenter);
		}
		finally
		{
		}
	}

	private final void onDragFinished(int deltaX, int deltaY)
	{
		synchronized (lock)
		{
			// double rad = Math.toRadians(-bearing);
			// int dX = (int) (deltaX * Math.cos(rad) + deltaY * Math.sin(rad));
			// int dY = (int) (deltaX * Math.sin(-rad) + deltaY * Math.cos(rad));
			boolean mapChanged = application.scrollMap(-deltaX, -deltaY);
			if (mapChanged)
				updateMapInfo();
			update();
		}
	}

	private void onSingleTap(int x, int y)
	{
		synchronized (lock)
		{
			int mapTapX = x + mapCenterXY[0] - getWidth() / 2;
			int mapTapY = y + mapCenterXY[1] - getHeight() / 2;

			if (isMoving && isFollowing && isFixed)
			{
				mapTapX -= lookAheadXY[0];
				mapTapY -= lookAheadXY[1];
			}

			int dt = GESTURE_THRESHOLD_DP / 2;
			Rect tap = new Rect(mapTapX - dt, mapTapY - dt, mapTapX + dt, mapTapY + dt);
			for (MapOverlay mo : application.getOverlays(Androzic.ORDER_SHOW_PREFERENCE))
				if (mo.onSingleTap(upEvent, tap, this))
					break;
		}
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
						onDragFinished(dx, dy);
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

				int dx = -(penOX - (int) event.getX());
				int dy = -(penOY - (int) event.getY());
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
			hideOnDrag = bundle.getBoolean("hideOnDrag");
			loadBestMap = bundle.getBoolean("loadBestMap");
			bestMapInterval = bundle.getInt("bestMapInterval");

			isFixed = bundle.getBoolean("isFixed");
			isMoving = bundle.getBoolean("isMoving");
			lastBestMap = bundle.getLong("lastBestMap");

			penX = bundle.getInt("penX");
			penY = bundle.getInt("penY");
			penOX = bundle.getInt("penOX");
			penOY = bundle.getInt("penOY");
			lookAheadXY = bundle.getIntArray("lookAheadXY");
			lookAhead = bundle.getInt("lookAhead");
			lookAheadC = bundle.getFloat("lookAheadC");
			lookAheadS = bundle.getFloat("lookAheadS");
			lookAheadSS = bundle.getFloat("lookAheadSS");
			lookAheadPst = bundle.getInt("lookAheadPst");
			lookAheadB = bundle.getFloat("lookAheadB");
			smoothB = bundle.getFloat("smoothB");
			smoothBS = bundle.getFloat("smoothBS");

			mapCenter = bundle.getDoubleArray("mapCenter");
			currentLocation = bundle.getDoubleArray("currentLocation");
			mapCenterXY = bundle.getIntArray("mapCenterXY");
			currentLocationXY = bundle.getIntArray("currentLocationXY");
			bearing = bundle.getFloat("bearing");
			speed = bundle.getFloat("speed");
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
		bundle.putBoolean("hideOnDrag", hideOnDrag);
		bundle.putBoolean("loadBestMap", loadBestMap);
		bundle.putInt("bestMapInterval", bestMapInterval);

		bundle.putBoolean("isFixed", isFixed);
		bundle.putBoolean("isMoving", isMoving);
		bundle.putLong("lastBestMap", lastBestMap);

		bundle.putInt("penX", penX);
		bundle.putInt("penY", penY);
		bundle.putInt("penOX", penOX);
		bundle.putInt("penOY", penOY);
		bundle.putIntArray("lookAheadXY", lookAheadXY);
		bundle.putInt("lookAhead", lookAhead);
		bundle.putFloat("lookAheadC", lookAheadC);
		bundle.putFloat("lookAheadS", lookAheadS);
		bundle.putFloat("lookAheadSS", lookAheadSS);
		bundle.putInt("lookAheadPst", lookAheadPst);
		bundle.putFloat("lookAheadB", lookAheadB);
		bundle.putFloat("smoothB", smoothB);
		bundle.putFloat("smoothBS", smoothBS);

		bundle.putDoubleArray("mapCenter", mapCenter);
		bundle.putDoubleArray("currentLocation", currentLocation);
		bundle.putIntArray("mapCenterXY", mapCenterXY);
		bundle.putIntArray("currentLocationXY", currentLocationXY);
		bundle.putFloat("bearing", bearing);
		bundle.putFloat("speed", speed);
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
				MapActivity androzic = (MapActivity) this.getContext();
				androzic.zoomMap(scale);
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
			synchronized (lock)
			{
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
		}
		return true;
	}
}
