package com.androzic.waypoint;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;
import com.androzic.util.StringFormatter;

public class WaypointListActivity extends ActionBarActivity implements OnWaypointActionListener
{
	private Androzic application;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = Androzic.getApplication();

		setContentView(R.layout.act_fragment);

		if (savedInstanceState == null)
		{
			Fragment fragment = Fragment.instantiate(this, WaypointList.class.getName());
			FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(android.R.id.content, fragment, "WaypointList");
			fragmentTransaction.commit();
		}
	}

	@Override
	public void onWaypointView(Waypoint waypoint)
	{
		application.ensureVisible(waypoint);
		finish();
	}

	@Override
	public void onWaypointNavigate(Waypoint waypoint)
	{
		Intent intent = new Intent(application, NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
		intent.putExtra(NavigationService.EXTRA_NAME, waypoint.name);
		intent.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude);
		intent.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude);
		intent.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity);
		application.startService(intent);
		finish();
	}

	@Override
	public void onWaypointEdit(Waypoint waypoint)
	{
		int index = application.getWaypointIndex(waypoint);
		startActivity(new Intent(application, WaypointProperties.class).putExtra("INDEX", index));
	}

	@Override
	public void onWaypointShare(Waypoint waypoint)
	{
		Intent i = new Intent(android.content.Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
		i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords);
		startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
	}

	@Override
	public void onWaypointRemove(Waypoint waypoint)
	{
		application.removeWaypoint(waypoint);
	}

}
