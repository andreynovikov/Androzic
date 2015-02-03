/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2015  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.map.forge;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.util.DisplayMetrics;

import com.androzic.BaseApplication;
import com.androzic.map.OnMapTileStateChangeListener;
import com.androzic.map.TileMap;
import com.androzic.ui.Viewport;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Redrawer;
import org.mapsforge.map.layer.TilePosition;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.cache.TwoLevelTileCache;
import org.mapsforge.map.layer.queue.Job;
import org.mapsforge.map.layer.queue.JobQueue;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.DestroyThread;
import org.mapsforge.map.layer.renderer.MapWorker;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ForgeMap extends TileMap implements Redrawer
{
	private static final long serialVersionUID = 1L;

	@SuppressWarnings("SpellCheckingInspection")
	public static final byte[] MAGIC = "mapsforge binary OSM".getBytes();

	private float textScale = 1f;

	private MapDatabase mapDatabase;
	private LatLong mapCenter;

	private transient File mapFile;
	private transient DatabaseRenderer databaseRenderer;
	private transient MapWorker mapWorker;
	private transient JobQueue<RendererJob> jobQueue;
	private transient TileCache tileCache;
	private transient MapViewPosition mapViewPosition;
	private transient final DisplayModel displayModel = new DisplayModel();

	public ForgeMap(String path)
	{
		super(path);

		mapFile = new File(path);
		mapDatabase = new MapDatabase();
		mapDatabase.openFile(mapFile);
		MapFileInfo mapInfo = mapDatabase.getMapFileInfo();
		mapDatabase.closeFile();
		mapViewPosition = new MapViewPosition(displayModel);

		// Remove extention
		name = mapFile.getName();
		int e = name.lastIndexOf(".map");
		if (e > 0)
			name = name.substring(0, e);

		// And capitalize first letter
		StringBuilder nameSb = new StringBuilder(name.toLowerCase());
		nameSb.setCharAt(0, Character.toUpperCase(nameSb.charAt(0)));
		name = nameSb.toString();

		initializeZooms((byte) 0, (byte) 24, mapInfo.startZoomLevel);
		mapViewPosition.setZoomLevelMin((byte) 0);
		mapViewPosition.setZoomLevelMax((byte) 24);

		setCornersAmount(4);
		cornerMarkers[0].lat = mapInfo.boundingBox.maxLatitude;
		cornerMarkers[0].lon = mapInfo.boundingBox.minLongitude;
		cornerMarkers[1].lat = mapInfo.boundingBox.maxLatitude;
		cornerMarkers[1].lon = mapInfo.boundingBox.maxLongitude;
		cornerMarkers[2].lat = mapInfo.boundingBox.minLatitude;
		cornerMarkers[2].lon = mapInfo.boundingBox.maxLongitude;
		cornerMarkers[3].lat = mapInfo.boundingBox.minLatitude;
		cornerMarkers[3].lon = mapInfo.boundingBox.minLongitude;
		int[] xy = new int[2];
		for (int i = 0; i < 4; i++)
		{
			getXYByLatLon(cornerMarkers[i].lat, cornerMarkers[i].lon, xy);
			cornerMarkers[i].x = xy[0];
			cornerMarkers[i].y = xy[1];
		}

		mapCenter = mapInfo.startPosition;

		updateTitle();
		recalculateMPP();
	}

	@Override
	public synchronized void activate(OnMapTileStateChangeListener listener, DisplayMetrics metrics, double zoom) throws Throwable
	{
		mapDatabase.openFile(mapFile);
		tileCache = getCache();
		databaseRenderer = new DatabaseRenderer(mapDatabase, AndroidGraphicFactory.INSTANCE, tileCache);

		jobQueue = new JobQueue<>(mapViewPosition, displayModel);
		ForgeLayer layer = new ForgeLayer(this);
		mapWorker = new MapWorker(tileCache, jobQueue, databaseRenderer, layer);
		mapWorker.start();
		super.activate(listener, metrics, zoom);
	}

	@Override
	public synchronized void deactivate()
	{
		super.deactivate();

		mapWorker.pause();
		new DestroyThread(mapWorker, mapDatabase, databaseRenderer).start();
		tileCache.destroy();
	}

	@Override
	public boolean drawMap(Viewport viewport, boolean cropBorder, boolean drawBorder, Canvas c) throws OutOfMemoryError
	{
		if (!isActive)
			return false;

		lastLatitude = viewport.mapCenter[0];
		recalculateMPP();
		mapViewPosition.setCenter(new LatLong(viewport.mapCenter[0], viewport.mapCenter[1]));

		int[] map_xy = new int[2];
		getXYByLatLon(viewport.mapCenter[0], viewport.mapCenter[1], map_xy);
		map_xy[0] -= viewport.lookAheadXY[0];
		map_xy[1] -= viewport.lookAheadXY[1];

		Path clipPath = new Path();

		if (cropBorder || drawBorder)
			mapClipPath.offset(-map_xy[0] + viewport.width / 2, -map_xy[1] + viewport.height / 2, clipPath);

		float tile_wh = (float) (TILE_SIZE * dynZoom);

		int osm_x = (int) (map_xy[0] / tile_wh);
		int osm_y = (int) (map_xy[1] / tile_wh);

		int tiles_per_x = Math.round(viewport.width * 1.f / tile_wh / 2 + .5f);
		int tiles_per_y = Math.round(viewport.height * 1.f / tile_wh / 2 + .5f);

		int c_min = osm_x - tiles_per_x;
		int c_max = osm_x + tiles_per_x + 1;

		int r_min = osm_y - tiles_per_y;
		int r_max = osm_y + tiles_per_y + 1;

		boolean result = true;

		if (c_min < 0)
		{
			c_min = 0;
			result = false;
		}
		if (r_min < 0)
		{
			r_min = 0;
			result = false;
		}
		if (c_max > Math.pow(2.0, srcZoom))
		{
			c_max = (int) (Math.pow(2.0, srcZoom));
			result = false;
		}
		if (r_max > Math.pow(2.0, srcZoom))
		{
			r_max = (int) (Math.pow(2.0, srcZoom));
			result = false;
		}

		float w2mx = viewport.width / 2 - map_xy[0];
		float h2my = viewport.height / 2 - map_xy[1];
		int twh = Math.round(tile_wh);

		int i = osm_y, j = osm_x, dx = 0, dy = -1;
		int t = Math.max(c_max - c_min + 1, r_max - r_min + 1);
		int maxI = t * t;

		List<TilePosition> tilePositions = new ArrayList<>();
		for (int k = 0; k < maxI; k++)
		{
			if (c_min <= j && j <= c_max && r_min <= i && i <= r_max)
			{
				tilePositions.add(new TilePosition(new Tile(j, i, srcZoom, TILE_SIZE), new Point(j, i)));
			}

			int x = j - osm_x, y = i - osm_y;
			if ((x == y) || ((x < 0) && (x == -y)) || ((x > 0) && (x == 1 - y)))
			{
				t = dx;
				dx = -dy;
				dy = t;
			}
			j += dx;
			i += dy;
		}

		Set<Job> jobs = new HashSet<>();
		for (TilePosition tilePosition : tilePositions)
		{
			RendererJob job = new RendererJob(tilePosition.tile, mapFile, InternalRenderTheme.OSMARENDER, displayModel, textScale, false, false);
			jobs.add(job);
		}
		tileCache.setWorkingSet(jobs);

		for (int k = tilePositions.size() - 1; k >= 0; k--)
		{
			TilePosition tilePosition = tilePositions.get(k);
			Point point = tilePosition.point;
			Tile mapTile = tilePosition.tile;

			Bitmap tile = getTile(mapTile);
			if (tile != null && !tile.isRecycled())
			{
				if (tile.getWidth() != twh)
					tile = Bitmap.createScaledBitmap(tile, twh, twh, true);
				float tx = w2mx + (float) (point.x) * tile_wh;
				float ty = h2my + (float) (point.y) * tile_wh;
				c.drawBitmap(tile, tx, ty, null);
			}
		}

		if (drawBorder && borderPaint != null)
			c.drawPath(clipPath, borderPaint);

		return result;
	}

	@Override
	protected Bitmap getTile(int x, int y) throws OutOfMemoryError
	{
		return null;
	}

	public Bitmap getTile(Tile tile) throws OutOfMemoryError
	{
		org.mapsforge.core.graphics.Bitmap bitmap = loadTile(tile);
		Bitmap tileBitmap = null;
		if (bitmap != null)
			tileBitmap = AndroidGraphicFactory.getBitmap(bitmap);

		if (tileBitmap == null)
			tileBitmap = generateTile(tile);

		if (tileBitmap != null)
		{
			if (dynZoom != 1.0)
			{
				int ss = (int) (dynZoom * TILE_SIZE);
				tileBitmap = Bitmap.createScaledBitmap(tileBitmap, ss, ss, true);
			}
		}
		return tileBitmap;
	}

	public org.mapsforge.core.graphics.Bitmap loadTile(Tile tile)
	{
		try
		{
			RendererJob job = new RendererJob(tile, mapFile, InternalRenderTheme.OSMARENDER, displayModel, textScale, false, false);
			org.mapsforge.core.graphics.Bitmap bitmap = tileCache.getImmediately(job);
			if (bitmap == null && !tileCache.containsKey(job))
				jobQueue.add(job);

			jobQueue.notifyWorkers();

			return bitmap;
		} catch (IllegalArgumentException e)
		{
			//TODO Check X,Y values for limits
			e.printStackTrace();
		}
		return null;
	}

	public Bitmap generateTile(Tile tile)
	{
		byte parentTileZoom = (byte) (tile.zoomLevel - 1);
		int parentTileX = tile.tileX / 2, parentTileY = tile.tileY / 2, scale = 2;

		// Search for parent tile
		for (; parentTileZoom >= 0; parentTileZoom--, parentTileX /= 2, parentTileY /= 2, scale *= 2)
		{
			Tile parentTile;
			try
			{
				parentTile = new Tile(parentTileX, parentTileY, parentTileZoom, TILE_SIZE);
			} catch (IllegalArgumentException e)
			{
				//TODO Check X,Y values for limits
				e.printStackTrace();
				continue;
			}
			RendererJob job = new RendererJob(parentTile, mapFile, InternalRenderTheme.OSMARENDER, displayModel, textScale, false, false);
			org.mapsforge.core.graphics.Bitmap bitmap = tileCache.getImmediately(job);
			if (bitmap == null)
				continue;
			Bitmap tileBitmap = AndroidGraphicFactory.getBitmap(bitmap);
			if (tileBitmap != null && scale <= tileBitmap.getWidth() && scale <= tileBitmap.getHeight())
			{
				Matrix matrix = new Matrix();
				matrix.postScale(scale, scale);

				int miniTileWidth = tileBitmap.getWidth() / scale;
				int miniTileHeight = tileBitmap.getHeight() / scale;
				int fromX = (tile.tileX % scale) * miniTileWidth;
				int fromY = (tile.tileY % scale) * miniTileHeight;

				// Create mini bitmap which will be stretched to tile
				Bitmap miniTileBitmap = Bitmap.createBitmap(tileBitmap, fromX, fromY, miniTileWidth, miniTileHeight);

				// Create tile bitmap from mini bitmap
				tileBitmap = Bitmap.createBitmap(miniTileBitmap, 0, 0, miniTileWidth, miniTileHeight, matrix, false);
				miniTileBitmap.recycle();
				return tileBitmap;
			}
		}
		return null;
	}

	@Override
	public synchronized void setZoom(double z)
	{
		super.setZoom(z);
		mapViewPosition.setZoomLevel(srcZoom);
	}

	@Override
	public void recalculateCache()
	{
		/*
		if (cache != null)
			cache.destroy();
		int nx = (int) Math.ceil(displayWidth * 1. / (TILE_WIDTH * dynZoom)) + 2;
		int ny = (int) Math.ceil(displayHeight * 1. / (TILE_HEIGHT * dynZoom)) + 2;
		int cacheSize = nx * ny;
		cache = new TileRAMCache(cacheSize);
		*/
	}

	private TileCache getCache()
	{
		//FIXME Cache size
		TileCache firstLevelTileCache = new InMemoryTileCache(80);

		BaseApplication application = BaseApplication.getApplication();
		if (application == null)
			return firstLevelTileCache;

		File cache = application.getCacheDir();
		if (cache == null) // cache is not available now
			return firstLevelTileCache;

		File cacheDirectory = new File(cache, "mapsforge");
		if (cacheDirectory.exists() || cacheDirectory.mkdir())
		{
			int tileCacheFiles = 2000; //estimateSizeOfFileSystemCache(cacheDirectoryName, firstLevelSize, tileSize);
			if (cacheDirectory.canWrite() && tileCacheFiles > 0)
			{
				try
				{
					TileCache secondLevelTileCache = new FileSystemTileCache(tileCacheFiles, cacheDirectory, AndroidGraphicFactory.INSTANCE, false, 0);
					return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
				} catch (IllegalArgumentException e)
				{
					e.printStackTrace();
				}
			}
		}
		return firstLevelTileCache;
	}

	@Override
	public void getMapCenter(double[] center)
	{
		center[0] = mapCenter.latitude;
		center[1] = mapCenter.longitude;
	}

	public List<String> info()
	{
		ArrayList<String> info = new ArrayList<>();

		info.add("title: " + title);
		info.add("path: " + path);
		info.add("minimum zoom: " + minZoom);
		info.add("maximum zoom: " + maxZoom);
		if (projection != null)
		{
			info.add("projection: " + projection.getName() + " (" + projection.getEPSGCode() + ")");
			info.add("\t" + projection.getPROJ4Description());
		}
		info.add("datum: " + datum);
		info.add("scale (mpp): " + mpp);

		return info;
	}

	@Override
	public void redrawLayers()
	{
		listener.onTileObtained();
	}

	protected class ForgeLayer extends Layer
	{
		public ForgeLayer(Redrawer redrawer)
		{
			assign(redrawer);
		}
		@Override
		public void draw(BoundingBox boundingBox, byte zoomLevel, org.mapsforge.core.graphics.Canvas canvas, Point topLeftPoint)
		{
		}
	}
}
