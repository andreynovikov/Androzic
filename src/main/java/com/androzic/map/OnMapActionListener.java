package com.androzic.map;

public interface OnMapActionListener
{
	void onOpenMap();
	void onMapDetails(BaseMap map);
	void onMapSelectedAtPosition(BaseMap map);
	void onMapSelected(BaseMap map);
}
