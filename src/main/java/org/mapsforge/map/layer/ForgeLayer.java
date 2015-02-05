package org.mapsforge.map.layer;

import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Point;

public class ForgeLayer extends Layer
{
	public ForgeLayer(Redrawer redrawer)
	{
		assign(redrawer);
	}

	@Override
	public void draw(BoundingBox boundingBox, byte zoomLevel, Canvas canvas, Point topLeftPoint)
	{
	}
}

