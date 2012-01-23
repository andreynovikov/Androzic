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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path.Direction;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

public class HSIView extends View
{
	private static final int MAX_WIDTH = 1000;
	private int width = 220;
	private float scale = 1;
	
	private static final float MAX_ROTATION_SPEED = 2;
	private static final float INC_ROTATION_SPEED = 0.05f;
	
	private Bitmap compassArrow;

	private Paint borderPaint;
	private Paint scalePaint;
	private Paint navPaint;
	private Paint textPaint;
	private Paint errorPaint;
	private Paint warnPaint;
	private RectF rect30;
	private RectF rect10;
	private RectF rect5;
	private Path planePath;
	private Path arrowPath;
	private Path xtkPath;
	private Path clipPath;
	private Path bearingArrow;

	private boolean compassMode;
	
	private float azimuth;
	private float course;
	private float bearing;
	private float xtk;
	private int proximity;
	private int navigating;
	
	private float rtA;
	private float rtAS;
	private float rtB;
	private float rtBS;
	private float rtC;
	private float rtCS;

	public HSIView(Context context)
	{
		super(context);
		initialize();
	}

	public HSIView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initialize();
	}

	public HSIView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initialize();
	}
	
	private void initialize()
	{
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Style.STROKE);
        borderPaint.setColor(Color.DKGRAY);
        borderPaint.setStrokeWidth(5);
        scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scalePaint.setStyle(Style.FILL);
        scalePaint.setColor(Color.LTGRAY);
        scalePaint.setStrokeWidth(1);
        navPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        navPaint.setStyle(Style.FILL);
        navPaint.setColor(Color.WHITE);
        navPaint.setStrokeWidth(1);
        errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setStyle(Style.FILL);
        errorPaint.setColor(Color.rgb(160, 0, 0));
        errorPaint.setStrokeWidth(1);
        warnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warnPaint.setStyle(Style.FILL);
        warnPaint.setColor(Color.rgb(255, 80, 0));
        warnPaint.setStrokeWidth(1);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setStrokeWidth(1);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTextSize(35);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setColor(Color.LTGRAY);

        compassMode = false;
        
        azimuth = 0;
        course = 0;
        bearing = 0;
        xtk = 0;
        proximity = 200;
        navigating = 0;

        rtA = 0;
        rtAS = 0;
        rtB = 0;
        rtBS = 0;
        rtB = 0;
        rtBS = 0;
        
        setSaveEnabled(true);
	}
	
	public void initialize(int proximity, float azimuth)
	{
		this.proximity = proximity;
        this.azimuth = azimuth;
        rtA = azimuth;
	}
	
	protected void setCompassMode(boolean mode)
	{
		compassMode = mode;
		if (compassMode)
		{
			compassArrow = BitmapFactory.decodeResource(getResources(), R.drawable.compass_needle);
		}
	}

	protected void setProximity(int proximity)
	{
		this.proximity = proximity;
	}

	protected void setAzimuth(float azimuth)
	{
		this.azimuth = azimuth;
		calcAzimuthRotation();
	}
	
	private void calcAzimuthRotation()
	{
		if (azimuth != rtA)
		{
			float turn = azimuth - rtA;
			if (Math.abs(turn) > 180)
			{
				turn = turn - Math.signum(turn) * 360;
			}
			if (Math.abs(turn) > Math.abs(rtAS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED))
			{
				rtAS += Math.signum(turn) * INC_ROTATION_SPEED;
				if (Math.abs(rtAS) > MAX_ROTATION_SPEED)
				{
					rtAS = Math.signum(rtAS) * MAX_ROTATION_SPEED;
				}
			}
			else if (Math.signum(turn) != Math.signum(rtAS))
			{
				rtAS += Math.signum(turn) * INC_ROTATION_SPEED * 2;
			}
			else if (Math.abs(rtAS) > INC_ROTATION_SPEED)
			{
				rtAS -= Math.signum(turn) * INC_ROTATION_SPEED * 0.5;
			}
			if (Math.abs(turn) < INC_ROTATION_SPEED)
			{
				rtA = azimuth;
				rtAS = 0;
			}
			else
			{
				rtA += rtAS;
				if (rtA >= 360) rtA -= 360;
				if (rtA < 0) rtA = 360 - rtA;
			}
			postInvalidate();
		}
	}

	protected void setCourse(float course)
	{
		this.course = course;
		calcCourseRotation();
	}

	private void calcCourseRotation()
	{
		if (course != rtC)
		{
			float turn = course - rtC;
			if (Math.abs(turn) > 180)
			{
				turn = turn - Math.signum(turn) * 360;
			}
			if (Math.abs(turn) > Math.abs(rtCS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED))
			{
				rtCS += Math.signum(turn) * INC_ROTATION_SPEED;
				if (Math.abs(rtCS) > MAX_ROTATION_SPEED)
				{
					rtCS = Math.signum(rtCS) * MAX_ROTATION_SPEED;
				}
			}
			else if (Math.signum(turn) != Math.signum(rtCS))
			{
				rtCS += Math.signum(turn) * INC_ROTATION_SPEED * 2;
			}
			else if (Math.abs(rtCS) > INC_ROTATION_SPEED)
			{
				rtCS -= Math.signum(turn) * INC_ROTATION_SPEED * 0.5;
			}
			if (Math.abs(turn) < INC_ROTATION_SPEED)
			{
				rtC = course;
				rtCS = 0;
			}
			else
			{
				rtC += rtCS;
				if (rtC >= 360) rtC -= 360;
				if (rtC < 0) rtC = 360 - rtC;
			}
			postInvalidate();
		}
	}

	protected void setBearing(float bearing)
	{
		this.bearing = bearing;
		calcBearingRotation();
	}

	private void calcBearingRotation()
	{
		if (bearing != rtB)
		{
			float turn = bearing - rtB;
			if (Math.abs(turn) > 180)
			{
				turn = turn - Math.signum(turn) * 360;
			}
			if (Math.abs(turn) > Math.abs(rtBS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED))
			{
				rtBS += Math.signum(turn) * INC_ROTATION_SPEED;
				if (Math.abs(rtBS) > MAX_ROTATION_SPEED)
				{
					rtBS = Math.signum(rtBS) * MAX_ROTATION_SPEED;
				}
			}
			else if (Math.signum(turn) != Math.signum(rtBS))
			{
				rtBS += Math.signum(turn) * INC_ROTATION_SPEED * 2;
			}
			else if (Math.abs(rtBS) > INC_ROTATION_SPEED)
			{
				rtBS -= Math.signum(turn) * INC_ROTATION_SPEED * 0.5;
			}
			if (Math.abs(turn) < INC_ROTATION_SPEED)
			{
				rtB = bearing;
				rtBS = 0;
			}
			else
			{
				rtB += rtBS;
				if (rtB >= 360) rtB -= 360;
				if (rtB < 0) rtB = 360 - rtB;
			}
			postInvalidate();
		}
	}

	protected void setXtk(float xtk)
	{
		if (this.xtk != xtk)
			postInvalidate();
		this.xtk = xtk;
	}

	protected void setNavigating(int navigating)
	{
        course = 0;
        bearing = 0;
        xtk = 0;
		this.navigating = navigating;
		postInvalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
//		int sw = MeasureSpec.getSize(widthMeasureSpec);
//		int sh = MeasureSpec.getSize(heightMeasureSpec);
		
		int w = getMeasurement(widthMeasureSpec, MAX_WIDTH);
		int h = getMeasurement(heightMeasureSpec, MAX_WIDTH);
		
		if (w > h)
		{
			width = h / 2 - 20;
		}
		else
		{
			width = w / 2 - 20;
		}
		scale = width / 220.0f;

		setMeasuredDimension(w, h);
	}

	private int getMeasurement(int measureSpec, int preferred)
	{
		int specSize = MeasureSpec.getSize(measureSpec);
		int measurement = 0;
		
		switch(MeasureSpec.getMode(measureSpec))
		{
			case MeasureSpec.EXACTLY:
				// This means the width of this view has been given.
				measurement = specSize;
				break;
			case MeasureSpec.AT_MOST:
				// Take the minimum of the preferred size and what we were told to be.
				measurement = Math.min(preferred, specSize);
				break;
			default:
				measurement = preferred;
				break;
		}
		
		return measurement;
	}	
	
    @Override 
    protected void onDraw(Canvas canvas)
    {
        int cx = getWidth();
        int cy = getHeight();

        canvas.translate(cx/2, cy/2);
        canvas.drawCircle(0, 0, width+10*scale, borderPaint);
        
        if (! compassMode)
        {
	        // plane
	        canvas.drawPath(planePath, scalePaint);
        }

        canvas.rotate(-rtA);

        // scale
        for (int i = 72; i > 0; i--)
        {
        	if (i % 2 == 1)
            	canvas.drawRect(rect5, scalePaint);
        	if (i % 6 == 0)
        	{
            	canvas.drawRect(rect30, scalePaint);
            	if (i % 18 == 0)
            	{
            		String cd = i == 72 ? "N" : i == 54 ? "E" : i == 36 ? "S" : "W";
            		canvas.drawText(cd, 0, -width+80*scale, textPaint);
            	}
            	else
            	{
            		canvas.drawText(Integer.toString((72-i)/2), 0, -width+80*scale, textPaint);
            	}
        	}
        	else if (i % 2 == 0)
        	{
        		canvas.drawRect(rect10, scalePaint);
        	}
        	canvas.rotate(5);
        }
        if (compassMode)
        {
	        canvas.drawBitmap(compassArrow, -compassArrow.getWidth() / 2, -compassArrow.getHeight() / 2, null);
        }
    	
    	canvas.save();

        if (! compassMode)
        {
	    	canvas.rotate(navigating == 2 ? rtC : rtB);
	    	// course arrow
	        canvas.drawPath(arrowPath, navPaint);
	        canvas.drawPath(xtkPath, scalePaint);
	        
	        // xtk unavailable
	        if (navigating == 0)
	        	canvas.drawRect(-110*scale, -50*scale, -60*scale, -20*scale, errorPaint);
	        else if (navigating == 1)
	        	canvas.drawRect(-110*scale, -50*scale, -60*scale, -20*scale, warnPaint);
	        
	        // xtk bar
	        int offset = Math.round(xtk / proximity * 20*scale);
	        RectF rectXtk = new RectF(-6, -width+124*scale, +6, width-124*scale);
	        rectXtk.offset(offset, 0);
	        canvas.clipPath(clipPath);
			canvas.drawRect(rectXtk, navPaint);
	
			canvas.restore();
			
	    	canvas.rotate(rtB);
			// bearing bug
	        canvas.drawPath(bearingArrow, navPaint);
        }
        
        calcAzimuthRotation();
        if (! compassMode)
        {
	        calcBearingRotation();
	        calcCourseRotation();
        }
    }

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);

		if (w == 0 || h == 0)
			return;

		textPaint.setTextSize(35 * scale);

        rect30 = new RectF(-5, width, +5, width-45*scale);
        rect10 = new RectF(-2, width, +2, width-30*scale);
        rect5 = new RectF(-2, width, +2, width-20*scale);

        if (! compassMode)
        {
			planePath = new Path();
	        planePath.addRoundRect(new RectF(-4, -30*scale, +4, +50*scale), 5, 5, Direction.CW);
	        planePath.addRoundRect(new RectF(-40*scale, -4, +40*scale, +4), 5, 5, Direction.CW);
	        planePath.addRoundRect(new RectF(-15*scale, +35*scale, +15*scale, +43*scale), 5, 5, Direction.CW);
	        planePath.addRect(-2, width+3, +2, width+18*scale, Direction.CW);
	        planePath.addRect(-2, -width-3, +2, -width-18*scale, Direction.CW);
	
	        arrowPath = new Path();
	        arrowPath.moveTo(0, -width+5);
	        arrowPath.lineTo(+15, -width+70*scale);
	        arrowPath.lineTo(+6, -width+70*scale);
	        arrowPath.lineTo(+6, -width+120*scale);
	        arrowPath.lineTo(-6, -width+120*scale);
	        arrowPath.lineTo(-6, -width+70*scale);
	        arrowPath.lineTo(-15, -width+70*scale);
	    	arrowPath.close();
	    	arrowPath.moveTo(+6, width-5);
	        arrowPath.lineTo(+6, width-120*scale);
	        arrowPath.lineTo(-6, width-120*scale);
	    	arrowPath.lineTo(-6, width-5);
	    	arrowPath.close();
	    	
	        xtkPath = new Path();
	        xtkPath.addCircle(+60*scale, 0, 6, Direction.CW);
	        xtkPath.addCircle(+120*scale, 0, 6, Direction.CW);
	        xtkPath.addCircle(-60*scale, 0, 6, Direction.CW);
	        xtkPath.addCircle(-120*scale, 0, 6, Direction.CW);
	
	        clipPath = new Path();
	        clipPath.addCircle(0, 0, width-90*scale, Direction.CW);
	
			bearingArrow = new Path();
			bearingArrow.addRect(-20*scale, -width-20*scale, -2, -width-5, Direction.CW);
			bearingArrow.addRect(+2, -width-20*scale, +20*scale, -width-5, Direction.CW);
        }
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if(! (state instanceof SavedState))
		{
	      super.onRestoreInstanceState(state);
	      return;
	    }

	    SavedState ss = (SavedState) state;
	    super.onRestoreInstanceState(ss.getSuperState());

	    setCompassMode(ss.compassMode);
	    
	    azimuth = ss.azimuth;
	    course = ss.course;
	    bearing = ss.bearing;
	    xtk = ss.xtk;
	    proximity = ss.proximity;
	    navigating = ss.navigating;
        
	    rtA = ss.rtA;
	    rtAS = ss.rtAS;
	    rtB = ss.rtB;
	    rtBS = ss.rtBS;
	    rtB = ss.rtB;
	    rtBS = ss.rtBS;
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
	    Parcelable superState = super.onSaveInstanceState();
	    SavedState ss = new SavedState(superState);

	    ss.compassMode = compassMode;
	    
	    ss.azimuth = azimuth;
	    ss.course = course;
	    ss.bearing = bearing;
	    ss.xtk = xtk;
	    ss.proximity = proximity;
	    ss.navigating = navigating;
        
	    ss.rtA = rtA;
	    ss.rtAS = rtAS;
	    ss.rtB = rtB;
	    ss.rtBS = rtBS;
	    ss.rtB = rtB;
	    ss.rtBS = rtBS;

	    return ss;
	}

	static class SavedState extends BaseSavedState
	{
		boolean compassMode;
		
		float azimuth;
		float course;
		float bearing;
		float xtk;
		int proximity;
		int navigating;
		
		float rtA;
		float rtAS;
		float rtB;
		float rtBS;
		float rtC;
		float rtCS;

        SavedState(Parcelable superState)
        {
        	super(superState);
        }

        private SavedState(Parcel in)
        {
        	super(in);
        	this.compassMode = in.readInt() == 1 ? true : false;
        	this.azimuth = in.readFloat();
        	this.course = in.readFloat();
        	this.bearing = in.readFloat();
        	this.xtk = in.readFloat();
        	this.proximity = in.readInt();
        	this.navigating = in.readInt();
        	this.rtA = in.readFloat();
        	this.rtAS = in.readFloat();
        	this.rtB = in.readFloat();
        	this.rtBS = in.readFloat();
        	this.rtC = in.readFloat();
        	this.rtCS = in.readFloat();
        }

        @Override
        public void writeToParcel(Parcel out, int flags)
        {
        	super.writeToParcel(out, flags);
        	out.writeInt(compassMode ? 1 : 0);
        	out.writeFloat(this.azimuth);
        	out.writeFloat(this.course);
        	out.writeFloat(this.bearing);
        	out.writeFloat(this.xtk);
        	out.writeInt(this.proximity);
        	out.writeInt(this.navigating);
        	out.writeFloat(this.rtA);
        	out.writeFloat(this.rtAS);
        	out.writeFloat(this.rtB);
        	out.writeFloat(this.rtBS);
        	out.writeFloat(this.rtC);
        	out.writeFloat(this.rtCS);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>()
        {
        	public SavedState createFromParcel(Parcel in)
        	{
        		return new SavedState(in);
        	}
        	
        	public SavedState[] newArray(int size)
        	{
        		return new SavedState[size];
        	}
        };
	}
}
