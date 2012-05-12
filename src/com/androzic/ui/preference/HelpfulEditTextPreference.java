package com.androzic.ui.preference;

import com.androzic.R;
import com.androzic.ui.QuickView;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class HelpfulEditTextPreference extends EditTextPreference
{
	private OnClickListener helpClickListener;
	private CharSequence summary;
	private QuickView helpView;

	public HelpfulEditTextPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		summary = getSummary();
		setSummary(null);
	}

	@Override
	protected void onBindView(View view)
	{
		super.onBindView(view);
		if (summary != null)
		{
			helpView = new QuickView(getContext());
			helpView.setText(summary);
			
			final ImageView helpImage = new ImageView(getContext());
			final ViewGroup widgetFrameView = ((ViewGroup) view.findViewById(android.R.id.widget_frame));
			if (widgetFrameView == null)
				return;
			widgetFrameView.setVisibility(View.VISIBLE);
			final int rightPaddingDip = android.os.Build.VERSION.SDK_INT < 14 ? 8 : 5;
			final float mDensity = getContext().getResources().getDisplayMetrics().density;
			if (widgetFrameView instanceof LinearLayout)
			{
				((LinearLayout) widgetFrameView).setOrientation(LinearLayout.HORIZONTAL);
			}
			widgetFrameView.addView(helpImage, 0);
			helpImage.setImageResource(R.drawable.ic_menu_info_details);
			helpImage.setPadding(helpImage.getPaddingLeft(), helpImage.getPaddingTop(), (int) (mDensity * rightPaddingDip), helpImage.getPaddingBottom());
			helpImage.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (helpClickListener != null)
						helpClickListener.onClick(helpImage);
					else
						helpView.show(helpImage);
//						Toast.makeText(getContext(), summary, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	public void setOnHelpClickListener(OnClickListener l)
	{
		helpClickListener = l;
	}
}
