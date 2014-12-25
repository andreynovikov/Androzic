package com.androzic.ui;

import java.util.ArrayList;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.androzic.R;

public class DrawerAdapter extends ArrayAdapter<DrawerItem>
{
	private static final int VIEW_TYPE_DIVIDER = 0;
	private static final int VIEW_TYPE_MAJOR = 1;
	private static final int VIEW_TYPE_MINOR = 2;

	private Context mContext;
	private int mSelectedItem;
	PorterDuffColorFilter mActiveIconFilter;

	public DrawerAdapter(Context mContext, ArrayList<DrawerItem> items)
	{
		super(mContext, R.layout.drawer_list_item, items);
		this.mContext = mContext;
		mActiveIconFilter = new PorterDuffColorFilter(mContext.getResources().getColor(R.color.drawer_selected_text), PorterDuff.Mode.SRC_IN);
	}

	public int getSelectedItem()
	{
		return mSelectedItem;
	}

	public void setSelectedItem(int selectedItem)
	{
		mSelectedItem = selectedItem;
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}

	@Override
	public boolean isEnabled(int position)
	{
		DrawerItem item = getItem(position);
		return item.type != DrawerItem.ItemType.DIVIDER;
	}

	@Override
	public int getViewTypeCount()
	{
		return 3;
	}

	@Override
	public int getItemViewType(int position)
	{
		DrawerItem item = getItem(position);
		if (item.type == DrawerItem.ItemType.DIVIDER)
		{
			return VIEW_TYPE_DIVIDER;
		}
		else if (item.minor)
		{
			return VIEW_TYPE_MINOR;
		}
		else
		{
			return VIEW_TYPE_MAJOR;
		}
	}

	@SuppressLint("NewApi")
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		DrawerItemHolder drawerHolder;
		DrawerItem item = getItem(position);

		int type = getItemViewType(position);
		if (convertView == null)
		{
			LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
			drawerHolder = new DrawerItemHolder();
			if (type == VIEW_TYPE_DIVIDER)
			{
				convertView = inflater.inflate(R.layout.drawer_list_divider, parent, false);
			}
			else if (type == VIEW_TYPE_MAJOR)
			{
				convertView = inflater.inflate(R.layout.drawer_list_item, parent, false);
				drawerHolder.icon = (ImageView) convertView.findViewById(R.id.drawerIcon);
				drawerHolder.name = (TextView) convertView.findViewById(R.id.drawerName);
			}
			else if (type == VIEW_TYPE_MINOR)
			{
				convertView = inflater.inflate(R.layout.drawer_list_item_aux, parent, false);
				drawerHolder.icon = (ImageView) convertView.findViewById(R.id.drawerIcon);
				drawerHolder.name = (TextView) convertView.findViewById(R.id.drawerName);
			}
			convertView.setTag(drawerHolder);
		}
		else
		{
			drawerHolder = (DrawerItemHolder) convertView.getTag();
		}

		if (type == VIEW_TYPE_MAJOR || type == VIEW_TYPE_MINOR)
		{
			Resources resources = mContext.getResources();
			drawerHolder.icon.setImageDrawable(item.icon);
			drawerHolder.name.setText(item.name);
			if (position == mSelectedItem)
			{
				drawerHolder.name.setTextColor(resources.getColor(R.color.drawer_selected_text));
				drawerHolder.name.setTypeface(Typeface.DEFAULT_BOLD);
				drawerHolder.icon.setColorFilter(mActiveIconFilter);
			}
			else
			{
				drawerHolder.name.setTextColor(resources.getColor(android.R.color.primary_text_dark));
				drawerHolder.name.setTypeface(Typeface.DEFAULT);
				drawerHolder.icon.setColorFilter(null);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				drawerHolder.name.setAllCaps(item.supplementary);
		}

		return convertView;
	}

	private static class DrawerItemHolder
	{
		TextView name;
		ImageView icon;
	}
}
