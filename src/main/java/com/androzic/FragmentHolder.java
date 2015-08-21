package com.androzic;

import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;

public interface FragmentHolder
{
	public void addFragment(Fragment fragment, String tag);
	public FloatingActionButton enableActionButton();
	public void disableActionButton();
}
