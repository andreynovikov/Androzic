package com.androzic.waypoint;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.androzic.MainActivity;
import com.androzic.R;
import com.androzic.util.WaypointFileHelper;

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

		try
		{
			File file = new File(filepath);
			int count = WaypointFileHelper.loadFile(file);
			if (count > 0)
				setResult(Activity.RESULT_OK, new Intent().putExtra("count", count));
			else
				setResult(Activity.RESULT_CANCELED, new Intent());
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
