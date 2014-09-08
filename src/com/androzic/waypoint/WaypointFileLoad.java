package com.androzic.waypoint;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.MainActivity;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;

public class WaypointFileLoad extends Activity
{
	@SuppressLint("DefaultLocale")
	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		String filepath = intent.getData().getPath();

		final ProgressDialog pd = new ProgressDialog(this);

		pd.setIndeterminate(true);
		pd.show();

		Androzic application = (Androzic) getApplication();
		List<Waypoint> waypoints = null;

		try
		{
			File file = new File(filepath);
			WaypointSet wptset = new WaypointSet(file);
			String lc = file.getName().toLowerCase();
			if (lc.endsWith(".wpt"))
			{
				waypoints = OziExplorerFiles.loadWaypointsFromFile(file, application.charset);
			}
			else if (lc.endsWith(".kml"))
			{
				wptset.path = null;
				waypoints = KmlFiles.loadWaypointsFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				wptset.path = null;
				waypoints = GpxFiles.loadWaypointsFromFile(file);
			}
			if (waypoints != null)
			{
				for (Waypoint waypoint : waypoints)
				{
					waypoint.set = wptset;
				}
				int count = application.addWaypoints(waypoints, wptset);
				setResult(Activity.RESULT_OK, new Intent().putExtra("count", count));
			}
			else
			{
				setResult(Activity.RESULT_CANCELED, new Intent());
			}
		}
		catch (IllegalArgumentException e)
		{
			Toast.makeText(getBaseContext(), R.string.err_wrongformat, Toast.LENGTH_LONG).show();
		}
		catch (SAXException e)
		{
			Toast.makeText(getBaseContext(), R.string.err_wrongformat, Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		catch (IOException e)
		{
			Toast.makeText(getBaseContext(), R.string.err_read, Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		catch (ParserConfigurationException e)
		{
			Toast.makeText(getBaseContext(), R.string.err_read, Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}

		pd.dismiss();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
		finish();
	}
}
