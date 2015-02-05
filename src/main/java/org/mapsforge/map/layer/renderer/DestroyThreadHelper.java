package org.mapsforge.map.layer.renderer;

import org.mapsforge.map.reader.MapDatabase;

public class DestroyThreadHelper
{
	public static void destroy(Thread thread, MapDatabase mapDatabase, DatabaseRenderer renderer)
	{
		new DestroyThread(thread, mapDatabase, renderer).start();
	}
}
