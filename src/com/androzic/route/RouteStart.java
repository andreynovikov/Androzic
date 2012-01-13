package com.androzic.route;

import com.androzic.Androzic;
import com.androzic.NavigationService;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

public class RouteStart extends Activity
{
    private Route route;
	private RadioButton forward;
	private RadioButton reverse;
	
	private int index;

	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_route_start);

        index = getIntent().getExtras().getInt("index");
        
		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);
		
		if (route.length() < 2)
		{
			Toast.makeText(getBaseContext(), R.string.err_shortroute, Toast.LENGTH_LONG).show();
			setResult(Activity.RESULT_CANCELED);
    		finish();
    		return;
		}
		
		this.setTitle(route.name);

		Waypoint start = route.getWaypoint(0);
		Waypoint end = route.getWaypoint(route.length()-1);

		forward = (RadioButton) findViewById(R.id.forward);
		forward.setText(start.name + " to "+end.name);
		reverse = (RadioButton) findViewById(R.id.reverse);
		reverse.setText(end.name + " to "+start.name);

		forward.setChecked(true);
		
	    Button navigate = (Button) findViewById(R.id.navigate_button);
	    navigate.setOnClickListener(saveOnClickListener);
    }

	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	int dir = forward.isChecked() ? NavigationService.DIRECTION_FORWARD : NavigationService.DIRECTION_REVERSE;
			setResult(Activity.RESULT_OK, new Intent().putExtra("index", index).putExtra("dir", dir));
    		finish();
        }
    };

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		route = null;
		forward = null;
		reverse = null;
	}

}
