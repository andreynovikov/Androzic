/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.ui.preference;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.androzic.R;
import com.androzic.ui.QuickView;

public class HelpfulCheckBoxPreference extends CheckBoxPreference
{
	private OnClickListener helpClickListener;
	private CharSequence summary;
	private QuickView helpView;

	public HelpfulCheckBoxPreference(Context context, AttributeSet attrs)
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
				}
			});
		}
	}

	public void setOnHelpClickListener(OnClickListener l)
	{
		helpClickListener = l;
	}
}
