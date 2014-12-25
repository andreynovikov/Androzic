/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ScrollView;

public class PreferencesHelpDialog extends DialogFragment
{
	private ScrollView rootView;
	private int sectionId;

    public PreferencesHelpDialog()
    {
        throw new RuntimeException("Unimplemented initialization context");
    }

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
	public PreferencesHelpDialog(int sectionId)
	{
		this.sectionId = sectionId;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		rootView = (ScrollView) inflater.inflate(R.layout.dlg_preferences_help, container, false);
		Log.e("DLG", "onCreateView");
		return rootView;
	}

	@SuppressLint("NewApi")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
		{
			dialog.setOnShowListener(new DialogInterface.OnShowListener(){
				@Override
				public void onShow(DialogInterface dialog)
				{
					scrollToView(rootView, rootView.findViewById(sectionId));
				}});
		}
		return dialog;
	}

	public static void scrollToView(final ScrollView scrollView, final View view)
	{
		view.requestFocus();

		final Rect scrollBounds = new Rect();
		scrollView.getHitRect(new Rect());
		if (!view.getLocalVisibleRect(scrollBounds))
		{
			new Handler().post(new Runnable() {
				@Override
				public void run()
				{
					scrollView.smoothScrollTo(0, view.getBottom());
				}
			});
		}
	}
}
