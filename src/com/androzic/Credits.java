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

package com.androzic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.actionbarsherlock.app.SherlockListActivity;

import android.os.Bundle;
import android.widget.SimpleAdapter;

public class Credits extends SherlockListActivity
{
	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		String[] names = getResources().getStringArray(R.array.credit_names);
		String[] merits = getResources().getStringArray(R.array.credit_merits);

		List<Map<String, String>> data = new ArrayList<Map<String, String>>();
		Map<String, String> group;
		
		for (int i = 0; i < names.length; i++)
		{
			group = new HashMap<String, String>();
			group.put("NAME", names[i]);
			group.put("MERIT", merits[i]);
			data.add(group);
		}

		setListAdapter(new SimpleAdapter(Credits.this, data, android.R.layout.simple_list_item_2, new String[] { "NAME", "MERIT" }, new int[]{ android.R.id.text1, android.R.id.text2 } ));
		getListView().setItemsCanFocus(false);
	}
}