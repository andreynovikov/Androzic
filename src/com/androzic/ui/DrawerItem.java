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
	public static enum ItemType
	{
		DIVIDER, INTENT, FRAGMENT, ACTION
	}
	
	public ItemType type;
	public Drawable icon;
	public String name;
	public Fragment fragment;
	public Intent intent;
	public Runnable action;
	public boolean minor = false;
	public boolean supplementary = false;

	public DrawerItem()
	{
		this.type = ItemType.DIVIDER;
	}

	public DrawerItem(Drawable icon, String name, Fragment fragment)
	{
		this.type = ItemType.FRAGMENT;
		this.icon = icon;
		this.name = name;
		this.fragment = fragment;
	}
	
	public DrawerItem(Drawable icon, String name, Intent intent)
	{
		this.type = ItemType.INTENT;
		this.icon = icon;
		this.name = name;
		this.intent = intent;
	}

	public DrawerItem(Drawable icon, String name, Runnable action)
	{
		this.type = ItemType.ACTION;
		this.icon = icon;
		this.name = name;
		this.action = action;
	}

	public DrawerItem makeMinor()
	{
		this.minor = true;
		return this;
	}

	public DrawerItem makeSupplementary()
	{
		this.supplementary = true;
		return this;
	}
}
