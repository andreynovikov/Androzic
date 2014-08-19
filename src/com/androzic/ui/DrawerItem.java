/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;

public class DrawerItem
{
	public Drawable icon;
	public String name;
	public Fragment fragment;
	public Intent action;

	public DrawerItem(String name)
	{
		this.name = name;
	}

	public DrawerItem(Drawable icon, String name, Fragment fragment)
	{
		this.icon = icon;
		this.name = name;
		this.fragment = fragment;
	}
	
	public DrawerItem(Drawable icon, String name, Intent action)
	{
		this.icon = icon;
		this.name = name;
		this.action = action;
	}
	
	public boolean isTitle()
	{
		return fragment == null && action == null;
	}

	public boolean isFragment()
	{
		return fragment != null;
	}

	public boolean isAction()
	{
		return action != null;
	}
}
