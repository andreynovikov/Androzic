package com.androzic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class SuitableMapsList extends DialogFragment implements OnItemClickListener
{
	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";

	private ListView listView;
	private List<com.androzic.map.Map> maps;
	private OnMapActionListener mapActionsCallback;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		return dialog;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.list_with_empty_view, container);
		listView = (ListView) view.findViewById(android.R.id.list);
		return view;
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			mapActionsCallback = (OnMapActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnMapActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		Androzic application = Androzic.getApplication();
		double[] loc = application.getMapCenter();
		maps = application.getMaps(loc);
		List<Map<String, String>> mapData = new ArrayList<Map<String, String>>();

		Map<String, String> group;

		String mappath = application.getMapPath();

		for (com.androzic.map.Map map : maps)
		{
			String fn = new String(map.mappath);
			if (fn.startsWith(mappath))
			{
				fn = fn.substring(mappath.length() + 1);
			}
			group = new HashMap<String, String>();
			group.put(KEY_NAME, map.title);
			group.put(KEY_DESC, String.format("MPP: %.2f - %s", map.mpp, fn));
			mapData.add(group);
		}

		SimpleAdapter adapter = new SimpleAdapter(getActivity(), mapData, android.R.layout.simple_list_item_2, new String[] { KEY_NAME, KEY_DESC }, new int[]{ android.R.id.text1, android.R.id.text2 } );
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		mapActionsCallback.onMapSelectedAtPosition(maps.get(position));
		dismiss();
	}

}
