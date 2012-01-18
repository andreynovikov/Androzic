package com.androzic.waypoint;

import java.io.File;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

public class WaypointInfo extends Activity implements OnClickListener
{
	private Waypoint waypoint;
	int index;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.act_waypoint_info);

        index = getIntent().getExtras().getInt("INDEX");
        double lat = getIntent().getExtras().getDouble("lat");
        double lon = getIntent().getExtras().getDouble("lon");
        
		Androzic application = (Androzic) getApplication();
		waypoint = application.getWaypoint(index);
		
		setTitle(waypoint.name);
		if (waypoint.drawImage)
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
			Bitmap b = BitmapFactory.decodeFile(application.iconPath + File.separator + waypoint.image, options);
			if (b != null)
			{
				b.setDensity(Bitmap.DENSITY_NONE);
				setFeatureDrawable(Window.FEATURE_LEFT_ICON, new BitmapDrawable(getResources(), b));
			}
			else
			{
				setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map);
			}
		}
		else
		{
			setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map);
		}

		WebView description = (WebView) findViewById(R.id.description);
		
		if ("".equals(waypoint.description))
		{
			description.setVisibility(View.GONE);
		}
		else
		{
			description.setBackgroundColor(Color.LTGRAY);
			description.loadData("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+waypoint.description, "text/html", "UTF-8");
		}

		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
		((TextView) findViewById(R.id.coordinates)).setText(coords);
		
		double dist = Geo.distance(lat, lon, waypoint.latitude, waypoint.longitude);
		double bearing = Geo.bearing(lat, lon, waypoint.latitude, waypoint.longitude);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.bearingH(bearing);
		((TextView) findViewById(R.id.distance)).setText(distance);

		if (waypoint.date != null)
			((TextView) findViewById(R.id.date)).setText(DateFormat.getDateFormat(this).format(waypoint.date)+" "+DateFormat.getTimeFormat(this).format(waypoint.date));
		else
			((TextView) findViewById(R.id.date)).setVisibility(View.GONE);
			
	    ((Button) findViewById(R.id.navigate_button)).setOnClickListener(this);
	    ((Button) findViewById(R.id.properties_button)).setOnClickListener(this);
	    ((Button) findViewById(R.id.remove_button)).setOnClickListener(this);
    }

	@Override
    public void onClick(View v)
    {
		setResult(Activity.RESULT_OK, new Intent().putExtra("index", index).putExtra("action", v.getId()));
   		finish();
    }

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		waypoint = null;
	}

}