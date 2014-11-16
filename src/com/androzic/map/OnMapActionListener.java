package com.androzic.map;

import com.androzic.map.Map;

public interface OnMapActionListener
{
	void onMapDetails(Map map);
	void onMapSelectedAtPosition(Map map);
	void onMapSelected(Map map);
}
