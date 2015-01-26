package com.androzic.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.PopupWindow;

import com.androzic.MapFragment;
import com.androzic.R;
import com.androzic.SuitableMapsList;
import com.androzic.waypoint.WaypointDetails;
import com.androzic.waypoint.WaypointList;
import com.androzic.waypoint.WaypointProperties;

public class TooltipManager
{
	private static long currentState = 0;
	private static Context context;
	private static TooltipPopup popup;

	/**
	 * Delay before first tooltip should be displayed after user opens the screen
	 */
	public static final long TOOLTIP_DELAY = 10000; // 10 seconds
	/**
	 * Delay before first tooltip should be displayed after user opens the screen
	 * on which she does not stay for long time
	 */
	public static final long TOOLTIP_DELAY_SHORT = 1000; // 1 second
	/**
	 * Pause between first and subsequent tooltips for the same screen
	 */
	public static final long TOOLTIP_PERIOD = 30000; // 30 seconds

	public static final long TOOLTIP_MAP_BUTTONS                    = 0x0000000000000001L;
	public static final long TOOLTIP_QUICK_ZOOM                     = 0x0000000000000002L;
	public static final long TOOLTIP_SWITCH_MAP                     = 0x0000000000000004L;
	public static final long TOOLTIP_LOAD_MAP                       = 0x0000000000000008L;
	public static final long TOOLTIP_CURRENT_LOCATION               = 0x0000000000000010L;
	public static final long TOOLTIP_UTM_ZONE                       = 0x0000000000000020L;
	public static final long TOOLTIP_DATA_LIST                      = 0x0000000000000040L;
	public static final long TOOLTIP_WAYPOINT_COORDINATES           = 0x0000000000000080L;
	public static final long TOOLTIP_BUSY                           = 0x8000000000000000L;

	@Nullable
	private static String getTooltipString(long tooltip)
	{
		Resources resources = context.getResources();
		if (tooltip == TOOLTIP_MAP_BUTTONS)
			return resources.getString(R.string.showcase_map_buttons);
		if (tooltip == TOOLTIP_QUICK_ZOOM)
			return resources.getString(R.string.showcase_quick_zoom);
		if (tooltip == TOOLTIP_SWITCH_MAP)
			return resources.getString(R.string.showcase_switch_map);
		if (tooltip == TOOLTIP_LOAD_MAP)
			return resources.getString(R.string.showcase_load_map);
		if (tooltip == TOOLTIP_CURRENT_LOCATION)
			return resources.getString(R.string.showcase_current_location);
		if (tooltip == TOOLTIP_UTM_ZONE)
			return resources.getString(R.string.showcase_utm_zone);
		if (tooltip == TOOLTIP_DATA_LIST)
			return resources.getString(R.string.showcase_data_list);
		if (tooltip == TOOLTIP_WAYPOINT_COORDINATES)
			return resources.getString(R.string.showcase_waypoint_coordinates);
		return null;
	}

	public static void initialize(Context context)
	{
		TooltipManager.context = context;
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		currentState = settings.getLong(context.getResources().getString(R.string.ui_tooltips_state), 0L);
	}

	/**
	 * Returns next unseen tooltip for specified screen
	 * @param screen UI screen tag
	 * @return tooltip ID or 0 if there are no more tooltips or busy flag
	 * if some tooltip is already displayed
	 */
	public static long getTooltip(String screen)
	{
		if (context == null)
			throw new IllegalStateException("TooltipManager not initialized");
		if (popup != null)
			return TOOLTIP_BUSY;
		if (MapFragment.TAG.equals(screen))
		{
			if ((currentState & TOOLTIP_MAP_BUTTONS) == 0L)
				return TOOLTIP_MAP_BUTTONS;
			if ((currentState & TOOLTIP_SWITCH_MAP) == 0L)
				return TOOLTIP_SWITCH_MAP;
			if ((currentState & TOOLTIP_CURRENT_LOCATION) == 0L)
				return TOOLTIP_CURRENT_LOCATION;
			if ((currentState & TOOLTIP_QUICK_ZOOM) == 0L)
				return TOOLTIP_QUICK_ZOOM;
		}
		else if (SuitableMapsList.TAG.equals(screen))
		{
			if ((currentState & TOOLTIP_LOAD_MAP) == 0L)
				return TOOLTIP_LOAD_MAP;
		}
		else if (WaypointDetails.TAG.equals(screen))
		{
			if ((currentState & TOOLTIP_WAYPOINT_COORDINATES) == 0L)
				return TOOLTIP_WAYPOINT_COORDINATES;
		}
		else if (WaypointProperties.TAG.equals(screen))
		{
			if ((currentState & TOOLTIP_UTM_ZONE) == 0L)
				return TOOLTIP_UTM_ZONE;
		}
		else if (WaypointList.TAG.equals(screen))
		{
			if ((currentState & TOOLTIP_DATA_LIST) == 0L)
				return TOOLTIP_DATA_LIST;
		}
		return 0L;
	}

	/**
	 * Shows tooltip at the specified anchor view. State is saved on tooltip dismiss.
	 * @param tooltip tooltip ID
	 * @param anchor anchor view
	 */
	public static void showTooltip(final long tooltip, View anchor)
	{
		if (context == null)
			throw new IllegalStateException("TooltipManager not initialized");
		popup = new TooltipPopup(context, getTooltipString(tooltip));
		popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
			@Override
			public void onDismiss()
			{
				currentState |= tooltip;
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
				editor.putLong(context.getResources().getString(R.string.ui_tooltips_state), currentState);
				editor.commit();
				popup = null;
			}
		});
		popup.show(anchor);
	}

	public static void dismiss()
	{
		if (popup != null)
		{
			popup.setOnDismissListener(null);
			popup.dismiss();
			popup = null;
		}
	}
}
