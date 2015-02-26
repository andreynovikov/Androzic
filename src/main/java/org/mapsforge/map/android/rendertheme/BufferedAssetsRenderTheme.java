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

package org.mapsforge.map.android.rendertheme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;

import android.content.Context;

/**
 * Thread safe AssetRenderTheme is an XmlRenderTheme that is picked up from the Android apk assets folder.
 */
public class BufferedAssetsRenderTheme implements XmlRenderTheme
{
	private static final long serialVersionUID = 1L;

	private final String assetName;
	private final XmlRenderThemeMenuCallback menuCallback;
	private final String relativePathPrefix;
	private final byte[] data;

	public BufferedAssetsRenderTheme(Context context, String relativePathPrefix, String fileName, XmlRenderThemeMenuCallback menuCallback) throws IOException
	{
		this.assetName = fileName;
		this.relativePathPrefix = relativePathPrefix;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream in = context.getAssets().open(this.assetName);
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer, 0, buffer.length)) != -1)
			baos.write(buffer, 0, read);
		in.close();
		data = baos.toByteArray();
		this.menuCallback = menuCallback;
	}

	@Override
	public XmlRenderThemeMenuCallback getMenuCallback()
	{
		return this.menuCallback;
	}

	@Override
	public String getRelativePathPrefix()
	{
		return this.relativePathPrefix;
	}

	@Override
	public InputStream getRenderThemeAsStream()
	{
		return new ByteArrayInputStream(data);
	}
}
