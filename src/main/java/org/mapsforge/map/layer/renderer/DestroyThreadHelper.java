package org.mapsforge.map.layer.renderer;

import org.mapsforge.map.reader.MapDataStore;

public class DestroyThreadHelper
{
	public static void destroy(Thread thread, MapDataStore dataStore, DatabaseRenderer renderer)
	{
		new DestroyThread(thread, dataStore, renderer).start();
	}
}
