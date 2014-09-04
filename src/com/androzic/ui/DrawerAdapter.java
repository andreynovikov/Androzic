package com.androzic.ui;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
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
	private static final int VIEW_TYPE_TITLE = 1;
	private static final int VIEW_TYPE_ACTION = 2;

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
		return item.type == DrawerItem.ItemType.ACTION || item.type == DrawerItem.ItemType.FRAGMENT;
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
		if (item.type == DrawerItem.ItemType.TITLE)
		{
			return VIEW_TYPE_TITLE;
		}
		else
		{
			return VIEW_TYPE_ACTION;
		}
	}

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
			else if (type == VIEW_TYPE_TITLE)
			{
				convertView = inflater.inflate(R.layout.drawer_list_title, parent, false);
				drawerHolder.title = (TextView) convertView.findViewById(R.id.drawerTitle);
			}
			else if (type == VIEW_TYPE_ACTION)
			{
				convertView = inflater.inflate(R.layout.drawer_list_item, parent, false);
				drawerHolder.icon = (ImageView) convertView.findViewById(R.id.drawerIcon);
				drawerHolder.name = (TextView) convertView.findViewById(R.id.drawerName);
			}
			convertView.setTag(drawerHolder);
		}
		else
		{
			drawerHolder = (DrawerItemHolder) convertView.getTag();
		}

		if (type == VIEW_TYPE_DIVIDER)
		{
		}
		else if (type == VIEW_TYPE_TITLE)
		{
			drawerHolder.title.setText(item.name);
		}
		else if (type == VIEW_TYPE_ACTION)
		{
			drawerHolder.icon.setImageDrawable(item.icon);
			drawerHolder.name.setText(item.name);
			if (position == mSelectedItem)
			{
				drawerHolder.name.setTextColor(mContext.getResources().getColor(R.color.drawer_selected_text));
				drawerHolder.name.setTypeface(Typeface.DEFAULT_BOLD);
				drawerHolder.icon.setColorFilter(mActiveIconFilter);
			}
			else
			{
				drawerHolder.name.setTextColor(mContext.getResources().getColor(android.R.color.primary_text_dark));
				drawerHolder.name.setTypeface(Typeface.DEFAULT);
				drawerHolder.icon.setColorFilter(null);
			}
		}
		convertView.setBackgroundColor(item.supplementary ? 0x00000000 : 0xFF111111);

		return convertView;
	}

	private static class DrawerItemHolder
	{
		TextView title;
		TextView name;
		ImageView icon;
	}
}
