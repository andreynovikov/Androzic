/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic;

import java.io.File;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.androzic.map.online.TileProvider;
import com.androzic.ui.SeekbarPreference;

public class Preferences extends PreferenceActivity
{

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		PreferenceScreen root = getPreferenceScreen();

		for (int i = root.getPreferenceCount() - 1; i >= 0; i--)
		{
			Preference pref = root.getPreference(i);
			final String key = pref.getKey();
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				public boolean onPreferenceClick(Preference preference)
				{
					startPreference(key);
					return true;
				}
			});
		}

		/*
		 * Androzic application = (Androzic) getApplication();
		 * Preference sharing = root.findPreference("pref_sharing");
		 * sharing.setEnabled(application.isPaid);
		 * if (! application.isPaid)
		 * {
		 * sharing.setSummary(R.string.donation_required);
		 * }
		 */

		if (getIntent().hasExtra("pref"))
		{
			startPreference(getIntent().getExtras().getString("pref"));
			finish();
		}
	}

	private void startPreference(String key)
	{
		Class<?> activity;
		if ("pref_behavior".equals(key))
		{
			activity = Preferences.OnlineMapPreferences.class;
		}
		else if ("pref_plugins".equals(key))
		{
			activity = Preferences.PluginsPreferences.class;
		}
		else
		{
			activity = Preferences.InnerPreferences.class;
		}
		startActivity(new Intent(Preferences.this, activity).putExtra("KEY", key));
	}

	public static class InnerPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			String key = getIntent().getExtras().getString("KEY");
			int res = getResources().getIdentifier(key, "xml", getPackageName());

			addPreferencesFromResource(res);
		}

		@Override
		public void onResume()
		{
			super.onResume();
			// initialize list summaries
			initSummaries(getPreferenceScreen());
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause()
		{
			super.onPause();

			getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		@SuppressLint("NewApi")
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (key.equals(getString(R.string.pref_folder_root)))
			{
				Androzic application = (Androzic) getApplication();
				String root = sharedPreferences.getString(key, Environment.getExternalStorageDirectory() + File.separator + getString(R.string.def_folder_prefix));
				application.setRootPath(root);
			}
			else if (key.equals(getString(R.string.pref_folder_map)))
			{
				final ProgressDialog pd = new ProgressDialog(this);
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() {
					public void run()
					{
						Androzic application = (Androzic) getApplication();
						application.setMapPath(sharedPreferences.getString(key, getResources().getString(R.string.def_folder_map)));
						pd.dismiss();
					}
				}).start();
			}
			else if (key.equals(getString(R.string.pref_charset)))
			{
				final ProgressDialog pd = new ProgressDialog(this);
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() {
					public void run()
					{
						Androzic application = (Androzic) getApplication();
						application.charset = sharedPreferences.getString(key, "UTF-8");
						application.resetMaps();
						pd.dismiss();
					}
				}).start();
			}

			Preference pref = findPreference(key);
			setPrefSummary(pref);

			if (key.equals(getString(R.string.pref_onlinemap)))
			{
				Androzic application = (Androzic) getApplication();
				SeekbarPreference mapzoom = (SeekbarPreference) findPreference(getString(R.string.pref_onlinemapscale));
				List<TileProvider> providers = application.getOnlineMaps();
				String current = sharedPreferences.getString(key, getResources().getString(R.string.def_onlinemap));
				TileProvider curProvider = null;
				for (TileProvider provider : providers)
				{
					if (current.equals(provider.code))
						curProvider = provider;
				}
				if (curProvider != null)
				{
					mapzoom.setMin(curProvider.minZoom);
					mapzoom.setMax(curProvider.maxZoom);
					int zoom = sharedPreferences.getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
					if (zoom < curProvider.minZoom)
					{
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.putInt(getString(R.string.pref_onlinemapscale), curProvider.minZoom);
						editor.commit();

					}
					if (zoom > curProvider.maxZoom)
					{
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.putInt(getString(R.string.pref_onlinemapscale), curProvider.maxZoom);
						editor.commit();
					}
				}
			}
			if (key.equals(getString(R.string.pref_locale)))
			{
				new AlertDialog.Builder(this).setTitle(R.string.restart_needed).setIcon(android.R.drawable.ic_dialog_alert).setMessage(getString(R.string.restart_needed_explained))
						.setCancelable(false).setPositiveButton(R.string.ok, null).show();
				// TODO Kill application
				// android.os.Process.killProcess(android.os.Process.myPid());
			}
			// TODO change intent name
			sendBroadcast(new Intent("onSharedPreferenceChanged").putExtra("key", key));
			try
			{
				if (Build.VERSION.SDK_INT > 7)
					BackupManager.dataChanged("com.androzic");
			}
			catch (NoClassDefFoundError e)
			{
				e.printStackTrace();
			}
		}

		private void setPrefSummary(Preference pref)
		{
			if (pref instanceof ListPreference)
			{
				CharSequence summary = ((ListPreference) pref).getEntry();
				if (summary != null)
				{
					pref.setSummary(summary);
				}
			}
			else if (pref instanceof EditTextPreference)
			{
				CharSequence summary = ((EditTextPreference) pref).getText();
				if (summary != null)
				{
					pref.setSummary(summary);
				}
			}
			else if (pref instanceof SeekbarPreference)
			{
				CharSequence summary = ((SeekbarPreference) pref).getText();
				if (summary != null)
				{
					pref.setSummary(summary);
				}
			}
		}

		private void initSummaries(PreferenceGroup preference)
		{
			for (int i = preference.getPreferenceCount() - 1; i >= 0; i--)
			{
				Preference pref = preference.getPreference(i);
				setPrefSummary(pref);

				if (pref instanceof PreferenceGroup || pref instanceof PreferenceScreen)
				{
					initSummaries((PreferenceGroup) pref);
				}
			}
		}
	}

	/**
	 * Preference lists Androzic plugins preferences.
	 */
	public static class PluginsPreferences extends PreferenceActivity
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
			root.setTitle(R.string.pref_plugins_title);
			setPreferenceScreen(root);

			Androzic application = (Androzic) getApplication();
			Map<String, Intent> plugins = application.getPluginsPreferences();

			for (String plugin : plugins.keySet())
			{
				Preference preference = new Preference(this);
				preference.setTitle(plugin);
				preference.setIntent(plugins.get(plugin));
				root.addPreference(preference);
			}
		}
	}

	public static class OnlineMapPreferences extends Preferences.InnerPreferences
	{
		@Override
		public void onResume()
		{
			Androzic application = (Androzic) getApplication();

			ListPreference maps = (ListPreference) findPreference(getString(R.string.pref_onlinemap));
			SeekbarPreference mapzoom = (SeekbarPreference) findPreference(getString(R.string.pref_onlinemapscale));
			// initialize map list
			List<TileProvider> providers = application.getOnlineMaps();
			if (providers != null)
			{
				String[] entries = new String[providers.size()];
				String[] entryValues = new String[providers.size()];
				String current = getPreferenceScreen().getSharedPreferences().getString(getString(R.string.pref_onlinemap), getResources().getString(R.string.def_onlinemap));
				TileProvider curProvider = null;
				int i = 0;
				for (TileProvider provider : providers)
				{
					entries[i] = provider.name;
					entryValues[i] = provider.code;
					if (current.equals(provider.code))
						curProvider = provider;
					i++;
				}
				maps.setEntries(entries);
				maps.setEntryValues(entryValues);

				if (curProvider != null)
				{
					mapzoom.setMin(curProvider.minZoom);
					mapzoom.setMax(curProvider.maxZoom);
				}
			}
			super.onResume();
		}
	}
}
