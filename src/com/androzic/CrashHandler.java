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
	private static final int ANDROZIC_NOTIFICATION_ID = 2416;

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
				notificationManager.cancel(ANDROZIC_NOTIFICATION_ID);
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
