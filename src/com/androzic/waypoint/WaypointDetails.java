package com.androzic.waypoint;

import java.io.File;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.FragmentHolder;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointDetails extends Fragment implements View.OnClickListener
{
	private FragmentHolder fragmentHolderCallback;
	private OnWaypointActionListener waypointActionsCallback;

	private Waypoint waypoint;
	private CharSequence oldTitle;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.waypoint_details, container, false);

		Button shareButton = (Button) rootView.findViewById(R.id.share_button);
		shareButton.setOnClickListener(this);

		return rootView;
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			fragmentHolderCallback = (FragmentHolder) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement FragmentHolder");
		}
		try
		{
			waypointActionsCallback = (OnWaypointActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnWaypointActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (waypoint != null)
		{
			Bundle args = getArguments();
			double lat = args != null ? args.getDouble("lat") : waypoint.latitude;
	        double lon = args != null ? args.getDouble("lon") : waypoint.longitude;
			updateWaypointDetails(lat, lon);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();

		fragmentHolderCallback.enableActionButton().setOnClickListener(this);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		
		fragmentHolderCallback.disableActionButton();

		if (oldTitle != null)
		{
			((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(oldTitle);
			oldTitle = null;
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.waypointdetails_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_edit:
				waypointActionsCallback.onWaypointEdit(waypoint);
				return true;
		}
		return false;
	}

	@Override
    public void onClick(View v)
    {
		switch (v.getId())
		{
			case R.id.toolbar_action_button:
				waypointActionsCallback.onWaypointNavigate(waypoint);
				break;
			case R.id.share_button:
				waypointActionsCallback.onWaypointShare(waypoint);
				break;
			case R.id.remove_button:
				waypointActionsCallback.onWaypointRemove(waypoint);
				break;
		}
    }

	public void setWaypoint(Waypoint waypoint)
	{
		this.waypoint = waypoint;
		
		if (isVisible())
		{
			Bundle args = getArguments();
			double lat = args != null ? args.getDouble("lat") : waypoint.latitude;
	        double lon = args != null ? args.getDouble("lon") : waypoint.longitude;
			updateWaypointDetails(lat, lon);
		}
	}

	@SuppressLint("NewApi")
	private void updateWaypointDetails(double lat, double lon)
	{
		Androzic application = Androzic.getApplication();
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		
		if (oldTitle == null)
			oldTitle = activity.getSupportActionBar().getTitle();
		activity.getSupportActionBar().setTitle(waypoint.name);

		View view = getView();
		
		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
		((TextView) view.findViewById(R.id.coordinates)).setText(coords);

		if (waypoint.altitude != Integer.MIN_VALUE)
		{
			// FIXME Does not use altitude units
			String altitude = String.format(Locale.getDefault(), "%d %s", waypoint.altitude, getResources().getStringArray(R.array.distance_abbrs_short)[2]);
			((TextView) view.findViewById(R.id.altitude)).setText(altitude);
			view.findViewById(R.id.altitude).setVisibility(View.VISIBLE);
		}
		else
		{
			view.findViewById(R.id.altitude).setVisibility(View.GONE);
		}

		if (waypoint.proximity > 0)
		{
			// FIXME Meters
			String altitude = String.format(Locale.getDefault(), "~%d m", waypoint.proximity);
			((TextView) view.findViewById(R.id.proximity)).setText(altitude);
			view.findViewById(R.id.proximity).setVisibility(View.VISIBLE);
		}
		else
		{
			view.findViewById(R.id.proximity).setVisibility(View.GONE);
		}

		double dist = Geo.distance(lat, lon, waypoint.latitude, waypoint.longitude);
		double bearing = Geo.bearing(lat, lon, waypoint.latitude, waypoint.longitude);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.bearingH(bearing);
		((TextView) view.findViewById(R.id.distance)).setText(distance);


		if (waypoint.date != null)
		{
			view.findViewById(R.id.date_row).setVisibility(View.VISIBLE);
			((TextView) view.findViewById(R.id.date)).setText(DateFormat.getDateFormat(activity).format(waypoint.date)+" "+DateFormat.getTimeFormat(activity).format(waypoint.date));
		}
		else
		{
			view.findViewById(R.id.date_row).setVisibility(View.GONE);
		}
		
		if ("".equals(waypoint.description))
		{
			view.findViewById(R.id.description_row).setVisibility(View.GONE);
		}
		else
		{
			WebView description = (WebView) view.findViewById(R.id.description);
			String descriptionHtml;
			try
			{
				TypedValue tv = new TypedValue();
				Theme theme = activity.getTheme();
				Resources resources = getResources();
				theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true);
				int secondaryColor = resources.getColor(tv.resourceId);
				String css = String.format("<style type=\"text/css\">html,body{margin:0;background:transparent} *{color:#%06X}</style>\n", (secondaryColor & 0x00FFFFFF));
				descriptionHtml = css + waypoint.description;
				description.setWebViewClient(new WebViewClient()
				{
				    @SuppressLint("NewApi")
					@Override
				    public void onPageFinished(WebView view, String url)
				    {
				    	view.setBackgroundColor(Color.TRANSPARENT);
				        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				        	view.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
				    }
				});
				description.setBackgroundColor(Color.TRANSPARENT);
		        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		        	description.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
			}
			catch (Resources.NotFoundException e)
			{
				description.setBackgroundColor(Color.LTGRAY);
				descriptionHtml = waypoint.description;
			}
			
			WebSettings settings = description.getSettings();
			settings.setDefaultTextEncodingName("utf-8");
			settings.setAllowFileAccess(true);
			Uri baseUrl = Uri.fromFile(new File(application.dataPath));
			description.loadDataWithBaseURL(baseUrl.toString() + "/", descriptionHtml, "text/html", "utf-8", null);
			view.findViewById(R.id.description_row).setVisibility(View.VISIBLE);
		}
	}
}
