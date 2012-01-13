package com.androzic.ui;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Portions of this file have been derived from code originally licensed
 * under the Apache License, Version 2.0.
 * 
 * Changes made by Christopher McCurdy, 2009.
 * Fixes and enhancements by Andrey Novikov, 2010.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

public class ColorPickerView extends View
{
    private Paint mPaint;
    private Paint mCenterPaint;
    private Paint mHSVPaint;
    private final int[] mColors;
    private int[] mHSVColors;
    private boolean mRedrawHSV;
    private boolean mTrackingCenter;
    private boolean mHighlightCenter;
    private OnColorChangedListener mListener;

    private int width = 100;
    private int radius = 33;
	private boolean horizontal = false;
    
    private static final int MAX_WIDTH = 300;

    public ColorPickerView(Context c, OnColorChangedListener l, int color)
    {
        super(c);
        
        mListener = l;
        mColors = new int[] {
            0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00,
            0xFFFFFF00, 0xFFFF0000
        };
        Shader s = new SweepGradient(0, 0, mColors, null);
        
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setShader(s);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(32);
        
        mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint.setColor(color);
        mCenterPaint.setStrokeWidth(5);
        
        mHSVColors = new int[] {
        		0xFF000000, color, 0xFFFFFFFF
        };

        mHSVPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHSVPaint.setStrokeWidth(10);
        
        mRedrawHSV = true;
    }
    
    public int getColor()
    {
    	return mCenterPaint.getColor();
    }
    
	public void setColor(int color)
	{
		mCenterPaint.setColor(color);
		invalidate();
	}
    
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int sw = MeasureSpec.getSize(widthMeasureSpec);
		int sh = MeasureSpec.getSize(heightMeasureSpec);
		
		int pw = sw > sh * 1.2 ? MAX_WIDTH+45 : MAX_WIDTH;
		int ph = sw > sh * 1.2 ? MAX_WIDTH : MAX_WIDTH+45;
		
		int w = getMeasurement(widthMeasureSpec, pw);
		int h = getMeasurement(heightMeasureSpec, ph);
		
		if (w > h * 1.2)
		{
			width = h / 2 - 20;
			horizontal  = true;
		}
		else
		{
			width = (h - 45) / 2 - 20;
		}

		radius = width / 3;

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
    	
        if (horizontal)
        	cx -= 45;
        else
        	cy -= 45;
        
        cx = cx / 2;
        cy = cy / 2;
        
        float r = width - mPaint.getStrokeWidth()*0.5f;
        
        canvas.translate(cx, cy);
        if (horizontal)
        	canvas.rotate(-90);
        
        int c = mCenterPaint.getColor();

        if (mRedrawHSV)
        {
            mHSVColors[1] = c;
            mHSVPaint.setShader(new LinearGradient(-width, 0, width, 0, mHSVColors, null, Shader.TileMode.CLAMP));
        }
        
        canvas.drawOval(new RectF(-r, -r, r, r), mPaint);
        canvas.drawCircle(0, 0, radius, mCenterPaint);
        canvas.drawRect(new RectF(-width, width + 25, width, width + 45), mHSVPaint);
        
        if (mTrackingCenter) {
            mCenterPaint.setStyle(Paint.Style.STROKE);
            
            if (mHighlightCenter) {
                mCenterPaint.setAlpha(0xFF);
            } else {
                mCenterPaint.setAlpha(0x80);
            }
            canvas.drawCircle(0, 0, radius + mCenterPaint.getStrokeWidth(), mCenterPaint);
            
            mCenterPaint.setStyle(Paint.Style.FILL);
            mCenterPaint.setColor(c);
        }
        
        mRedrawHSV = true;
    }

    private int ave(int s, int d, double p) {
        return (int) (s + java.lang.Math.round(p * (d - s)));
    }
    
    private int interpColor(int colors[], double unit) {
        if (unit <= 0) {
            return colors[0];
        }
        if (unit >= 1) {
            return colors[colors.length - 1];
        }
        
        double p = unit * (colors.length - 1);
        int i = (int)p;
        p -= i;

        // now p is just the fractional part [0...1) and i is the index
        int c0 = colors[i];
        int c1 = colors[i+1];
        int a = ave(Color.alpha(c0), Color.alpha(c1), p);
        int r = ave(Color.red(c0), Color.red(c1), p);
        int g = ave(Color.green(c0), Color.green(c1), p);
        int b = ave(Color.blue(c0), Color.blue(c1), p);
        
        return Color.argb(a, r, g, b);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int cx = getWidth();
        int cy = getHeight();
    	
        float y;
        float x;

        if (horizontal)
        {
        	// rotate coordinate system - it's simpler than handling it through all code
            y = event.getX() - (cx - 45) / 2;
            x = cy / 2 - event.getY();
        }
        else
        {
            x = event.getX() - cx / 2;
            y = event.getY() - (cy - 45) / 2;
        	cy -= 45;
        }

        boolean inCenter = java.lang.Math.sqrt(x*x + y*y) <= radius;
        boolean inPeeker = java.lang.Math.sqrt(x*x + y*y) <= width;
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTrackingCenter = inCenter;
                if (inCenter) {
                    mHighlightCenter = true;
                    invalidate();
                    break;
                }
            case MotionEvent.ACTION_MOVE:
                if (mTrackingCenter)
                {
                    if (mHighlightCenter != inCenter)
                    {
                        mHighlightCenter = inCenter;
                        invalidate();
                    }
                }
                else if ((x >= -width & x <= width) && (y <= width + 45 && y >= width + 25)) // see if we're in the hsv slider
                {
                	int a, r, g, b, c0, c1;
                	float p;

                	// set the center paint to this color
                	if (x < 0)
                	{
                		c0 = mHSVColors[0];
                		c1 = mHSVColors[1];
                		p = (x + width)/width;
                	}
                	else
                	{
                		c0 = mHSVColors[1];
                		c1 = mHSVColors[2];
                		p = x/width;
                	}
                	
            		a = ave(Color.alpha(c0), Color.alpha(c1), p);
            		r = ave(Color.red(c0), Color.red(c1), p);
            		g = ave(Color.green(c0), Color.green(c1), p);
            		b = ave(Color.blue(c0), Color.blue(c1), p);
            		
            		mCenterPaint.setColor(Color.argb(a, r, g, b));
                	
                	mRedrawHSV = false;
                	invalidate();
        		}
        		else if (inPeeker)
                {
                    double angle = java.lang.Math.atan2(y, x);
                    // need to turn angle [-PI ... PI] into unit [0....1]
                    double unit = (angle/(2*Math.PI));
                    if (unit < 0) {
                        unit += 1;
                    }
                    mCenterPaint.setColor(interpColor(mColors, unit));
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTrackingCenter)
                {
                    if (inCenter)
                    {
                        mListener.colorChanged(mCenterPaint.getColor());
                    }
                    mTrackingCenter = false;    // so we draw w/o halo
                    invalidate();
                }
                break;
        }
        return true;
    }

	public void setOnColorChangedListener(OnColorChangedListener l)
	{
		mListener = l;
	}

}
