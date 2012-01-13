package com.androzic;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class ErrorDialog extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.act_error);

    	String title = getIntent().getExtras().getString("title");
		setTitle(title);
		this.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_alert);

    	String message = getIntent().getExtras().getString("message");
        ((TextView) findViewById(R.id.message)).setText(Html.fromHtml(message));

        ((Button) findViewById(R.id.ok_button)).setOnClickListener(new OnClickListener() { public void onClick(View v) { finish(); } });
    }
}
