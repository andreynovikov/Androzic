/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

import android.app.NotificationManager;
import android.content.Context;

public class CrashHandler implements UncaughtExceptionHandler
{
	private static final int[] ANDROZIC_NOTIFICATION_IDS = new int[] {24161, 24162, 24163};

	private UncaughtExceptionHandler defaultUEH;
	private NotificationManager notificationManager;
	private String localPath;

	/*
	 * if any of the parameters is null, the respective functionality will not
	 * be used
	 */
	public CrashHandler(Context context, String localPath)
	{
		this.localPath = localPath;
		this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void uncaughtException(Thread t, Throwable e)
	{
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		String stacktrace = result.toString();
		printWriter.close();
		String filename = "Androzic_" + System.currentTimeMillis() + ".crash";

		if (localPath != null)
		{
			writeToFile(stacktrace, filename);
		}
		if (notificationManager != null)
		{
			try
			{
				for (int id : ANDROZIC_NOTIFICATION_IDS)
					notificationManager.cancel(id);
			}
			catch (Throwable ex)
			{
				ex.printStackTrace();
			}
		}
        notificationManager = null;

		defaultUEH.uncaughtException(t, e);
	}

	private void writeToFile(String stacktrace, String filename)
	{
		try
		{
			BufferedWriter bos = new BufferedWriter(new FileWriter(localPath + "/" + filename));
			bos.write(stacktrace);
			bos.flush();
			bos.close();
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
	}

}
