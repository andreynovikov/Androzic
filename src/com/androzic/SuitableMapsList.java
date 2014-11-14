package com.androzic;

import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.map.Map;

public class SuitableMapsList extends DialogFragment implements OnItemClickListener
{
	private ListView listView;
	private OnMapActionListener mapActionsCallback;	
	private SuitableMapListAdapter adapter;

	private List<Map> maps;
	private String mapsPath;
	private Map currentMap;

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
		View view = inflater.inflate(R.layout.dlg_suitable_maps_list, container);
		listView = (ListView) view.findViewById(android.R.id.list);
		View infoButton = view.findViewById(R.id.information_button);
		infoButton.setOnClickListener(onMapInformation);
		View editButton = view.findViewById(R.id.edit_button);
		editButton.setOnClickListener(onMapEdit);
		View openButton = view.findViewById(R.id.open_button);
		openButton.setOnClickListener(onMapOpen);
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

		currentMap = application.getCurrentMap();
		mapsPath = application.getMapPath();

		adapter = new SuitableMapListAdapter(getActivity());
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

	public class SuitableMapListAdapter extends BaseAdapter
	{
		private Context mContext;
		private LayoutInflater mInflater;
		private int mItemLayout;

		public SuitableMapListAdapter(Context context)
		{
			mContext = context;
			mItemLayout = R.layout.list_item_suitable_map;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public Map getItem(int position)
		{
			return maps.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return maps.get(position).id;
		}

		@Override
		public int getCount()
		{
			return maps.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View v;
			if (convertView == null)
			{
				v = mInflater.inflate(mItemLayout, parent, false);
			}
			else
			{
				v = convertView;
			}
			Map map = getItem(position);
			TextView title = (TextView) v.findViewById(R.id.name);
			title.setText(map.title);
			TextView scale = (TextView) v.findViewById(R.id.scale);
			scale.setText(String.format("%.0f%% (%.0f)", 100 * currentMap.mpp / map.mpp, map.mpp));
			TextView path = (TextView) v.findViewById(R.id.filename);
			if (map.mappath != null)
			{
				String filepath = map.mappath.startsWith(mapsPath) ? map.mappath.substring(mapsPath.length() + 1, map.mappath.length()) : map.mappath;
				path.setText(filepath);
			}
			else
			{
				path.setText("");
			}

			Resources resources = mContext.getResources();
			int color;
			if (map.id == currentMap.id)
			{
				color = resources.getColor(R.color.drawer_selected_text);
				title.setTypeface(Typeface.DEFAULT_BOLD);
			}
			else
			{
				color = resources.getColor(android.R.color.primary_text_dark);
				title.setTypeface(Typeface.DEFAULT);
			}
			title.setTextColor(color);
			scale.setTextColor(color);
			path.setTextColor(color);

			return v;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}
	}

	private View.OnClickListener onMapInformation = new View.OnClickListener() {

		@Override
		public void onClick(View v)
		{
		}
	};

	private View.OnClickListener onMapEdit = new View.OnClickListener() {

		@Override
		public void onClick(View v)
		{
		}
	};

	private View.OnClickListener onMapOpen = new View.OnClickListener() {

		@Override
		public void onClick(View v)
		{
		}
	};
}
