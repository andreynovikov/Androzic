package com.androzic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.SimpleAdapter;

public class Credits extends ListActivity
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