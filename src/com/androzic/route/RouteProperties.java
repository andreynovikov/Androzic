package com.androzic.route;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.ui.ColorPickerDialog;
import com.androzic.ui.OnColorChangedListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class RouteProperties extends Activity
{
	private Route route;
	
	private TextView name;
	//private TextView description;
	private CheckBox show;
	private TextView color;
	private Button colorselect;
	
	private int colorValue;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // FIXME Should have its own layout
        setContentView(R.layout.act_track_properties);

        int index = getIntent().getExtras().getInt("index");
        
		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);
		
		name = (TextView) findViewById(R.id.name_text);
		name.setText(route.name);
		/*
		description = (TextView) findViewById(R.id.description_text);
		description.setText(track.description);
		*/
		show = (CheckBox) findViewById(R.id.show_check);
        show.setChecked(route.show);
        color = (TextView) findViewById(R.id.color_text);
        colorValue = route.lineColor;
        color.setBackgroundColor(route.lineColor);
	    colorselect = (Button) findViewById(R.id.color_button);
	    colorselect.setOnClickListener(colorOnClickListener);
		
	    Button save = (Button) findViewById(R.id.done_button);
	    save.setOnClickListener(saveOnClickListener);

	    Button cancel = (Button) findViewById(R.id.cancel_button);
	    cancel.setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
    }

	private OnClickListener saveOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	try
        	{
        		route.name = name.getText().toString();
        		//route.description = description.getText().toString();
        		route.show = show.isChecked();
        		route.lineColor = colorValue;
    			setResult(Activity.RESULT_OK);
        		finish();
        	}
        	catch (Exception e)
        	{
    			Toast.makeText(getBaseContext(), "Error saving route", Toast.LENGTH_LONG).show();        		
        	}
        }
    };

	private OnClickListener colorOnClickListener = new OnClickListener()
	{
        public void onClick(View v)
        {
        	new ColorPickerDialog(RouteProperties.this, colorChangeListener, colorValue, route.lineColor, false).show();
        }
    };

	private OnColorChangedListener colorChangeListener = new OnColorChangedListener()
	{

		@Override
		public void colorChanged(int newColor)
		{
			colorValue = newColor;
			color.setBackgroundColor(newColor);
		}
		
	};

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		route = null;
		name = null;
		show = null;
		color = null;
		colorselect = null;
	}

}
