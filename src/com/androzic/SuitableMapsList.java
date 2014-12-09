package com.androzic;

import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.LightingColorFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.map.Map;
import com.androzic.map.OnMapActionListener;

public class SuitableMapsList extends DialogFragment implements OnItemClickListener
{
	private ListView listView;
	private OnMapActionListener mapActionsCallback;	
	private SuitableMapListAdapter adapter;

	private List<Map> maps;
	private String mapsPath;
	private Map currentMap;

	private LightingColorFilter disable = new LightingColorFilter(0xFF666666, 0xFF000000);

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
		View view = inflater.inflate(R.layout.dlg_suitable_maps_list, container, false);
		listView = (ListView) view.findViewById(android.R.id.list);
		View infoButton = view.findViewById(R.id.information_button);
		infoButton.setOnClickListener(onMapInformation);
		ImageButton editButton = (ImageButton) view.findViewById(R.id.edit_button);
		editButton.setColorFilter(disable);
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
		private double ppcm;

		public SuitableMapListAdapter(Context context)
		{
			mContext = context;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
			Resources resources = getResources();
			boolean scaleInMeters = settings.getBoolean(getString(R.string.pref_maplistscale), resources.getBoolean(R.bool.def_maplistscale));
			
			DisplayMetrics metrics = resources.getDisplayMetrics();
			ppcm = metrics.xdpi / 2.54;
			if (!scaleInMeters)
				ppcm *= 100;
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
				v = mInflater.inflate(R.layout.list_item_suitable_map, parent, false);
			}
			else
			{
				v = convertView;
			}
			Map map = getItem(position);
			TextView title = (TextView) v.findViewById(R.id.name);
			title.setText(map.title);
			TextView scale = (TextView) v.findViewById(R.id.scale);
			int mpcm = (int) (map.mpp * ppcm);
			double pct = 100 * currentMap.mpp / map.mpp;
			String fmt = pct < 0.1 ? "1:%,d (%.2f%%)" : pct < 1 ? "1:%,d (%.1f%%)" : "1:%,d (%.0f%%)";
			scale.setText(String.format(fmt, mpcm, pct));
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
			mapActionsCallback.onMapDetails(currentMap);
			dismiss();
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
			mapActionsCallback.onOpenMap();
			dismiss();
		}
	};
}
