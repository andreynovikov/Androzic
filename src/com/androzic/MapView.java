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

package com.androzic;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.androzic.overlay.MapOverlay;

import org.metalev.multitouch.controller.MultiTouchController;
import org.metalev.multitouch.controller.MultiTouchController.MultiTouchObjectCanvas;
import org.metalev.multitouch.controller.MultiTouchController.PointInfo;
import org.metalev.multitouch.controller.MultiTouchController.PositionAndScale;

public class MapView extends View implements MultiTouchObjectCanvas<Object>
{
	private static final float MAX_ROTATION_SPEED = 15f;
	private static final float INC_ROTATION_SPEED = 1f;
	private static final float MAX_SHIFT_SPEED = 20f;
	private static final float INC_SHIFT_SPEED = 1.5f;

	private boolean autoFollow = true;
	private boolean strictUnfollow = true;
	private boolean hideOnDrag = true;
	private boolean ready = false;
	private boolean isFixed = false;
	private boolean isMoving = false;
	
	int penX = 0;
	int penY = 0;
	int penOX = 0;
	int penOY = 0;
	int[] lookAheadXY = new int[] {0, 0};
	int lookAhead = 0;
	float lookAheadC = 0;
	float lookAheadS = 0;
	float lookAheadSS = 0;
	int lookAheadPst = 0;
	
	int waypointSelected = -1;
		
	public double[] currentLocation;
	public int[] currentXY;
	private float lookAheadB = 0;
	private float smoothB = 0;
	private float smoothBS = 0;
	private float bearing = 0;

	private Drawable movingCursor	= null;	
	private Drawable standingCursor = null;
	private PorterDuffColorFilter active = null;
	
	private Androzic application;
	
	private MultiTouchController<Object> multiTouchController;
	private float pinch = 0;
	private float scale = 1;
	private boolean wasMultitouch = false;
	
	public MapView(Context context)
	{
		super(context);
	}

