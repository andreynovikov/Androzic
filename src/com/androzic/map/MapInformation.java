package com.androzic.map;

import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;

public class MapInformation extends Activity
{
	private TextView information;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_mapinfo);
        
		Androzic application = (Androzic) getApplication();

        List<String> info = application.getCurrentMap().info();

        StringBuilder sb = new StringBuilder();
        for (String s : info)
        {
            sb.append(s);
            sb.append("\n");
        }

		information = (TextView) findViewById(R.id.mapinfo);
		information.setText(sb);
    }
    
	@Override
	protected void onResume()
	{
		super.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
	}

}
