package com.androzic.overlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.androzic.MapView;

public abstract class MapOverlay
{
	Activity context;
	boolean enabled;
	
	MapOverlay(final Activity activity)
	{
		context = activity;
		enabled = false;
	}
	
	public void setMapContext(final Activity activity)
	{
		context = activity;
	}
	
	/**
	 * Called when application preferences where changed
	 * @param settings <code>SharedPreferences</code> containing current preferences
	 */
	public abstract void onPreferencesChanged(final SharedPreferences settings);
	
	/**
	 * Managed Draw calls gives Overlays the possibility to first draw manually and after 
	 * that do a final draw. This is very useful, i sth. to be drawn needs to be <b>topmost</b>.
	 */
	public void onManagedDraw(final Canvas c, final MapView mapView)
	{
		if (enabled)
		{
			onDraw(c, mapView);
			onDrawFinished(c, mapView);
		}
	}

	protected abstract void onDraw(final Canvas c, final MapView mapView);
		
	protected abstract void onDrawFinished(final Canvas c, final MapView mapView);
	
	public void onBeforeDestroy()
	{
		enabled = false;
	}

	public void onMapChanged()
	{
	}

	public boolean disable()
	{
		boolean r = enabled;
		enabled = false;
		return r;
	}

	public void enable()
	{
		enabled = true;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onKeyDown(final int keyCode, KeyEvent event, final MapView mapView)
	{
		return false;
	}
		
	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onKeyUp(final int keyCode, KeyEvent event, final MapView mapView)
	{
		return false;
	}
		
	/**
	 * <b>You can prevent all(!) other Touch-related events from happening!</b><br />
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onTouchEvent(final MotionEvent event, final MapView mapView)
	{
		return false;
	}
	
	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onTrackballEvent(final MotionEvent event, final MapView mapView)
	{
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onSingleTapUp(MotionEvent e, MapView mapView)
	{
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onLongPress(MotionEvent e, MapView mapView)
	{
		return false;
	}
}
