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
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.androzic.map.online.TileProvider;
import com.androzic.ui.SeekbarPreference;

@SuppressLint("NewApi")
public class PreferencesHC extends PreferenceActivity
{
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        /*
		Androzic application = (Androzic) getApplication();
    	if (! application.isPaid)
    	{
			for (int i = 0; i < getListAdapter().getCount(); i++)
			{
				if (R.id.pref_sharing == ((Header) getListAdapter().getItem(i)).id)
				{
					((Header) getListAdapter().getItem(i)).summaryRes = R.string.donation_required;
					((Header) getListAdapter().getItem(i)).fragmentArguments.putBoolean("disable", true);
				}
			}
    	}
    	*/
        
    	if (getIntent().hasExtra("pref"))
    	{
			for (int i = 0; i < getListAdapter().getCount(); i++)
			{
				if (getIntent().getIntExtra("pref", -1) == ((Header) getListAdapter().getItem(i)).id)
				{
					startWithFragment(((Header) getListAdapter().getItem(i)).fragment, ((Header) getListAdapter().getItem(i)).fragmentArguments, null, 0);
					finish();
				}
			}
    	}
    }
	
	@Override
	public void onBuildHeaders(List<Header> target)
	{
		loadHeadersFromResource(R.xml.preference_headers, target);
	}

	public static class PreferencesFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			Bundle arguments = getArguments();
			
			if (arguments == null)
				return;
			
			String resource = arguments.getString("resource");
			if (resource != null)
			{
				int res = getActivity().getResources().getIdentifier(resource, "xml", getActivity().getPackageName());
				addPreferencesFromResource(res);
			}
			
			if (arguments.getBoolean("disable", false))
			{
				PreferenceScreen screen = getPreferenceScreen();
				for (int i = 0; i < screen.getPreferenceCount(); i++)
				{
					getPreferenceScreen().getPreference(i).setEnabled(false);
				}
			}
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

	    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	    {
	        if (key.equals(getString(R.string.pref_folder_root)))
	        {
	    		Androzic application = (Androzic) getActivity().getApplication();
	    		String root = sharedPreferences.getString(key, Environment.getExternalStorageDirectory() + File.separator + getString(R.string.def_folder_prefix));
	        	application.setRootPath(root);
	        }
	        else if (key.equals(getString(R.string.pref_folder_map)))
	        {
				final ProgressDialog pd = new ProgressDialog(getActivity());
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() 
				{ 
					public void run() 
					{
						Androzic application = (Androzic) getActivity().getApplication();
						application.setMapPath(sharedPreferences.getString(key, getActivity().getResources().getString(R.string.def_folder_map)));
						pd.dismiss();
					} 
				}).start(); 
	        }
	        else if (key.equals(getString(R.string.pref_charset)))
	        {
				final ProgressDialog pd = new ProgressDialog(getActivity());
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() 
				{ 
					public void run() 
					{
						Androzic application = (Androzic) getActivity().getApplication();
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
	    		Androzic application = (Androzic) getActivity().getApplication();
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
				new AlertDialog.Builder(getActivity()).setTitle(R.string.restart_needed).setIcon(android.R.drawable.ic_dialog_alert).setMessage(getString(R.string.restart_needed_explained)).setCancelable(false).setPositiveButton(R.string.ok, null).show();
	        }
	        // TODO change intent name
	        getActivity().sendBroadcast(new Intent("onSharedPreferenceChanged").putExtra("key", key));
	        try
	        {
	        	BackupManager.dataChanged("com.androzic");
	        }
	        catch (NoClassDefFoundError e)
	        {
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
	    	for (int i=preference.getPreferenceCount()-1; i>=0; i--)
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

	public static class PluginsPreferencesFragment extends PreferencesHC.PreferencesFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			PreferenceScreen root = getPreferenceManager().createPreferenceScreen(getActivity());
			root.setTitle(R.string.pref_plugins_title);
			setPreferenceScreen(root);

			Androzic application = (Androzic) getActivity().getApplication();
			Map<String, Intent> plugins = application.getPluginsPreferences();

			for (String plugin : plugins.keySet())
			{
				Preference preference = new Preference(getActivity());
				preference.setTitle(plugin);
				preference.setIntent(plugins.get(plugin));
				root.addPreference(preference);
			}
		}
	}

	public static class OnlineMapPreferencesFragment extends PreferencesHC.PreferencesFragment
	{
	    @Override
		public void onResume()
	    {
    		Androzic application = (Androzic) getActivity().getApplication();
			
			ListPreference maps = (ListPreference) findPreference(getString(R.string.pref_onlinemap));
			SeekbarPreference mapzoom = (SeekbarPreference) findPreference(getString(R.string.pref_onlinemapscale));
	        // initialize map list
	    	List<TileProvider> providers = application.getOnlineMaps();
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

			super.onResume();
	    }
	}
	
	public static class ApplicationPreferencesFragment extends PreferencesHC.PreferencesFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
	    {
	        super.onCreate(savedInstanceState);
	        
	        Preference prefAbout = (Preference) findPreference(getString(R.string.pref_about));
	        prefAbout.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
		      		final LayoutInflater factory = LayoutInflater.from(ApplicationPreferencesFragment.this.getActivity());
		            final View aboutView = factory.inflate(R.layout.dlg_about, null);
		            final TextView versionLabel = (TextView) aboutView.findViewById(R.id.version_label);
		            String versionName = null;
		            try {
		                versionName = ApplicationPreferencesFragment.this.getActivity().getPackageManager().getPackageInfo(ApplicationPreferencesFragment.this.getActivity().getPackageName(), 0).versionName;
		            } catch (NameNotFoundException ex) {
		                versionName = "unable to retreive version";
		            }
		            versionLabel.setText(getString(R.string.version, versionName));
		            new AlertDialog.Builder(ApplicationPreferencesFragment.this.getActivity())
		                .setIcon(R.drawable.icon)
		                .setTitle(R.string.app_name)
		                .setView(aboutView)
		                .setPositiveButton("OK", null)
		                .create().show();
	        		return true;
	        	}
	        });
	    	
	        Preference prefDonateGoogle = (Preference) findPreference(getString(R.string.pref_donategoogle));
	        prefDonateGoogle.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	            	Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.androzic.donate"));
	            	startActivity(marketIntent);
	        		return true;
	        	}
	        });
	        Preference prefDonatePaypal = (Preference) findPreference(getString(R.string.pref_donatepaypal));
	        prefDonatePaypal.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.paypaluri))));
	        		return true;
	        	}
	        });

	    	Androzic application = (Androzic) getActivity().getApplication();
	        if (application.isPaid)
	        {
	        	ApplicationPreferencesFragment.this.getPreferenceScreen().removePreference(prefDonateGoogle);
	        	ApplicationPreferencesFragment.this.getPreferenceScreen().removePreference(prefDonatePaypal);
	        }
	        
	        Preference prefGooglePlus = (Preference) findPreference(getString(R.string.pref_googleplus));
	        prefGooglePlus.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.googleplusuri))));
	        		return true;
	        	}
	        });

			Preference prefFacebook = (Preference) findPreference(getString(R.string.pref_facebook));
	        prefFacebook.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.facebookuri))));
	        		return true;
	        	}
	        });

	        Preference prefTwitter = (Preference) findPreference(getString(R.string.pref_twitter));
	        prefTwitter.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.twitteruri))));
	        		return true;
	        	}
	        });

	        Preference prefFaq = (Preference) findPreference(getString(R.string.pref_faq));
	        prefFaq.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.faquri))));
	        		return true;
	        	}
	        });
	
	        Preference prefFeature = (Preference) findPreference(getString(R.string.pref_feature));
	        prefFeature.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.featureuri))));
	        		return true;
	        	}
	        });
	
	        Preference prefCredits = (Preference) findPreference(getString(R.string.pref_credits));
	        prefCredits.setOnPreferenceClickListener(new OnPreferenceClickListener()
	        {
	        	public boolean onPreferenceClick(Preference preference)
	        	{
	        		startActivity(new Intent(ApplicationPreferencesFragment.this.getActivity(), Credits.class));
	        		return true;
	        	}
	        });
	    }
	}
}
