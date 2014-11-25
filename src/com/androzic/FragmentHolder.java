package com.androzic;

import android.support.v4.app.Fragment;

import com.shamanland.fab.FloatingActionButton;

public interface FragmentHolder
{
	public void addFragment(Fragment fragment, String tag);
	public FloatingActionButton enableActionButton();
	public void disableActionButton();
}
