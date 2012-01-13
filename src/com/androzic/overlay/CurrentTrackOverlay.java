package com.androzic.overlay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;

import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.track.ITrackingCallback;
import com.androzic.track.ITrackingRemoteService;

public class CurrentTrackOverlay extends TrackOverlay
{
	private ITrackingRemoteService remoteService = null;
	private boolean isBound = false;

    public CurrentTrackOverlay(final Activity mapActivity)
    {
    	super(mapActivity);

    	onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
    	
    	track.name = "Current Track";
    	track.show = true;
    }
    
	public void setMapContext(final Activity activity)
	{
		unbind();
		super.setMapContext(activity);
        isBound = context.bindService(new Intent(ITrackingRemoteService.class.getName()), connection, 0);
	}

    @Override
	public void setTrack(Track track)
	{
    	this.track.clear();
		this.track = track;
		points.clear();
	}

	public void clear()
	{
		track.clear();
		points.clear();
	}

	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		unbind();
    }
	
	private void unbind()
	{
    	if (isBound)
    	{
    		if (remoteService != null)
    		{
            	try
            	{
                	remoteService.unregisterCallback(callback);
                }
            	catch (RemoteException e) { }
    		}

    		context.unbindService(connection);
    		isBound = false;
    	}
	}
	
	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		super.onPreferencesChanged(settings);
    	track.maxPoints = Integer.parseInt(settings.getString(context.getString(R.string.pref_tracking_currentlength), context.getString(R.string.def_tracking_currentlength)));		
	}
    
    private ServiceConnection connection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            remoteService = ITrackingRemoteService.Stub.asInterface(service);

            try
            {
                remoteService.registerCallback(callback);
            }
            catch (RemoteException e) { }
        }

        public void onServiceDisconnected(ComponentName className)
        {
            remoteService = null;
        }
    };
    
    private ITrackingCallback callback = new ITrackingCallback.Stub()
    {
        public void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double trk, long time)
        {
        	track.addTrackPoint(continous, lat, lon, elev, speed, time);
        }
    };

}