	public MapView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public MapView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
	}

	public void initialize(Androzic application)
   	{
		this.application = application;

		Resources resources = getResources();
		
		if (application.customCursor != null)
		{
			movingCursor = application.customCursor;
		}
		else
		{
			movingCursor = resources.getDrawable(R.drawable.moving);
		}
		movingCursor.setBounds(-movingCursor.getIntrinsicWidth()/2, 0, movingCursor.getIntrinsicWidth()/2, movingCursor.getIntrinsicHeight());
   		standingCursor = resources.getDrawable(R.drawable.standing);
		standingCursor.setBounds(-standingCursor.getIntrinsicWidth()/2, -standingCursor.getIntrinsicHeight()/2, standingCursor.getIntrinsicWidth()/2, standingCursor.getIntrinsicHeight()/2);

   		multiTouchController = new MultiTouchController<Object>(this, false);
   	 
   		Log.d("ANDROZIC","Map initialize");
   	}	
	
	@Override
    protected void onDraw(Canvas canvas) 
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

        /*
        double[] tl = Swampex.getLocationByXYonMap(x - cx, y - cy);
        double[] br = Swampex.getLocationByXYonMap(x + cx, y + cy);
        Log.d("ANDROZIC", "TL: " + String.valueOf(tl[0]) + ", " + String.valueOf(tl[1]));
        Log.d("ANDROZIC", "BR: " + String.valueOf(br[0]) + ", " + String.valueOf(br[1]));
        */
        
        application.drawMap(currentLocation, lookAheadXY, getWidth(), getHeight(), canvas);

		canvas.translate(lookAheadXY[0], lookAheadXY[1]);

        canvas.translate(cx, cy);

        // draw overlays
        if (! scaled && ready && ((penOX == 0 && penOY == 0) || ! hideOnDrag))
        {
    		canvas.save();
    		canvas.translate(-currentXY[0], -currentXY[1]);
        	// TODO Should be synchronized?
        	for (MapOverlay mo : application.getOverlays(Androzic.ORDER_DRAW_PREFERENCE))
       			mo.onManagedDraw(canvas, this);
    		canvas.restore();
        }
        
		// draw cursor (it is always topmost)
        if (! scaled && ready)
        {
			if (isMoving == true && autoFollow == true)
			{
				canvas.rotate(bearing, 0, 0);
				movingCursor.draw(canvas);
			}
			else
			{
				standingCursor.draw(canvas);
			}
        }
        
		if (isMoving && autoFollow && isFixed)
		{
			lookAheadC = lookAhead;
		}
		else
		{
			lookAheadC = 0;
		}
		calculateLookAhead();
	}

   	public void setBearing(float b)
   	{
   		bearing = b;
   		lookAheadB = Math.round(b/10)*10;
   		calculateLookAhead();
   	}
   	
   	private void calculateLookAhead()
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
				if (smoothB >= 360) smoothB -= 360;
				if (smoothB < 0) smoothB = 360 - smoothB;
			}
		}
		if (recalculated)
		{
			lookAheadXY[0] = (int) Math.round(Math.sin(Math.toRadians(smoothB)) * -lookAheadS);
			lookAheadXY[1] = (int) Math.round(Math.cos(Math.toRadians(smoothB)) * lookAheadS);
			invalidate();
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

   	public void becomeNotReady()
   	{
   		ready = false;
   	}

   	public boolean isReady()
   	{
   		return ready;
   	}
   	
   	public void setAutoFollow(boolean follow) 
   	{
   		if (autoFollow != follow)
   		{
   			standingCursor.setColorFilter(follow ? active : null);
	   		if (follow)
	   			Toast.makeText(getContext(), R.string.auto_follow_enabled, Toast.LENGTH_SHORT).show();
	   		else
	   			Toast.makeText(getContext(), R.string.auto_follow_disabled, Toast.LENGTH_SHORT).show();
	   		if (ready)
	   			post(updateView);
	   		autoFollow = follow;
   		}
   	}

   	private void setAutoFollowThroughContext(boolean follow)
   	{
   		if (autoFollow != follow)
   		{
			try
			{
				MapActivity androzic = (MapActivity) this.getContext();
				androzic.setAutoFollow(! autoFollow);
			}
			catch(Exception e)
			{
				setAutoFollow(! autoFollow);
			}
   		}
   	}
   	
   	public boolean getAutoFollow()
   	{
   		return autoFollow;
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

   	public void setFixed(boolean fixed)
	{
		isFixed = fixed;
		movingCursor.setColorFilter(isFixed ? active : null);
		if (ready)
   			post(updateView);
	}

   	public boolean isFixed()
   	{
   		return isFixed;
   	}

	/**
	 * Set the amount of screen intended for looking ahead
	 * 
	 * @param ahead % of the smaller dimension of screen
	 */
	public void setLookAhead(final int ahead)
	{
		lookAheadPst = ahead;
		final int w = getWidth();
		final int h = getHeight();
		final int half = w > h ? h / 2 : w / 2;
		lookAhead = (int) (half * ahead * 0.01);
	}

	public void setCursorColor(final int color)
	{
		active = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
		movingCursor.setColorFilter(isFixed ? active : null);			
		standingCursor.setColorFilter(autoFollow ? active : null);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		Log.d("ANDROZIC","Size: "+w+","+h+","+oldw+","+oldh);
		super.onSizeChanged(w, h, oldw, oldh);
		if ((w != oldw || h != oldh))
		{
			setLookAhead(lookAheadPst);
			updateView(false);
		}		
	}
	
    private final Runnable updateView = new Runnable() 
    {
        public void run() 
        {
            updateView(false);
        }
    };

	public void update(boolean mapChanged)
	{
		updateView(mapChanged);
	}

 	private final void updateView(boolean mapChanged)
	{
        currentLocation = application.getLocation();
        currentXY = application.getXYbyLatLon(currentLocation[0], currentLocation[1]);

		try
		{
			MapActivity androzic = (MapActivity) this.getContext();
			androzic.updateCoordinates(currentLocation);
			if (mapChanged)
			{
				scale = 1;
				androzic.updateFileInfo();
			}
		}
		finally {}

		ready = true;
		invalidate();
	}
 	
   	private boolean waypointTapped(int x, int y)
   	{
		try
		{
   			int centerX = currentXY[0] - getWidth() / 2;
   			int centerY = currentXY[1] - getHeight() / 2;
   			
   			if (isMoving && autoFollow && isFixed)
   			{
   				centerX -= lookAheadXY[0];
   				centerY -= lookAheadXY[1];
   			}

			waypointSelected = application.waypointsOverlay.waypointTapped(x, y, centerX, centerY);
			if (waypointSelected > -1)
			{
				try
				{
					MapActivity androzic = (MapActivity) this.getContext();
					if (androzic.editingRoute != null)
					{
						showContextMenu();
					}
					else
					{
						androzic.showWaypointInfo();
					}
				}
				finally {}
				
   				return true;
			}
		}
		finally {}

   		return false;
   	}

   	private final void onDragFinished(int deltaX, int deltaY)
	{
   		boolean mapChanged = application.scrollMap(-deltaX, -deltaY);
        updateView(mapChanged);
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
   				penOX = penX = (int) event.getX();
   				penOY = penY = (int) event.getY();
   				break;
   			case MotionEvent.ACTION_MOVE:
   				if (! wasMultitouch && (! autoFollow || ! strictUnfollow))
   				{
   		   			int x = (int) event.getX();
   		   			int y = (int) event.getY();
   		   					
   		   			int dx = -(penX - x);
   		   			int dy = -(penY - y);

	   		        if (! autoFollow && (Math.abs(dx) > 0 || Math.abs(dy) > 0))
	   				{
	   	   				penX = x;
	   	   				penY = y;
	   					onDragFinished(dx, dy);
	   				}
					if (Math.abs(dx) > 10 || Math.abs(dy) > 10)
	   				{
	   	    			if (! strictUnfollow)
	   	    				setAutoFollowThroughContext(false);
	   				}
   				}
   				break;
   			case MotionEvent.ACTION_UP:
				int dx = -(penOX - (int) event.getX());
				int dy = -(penOY - (int) event.getY());
				int tapDelta = autoFollow ? 15 : 30;
				if (! wasMultitouch && Math.abs(dx) < tapDelta && Math.abs(dy) < tapDelta)
				{
					waypointTapped(penOX, penOY);
				}
				penX = 0;
				penY = 0;
				penOX = 0;
				penOY = 0;
				wasMultitouch = false;
				invalidate();
       	}

   		return true;
   	}
	
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
    	switch (keyCode)
    	{
    		case KeyEvent.KEYCODE_DPAD_CENTER:
    			setAutoFollowThroughContext(! autoFollow);
    			return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
   				if (! autoFollow || ! strictUnfollow)
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
   	    			if (autoFollow)
   	    				setAutoFollowThroughContext(false);
    				onDragFinished(dx, dy);
    				return true;
   				}
    	}

    	/*
    	for (MapOverlay mo : application.getOverlays())
    		if (mo.onKeyDown(keyCode, event, this))
    			return true;
    	*/

    	return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
    	/*
    	for (MapOverlay mo : application.getOverlays())
    		if (mo.onKeyUp(keyCode, event, this))
    			return true;
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
   				setAutoFollowThroughContext(! autoFollow);
   				break;
   			case MotionEvent.ACTION_MOVE:
   				if (! autoFollow)
   				{
	   		        int n = event.getHistorySize();
	   		        final float scaleX = event.getXPrecision();
	   		        final float scaleY = event.getYPrecision();
   		            int dx = (int) (-event.getX()*scaleX);
   		            int dy = (int) (-event.getY()*scaleY);
	   		        for (int i=0; i<n; i++)
	   		        {
	   		            dx += -event.getHistoricalX(i)*scaleX;
	   		            dy += -event.getHistoricalY(i)*scaleY;
	   		        }
	   		        if (Math.abs(dx) > 0 || Math.abs(dy) > 0)
	   				{
	   					onDragFinished(dx, dy);
	   				}
   				}
   				break;
   		}
   		
/*    	for (MapOverlay mo : this.overlays)
    		if (mo.onTrackballEvent(event, this))
    			return true;*/

		return true;
    }
    
	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (state instanceof Bundle)
		{
			Bundle bundle = (Bundle) state;
			super.onRestoreInstanceState(bundle.getParcelable("instanceState"));

			autoFollow = bundle.getBoolean("autoFollow");
			strictUnfollow = bundle.getBoolean("strictUnfollow");
			hideOnDrag = bundle.getBoolean("hideOnDrag");
			ready = bundle.getBoolean("ready");
			isFixed = bundle.getBoolean("isFixed");
			isMoving = bundle.getBoolean("isMoving");
			
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
			
			waypointSelected = bundle.getInt("waypointSelected");
				
			currentLocation = bundle.getDoubleArray("currentLocation");
			currentXY = bundle.getIntArray("currentXY");
			bearing = bundle.getFloat("bearing");
			
			//TODO Should be somewhere else?
   			standingCursor.setColorFilter(autoFollow ? active : null);
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

		bundle.putBoolean("autoFollow", autoFollow);
		bundle.putBoolean("strictUnfollow", strictUnfollow);
		bundle.putBoolean("hideOnDrag", hideOnDrag);
		bundle.putBoolean("ready", ready);
		bundle.putBoolean("isFixed", isFixed);
		bundle.putBoolean("isMoving", isMoving);
		
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
		
		bundle.putInt("waypointSelected", waypointSelected);
			
		bundle.putDoubleArray("currentLocation", currentLocation);
		bundle.putIntArray("currentXY", currentXY);
		bundle.putFloat("bearing", bearing);
		
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
			Log.e("ANDROZIC","Scale: "+scale);
			try
			{
				MapActivity androzic = (MapActivity) this.getContext();
				androzic.zoomMap(scale);
			}
			finally {}
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
	        	scale = (float) (1/(Math.log10(1/scale) + 1));
	        }
			invalidate();
		}
		return true;
	}
}
