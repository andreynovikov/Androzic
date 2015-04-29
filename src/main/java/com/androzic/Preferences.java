/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.droidparts.widget.MultiSelectListPreference;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.map.online.TileProvider;
import com.androzic.ui.SeekbarPreference;
import com.androzic.util.XmlUtils;

public class Preferences extends ListFragment
{
	private FragmentHolder fragmentHolderCallback;

	private final ArrayList<Header> headers = new ArrayList<>();
	private HeaderAdapter adapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		headers.clear();
		loadHeadersFromResource(R.xml.preference_headers, headers);
		adapter = new HeaderAdapter(getActivity(), headers);
		setListAdapter(adapter);

		/*
		 * Androzic application = (Androzic) getApplication();
		 * if (! application.isPaid)
		 * {
		 * for (int i = 0; i < getListAdapter().getCount(); i++)
		 * {
		 * if (R.id.pref_sharing == ((Header) getListAdapter().getItem(i)).id)
		 * {
		 * ((Header) getListAdapter().getItem(i)).summaryRes = R.string.donation_required;
		 * ((Header) getListAdapter().getItem(i)).fragmentArguments.putBoolean("disable", true);
		 * }
		 * }
		 * }
		 */

		/*
		 * if (getArguments().hasExtra("pref"))
		 * {
		 * for (int i = 0; i < getListAdapter().getCount(); i++)
		 * {
		 * if (getIntent().getIntExtra("pref", -1) == ((Header) getListAdapter().getItem(i)).id)
		 * {
		 * startWithFragment(((Header) getListAdapter().getItem(i)).fragment, ((Header) getListAdapter().getItem(i)).fragmentArguments, null, 0);
		 * finish();
		 * }
		 * }
		 * }
		 */
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			fragmentHolderCallback = (FragmentHolder) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement FragmentHolder");
		}
	}

	@Override
	public void onListItemClick(ListView lv, View v, int position, long id)
	{
		Header header = adapter.getItem(position);
		
		Fragment fragment = Fragment.instantiate(getActivity(), header.fragment);
		Bundle args = header.fragmentArguments;
		if (args == null)
			args = new Bundle();
		//TODO We should use breadcrumbs or remove them from parser
		args.putString("title", (String) header.getTitle(getResources()));
		args.putInt("help", header.help);
		fragment.setArguments(args);
		
		fragmentHolderCallback.addFragment(fragment, header.fragment);
	}

	/**
	 * Parse the given XML file as a header description, adding each
	 * parsed Header into the target list.
	 *
	 * @param resid
	 *            The XML resource to load and parse.
	 * @param target
	 *            The list in which the parsed headers should be placed.
	 */
	public void loadHeadersFromResource(int resid, List<Header> target)
	{
		Androzic application = Androzic.getApplication();
		XmlResourceParser parser = null;
		try
		{
			Resources resources = getResources();
			parser = resources.getXml(resid);
			AttributeSet attrs = Xml.asAttributeSet(parser);

			int type;
			//noinspection StatementWithEmptyBody
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG)
			{
				// Parse next until start tag is found
			}

			String nodeName = parser.getName();
			if (!"preference-headers".equals(nodeName))
			{
				throw new RuntimeException("XML document must start with <preference-headers> tag; found" + nodeName + " at " + parser.getPositionDescription());
			}

			Bundle curBundle = null;

			final int outerDepth = parser.getDepth();
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth))
			{
				if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT)
				{
					continue;
				}

				nodeName = parser.getName();
				if ("header".equals(nodeName))
				{
					Header header = new Header();

					TypedArray sa = resources.obtainAttributes(attrs, R.styleable.PreferenceHeader);
					header.id = sa.getResourceId(R.styleable.PreferenceHeader_id, (int) HEADER_ID_UNDEFINED);
					TypedValue tv = sa.peekValue(R.styleable.PreferenceHeader_title);
					if (tv != null && tv.type == TypedValue.TYPE_STRING)
					{
						if (tv.resourceId != 0)
						{
							header.titleRes = tv.resourceId;
						}
						else
						{
							header.title = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_summary);
					if (tv != null && tv.type == TypedValue.TYPE_STRING)
					{
						if (tv.resourceId != 0)
						{
							header.summaryRes = tv.resourceId;
						}
						else
						{
							header.summary = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_breadCrumbTitle);
					if (tv != null && tv.type == TypedValue.TYPE_STRING)
					{
						if (tv.resourceId != 0)
						{
							header.breadCrumbTitleRes = tv.resourceId;
						}
						else
						{
							header.breadCrumbTitle = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_breadCrumbShortTitle);
					if (tv != null && tv.type == TypedValue.TYPE_STRING)
					{
						if (tv.resourceId != 0)
						{
							header.breadCrumbShortTitleRes = tv.resourceId;
						}
						else
						{
							header.breadCrumbShortTitle = tv.string;
						}
					}
					header.iconRes = sa.getResourceId(R.styleable.PreferenceHeader_icon, 0);
					header.fragment = sa.getString(R.styleable.PreferenceHeader_fragment);
					header.help = sa.getResourceId(R.styleable.PreferenceHeader_help, 0);
					sa.recycle();

					if (curBundle == null)
					{
						curBundle = new Bundle();
					}

					final int innerDepth = parser.getDepth();
					while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth))
					{
						if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT)
						{
							continue;
						}

						String innerNodeName = parser.getName();
						if (innerNodeName.equals("extra"))
						{
							resources.parseBundleExtra(innerNodeName, attrs, curBundle);
							XmlUtils.skipCurrentTag(parser);

						}
						else if (innerNodeName.equals("intent"))
						{
							header.intent = Intent.parseIntent(resources, parser, attrs);

						}
						else
						{
							XmlUtils.skipCurrentTag(parser);
						}
					}

					if (curBundle.size() > 0)
					{
						header.fragmentArguments = curBundle;
						curBundle = null;
					}

					if (header.id == R.id.pref_plugins && application.getPluginsPreferences().size() == 0)
						continue;

					target.add(header);
				}
				else
				{
					XmlUtils.skipCurrentTag(parser);
				}
			}

		}
		catch (XmlPullParserException | IOException e)
		{
			throw new RuntimeException("Error parsing headers", e);
		} finally
		{
			if (parser != null)
				parser.close();
		}

	}

	private static class HeaderAdapter extends ArrayAdapter<Header>
	{
		private static class HeaderViewHolder
		{
			ImageView icon;
			TextView title;
			TextView summary;
		}

		private LayoutInflater mInflater;

		public HeaderAdapter(Context context, List<Header> objects)
		{
			super(context, 0, objects);
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			HeaderViewHolder holder;
			View view;

			if (convertView == null)
			{
				view = mInflater.inflate(R.layout.preference_header_item, parent, false);
				holder = new HeaderViewHolder();
				holder.icon = (ImageView) view.findViewById(android.R.id.icon);
				holder.title = (TextView) view.findViewById(android.R.id.title);
				holder.summary = (TextView) view.findViewById(android.R.id.summary);
				view.setTag(holder);
			}
			else
			{
				view = convertView;
				holder = (HeaderViewHolder) view.getTag();
			}

			// All view fields must be updated every time, because the view may be recycled
			Header header = getItem(position);
			holder.icon.setImageResource(header.iconRes);
			holder.title.setText(header.getTitle(getContext().getResources()));
			CharSequence summary = header.getSummary(getContext().getResources());
			if (!TextUtils.isEmpty(summary))
			{
				holder.summary.setVisibility(View.VISIBLE);
				holder.summary.setText(summary);
			}
			else
			{
				holder.summary.setVisibility(View.GONE);
			}

			return view;
		}
	}

	/**
	 * Default value for {@link Header#id Header.id} indicating that no
	 * identifier value is set. All other values (including those below -1)
	 * are valid.
	 */
	public static final long HEADER_ID_UNDEFINED = -1;

	/**
	 * Description of a single Header item that the user can select.
	 */
	public static final class Header implements Parcelable
	{
		public int help;

		/**
		 * Identifier for this header, to correlate with a new list when
		 * it is updated. The default value is {@link PreferenceActivity#HEADER_ID_UNDEFINED}, meaning no id.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_id
		 */
		public long id = HEADER_ID_UNDEFINED;

		/**
		 * Resource ID of title of the header that is shown to the user.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_title
		 */
		public int titleRes;

		/**
		 * Title of the header that is shown to the user.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_title
		 */
		public CharSequence title;

		/**
		 * Resource ID of optional summary describing what this header controls.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_summary
		 */
		public int summaryRes;

		/**
		 * Optional summary describing what this header controls.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_summary
		 */
		public CharSequence summary;

		/**
		 * Resource ID of optional text to show as the title in the bread crumb.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_breadCrumbTitle
		 */
		public int breadCrumbTitleRes;

		/**
		 * Optional text to show as the title in the bread crumb.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_breadCrumbTitle
		 */
		public CharSequence breadCrumbTitle;

		/**
		 * Resource ID of optional text to show as the short title in the bread crumb.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_breadCrumbShortTitle
		 */
		public int breadCrumbShortTitleRes;

		/**
		 * Optional text to show as the short title in the bread crumb.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_breadCrumbShortTitle
		 */
		public CharSequence breadCrumbShortTitle;

		/**
		 * Optional icon resource to show for this header.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_icon
		 */
		public int iconRes;

		/**
		 * Full class name of the fragment to display when this header is
		 * selected.
		 * 
		 * @attr ref android.R.styleable#PreferenceHeader_fragment
		 */
		public String fragment;

		/**
		 * Optional arguments to supply to the fragment when it is
		 * instantiated.
		 */
		public Bundle fragmentArguments;

		/**
		 * Intent to launch when the preference is selected.
		 */
		public Intent intent;

		/**
		 * Optional additional data for use by subclasses of PreferenceActivity.
		 */
		public Bundle extras;

		public Header()
		{
			// Empty
		}

		/**
		 * Return the currently set title. If {@link #titleRes} is set,
		 * this resource is loaded from <var>res</var> and returned. Otherwise {@link #title} is returned.
		 */
		public CharSequence getTitle(Resources res)
		{
			if (titleRes != 0)
			{
				return res.getText(titleRes);
			}
			return title;
		}

		/**
		 * Return the currently set summary. If {@link #summaryRes} is set,
		 * this resource is loaded from <var>res</var> and returned. Otherwise {@link #summary} is returned.
		 */
		public CharSequence getSummary(Resources res)
		{
			if (summaryRes != 0)
			{
				return res.getText(summaryRes);
			}
			return summary;
		}

		/**
		 * Return the currently set bread crumb title. If {@link #breadCrumbTitleRes} is set,
		 * this resource is loaded from <var>res</var> and returned. Otherwise {@link #breadCrumbTitle} is returned.
		 */
		public CharSequence getBreadCrumbTitle(Resources res)
		{
			if (breadCrumbTitleRes != 0)
			{
				return res.getText(breadCrumbTitleRes);
			}
			return breadCrumbTitle;
		}

		/**
		 * Return the currently set bread crumb short title. If {@link #breadCrumbShortTitleRes} is set,
		 * this resource is loaded from <var>res</var> and returned. Otherwise {@link #breadCrumbShortTitle} is returned.
		 */
		public CharSequence getBreadCrumbShortTitle(Resources res)
		{
			if (breadCrumbShortTitleRes != 0)
			{
				return res.getText(breadCrumbShortTitleRes);
			}
			return breadCrumbShortTitle;
		}

		@Override
		public int describeContents()
		{
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeLong(id);
			dest.writeInt(titleRes);
			TextUtils.writeToParcel(title, dest, flags);
			dest.writeInt(summaryRes);
			TextUtils.writeToParcel(summary, dest, flags);
			dest.writeInt(breadCrumbTitleRes);
			TextUtils.writeToParcel(breadCrumbTitle, dest, flags);
			dest.writeInt(breadCrumbShortTitleRes);
			TextUtils.writeToParcel(breadCrumbShortTitle, dest, flags);
			dest.writeInt(iconRes);
			dest.writeString(fragment);
			dest.writeBundle(fragmentArguments);
			if (intent != null)
			{
				dest.writeInt(1);
				intent.writeToParcel(dest, flags);
			}
			else
			{
				dest.writeInt(0);
			}
			dest.writeBundle(extras);
		}

		public void readFromParcel(Parcel in)
		{
			id = in.readLong();
			titleRes = in.readInt();
			title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
			summaryRes = in.readInt();
			summary = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
			breadCrumbTitleRes = in.readInt();
			breadCrumbTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
			breadCrumbShortTitleRes = in.readInt();
			breadCrumbShortTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
			iconRes = in.readInt();
			fragment = in.readString();
			fragmentArguments = in.readBundle();
			if (in.readInt() != 0)
			{
				intent = Intent.CREATOR.createFromParcel(in);
			}
			extras = in.readBundle();
		}

		Header(Parcel in)
		{
			readFromParcel(in);
		}

		public static final Creator<Header> CREATOR = new Creator<Header>() {
			public Header createFromParcel(Parcel source)
			{
				return new Header(source);
			}

			public Header[] newArray(int size)
			{
				return new Header[size];
			}
		};
	}

	public static class PreferencesFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		private int help;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			setHasOptionsMenu(true);

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
			
			help = arguments.getInt("help", 0);
		}

		@Override
		public void onResume()
		{
			super.onResume();

			// initialize list summaries
			initSummaries(getPreferenceScreen());
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
			((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(getArguments().getString("title"));
		}

		@Override
		public void onPause()
		{
			super.onPause();

			getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
			((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(null);
		}

		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
		{
			inflater.inflate(R.menu.help_menu, menu);
			super.onCreateOptionsMenu(menu, inflater);
		}

		@Override
		public void onPrepareOptionsMenu(final Menu menu)
		{
			menu.findItem(R.id.action_help).setVisible(help != 0);
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item)
		{
			switch (item.getItemId())
			{
				case R.id.action_help:
					PreferencesHelpDialog dialog = new PreferencesHelpDialog(help);
					dialog.show(getFragmentManager(), "dialog");
					return true;
				default:
					return super.onOptionsItemSelected(item);
			}
		}

		@SuppressLint("NewApi")
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (key.equals(getString(R.string.pref_folder_root)))
			{
				Androzic application = Androzic.getApplication();
				String root = sharedPreferences.getString(key, Environment.getExternalStorageDirectory() + File.separator + getString(R.string.def_folder_prefix));
				application.setRootPath(root);
			}
			else if (key.equals(getString(R.string.pref_folder_map)))
			{
				final ProgressDialog pd = new ProgressDialog(getActivity());
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() {
					public void run()
					{
						Androzic application = Androzic.getApplication();
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

				new Thread(new Runnable() {
					public void run()
					{
						Androzic application = Androzic.getApplication();
						application.charset = sharedPreferences.getString(key, "UTF-8");
						application.resetMaps();
						pd.dismiss();
					}
				}).start();
			}

			Preference pref = findPreference(key);
			setPrefSummary(pref);

			if (key.equals(getString(R.string.pref_locale)))
			{
				new AlertDialog.Builder(getActivity()).setTitle(R.string.restart_needed).setIcon(android.R.drawable.ic_dialog_alert).setMessage(getString(R.string.restart_needed_explained))
						.setCancelable(false).setPositiveButton(R.string.ok, null).show();
			}
			// TODO change intent name
			getActivity().sendBroadcast(new Intent("onSharedPreferenceChanged").putExtra("key", key));
			try
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
					BackupManager.dataChanged("com.androzic");
			}
			catch (NoClassDefFoundError e)
			{
			}
		}

		protected void setPrefSummary(Preference pref)
		{
			if (pref instanceof MultiSelectListPreference)
			{
				CharSequence[] summaries = ((MultiSelectListPreference) pref).getCheckedEntries();
				if (summaries != null)
				{
					StringBuffer summary = new StringBuffer("");
					for (int i = 0; i < summaries.length; i++)
					{
						summary.append(summaries[i]);
						if (i < summaries.length - 1)
						{
							summary.append(", ");
						}
					}
					pref.setSummary(summary);
				}
			}
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

	@SuppressWarnings("UnusedDeclaration")
	public static class PluginsPreferencesFragment extends Preferences.PreferencesFragment
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

	@SuppressWarnings("UnusedDeclaration")
	public static class MapPreferencesFragment extends Preferences.PreferencesFragment
	{
		String themeSelection;

		@Override
		public void onResume()
		{
			initThemeList();
			initPoiList();
			initProviderList();

			super.onResume();
		}

		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (key.equals(getString(R.string.pref_vectormap_theme)))
			{
				Androzic application = Androzic.getApplication();
				XmlRenderThemeStyleMenu xmlRenderThemeStyleMenu = application.xmlRenderThemeStyleMenu;
				themeSelection = sharedPreferences.getString(key, xmlRenderThemeStyleMenu.getDefaultValue());
				initPoiList();
			}
			else if (key.equals(getString(R.string.pref_vectormap_poi)))
			{
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(key + "_" + themeSelection, sharedPreferences.getString(key, "---"));
				editor.commit();
			}
			super.onSharedPreferenceChanged(sharedPreferences, key);
		}

		private void initThemeList()
		{
			Androzic application = Androzic.getApplication();
			XmlRenderThemeStyleMenu xmlRenderThemeStyleMenu = application.xmlRenderThemeStyleMenu;

			ListPreference themePreference = (ListPreference) findPreference(getString(R.string.pref_vectormap_theme));
			// This is the user language for the app, in 'en', 'de' etc format
			// No dialects are supported at the moment
			String language = Locale.getDefault().getLanguage();
			Map<String, XmlRenderThemeStyleLayer> baseLayers = xmlRenderThemeStyleMenu.getLayers();
			int visibleStyles = 0;
			for (XmlRenderThemeStyleLayer baseLayer : baseLayers.values())
			{
				if (baseLayer.isVisible())
					visibleStyles++;
			}

			String[] entries = new String[visibleStyles];
			String[] values = new String[visibleStyles];
			int i = 0;
			for (XmlRenderThemeStyleLayer baseLayer : baseLayers.values())
			{
				if (baseLayer.isVisible())
				{
					entries[i] = baseLayer.getTitle(language);
					values[i] = baseLayer.getId();
					i++;
				}
			}

			themePreference.setDefaultValue(xmlRenderThemeStyleMenu.getDefaultValue());
			themePreference.setEntries(entries);
			themePreference.setEntryValues(values);

			themeSelection = themePreference.getValue();
			// We need to check that the selection stored is actually a valid getLayer in the current
			// rendertheme.
			if (themeSelection == null || !xmlRenderThemeStyleMenu.getLayers().containsKey(themeSelection))
			{
				themeSelection = xmlRenderThemeStyleMenu.getLayer(xmlRenderThemeStyleMenu.getDefaultValue()).getId();
				themePreference.setValue(themeSelection);
			}
			themePreference.setSummary(xmlRenderThemeStyleMenu.getLayer(themeSelection).getTitle(language));
		}

		private void initPoiList()
		{
			Androzic application = Androzic.getApplication();
			XmlRenderThemeStyleMenu xmlRenderThemeStyleMenu = application.xmlRenderThemeStyleMenu;

			String language = Locale.getDefault().getLanguage();

			MultiSelectListPreference poiPreference = (MultiSelectListPreference) findPreference(getString(R.string.pref_vectormap_poi));

			int poiTypes = 0, defaultTypes = 0;
			for (XmlRenderThemeStyleLayer overlay : xmlRenderThemeStyleMenu.getLayer(themeSelection).getOverlays())
			{
				poiTypes++;
				if (overlay.isEnabled())
					defaultTypes++;
			}

			String[] entries = new String[poiTypes];
			String[] values = new String[poiTypes];
			String[] defaults = new String[defaultTypes];
			int i=0, j=0;
			for (XmlRenderThemeStyleLayer overlay : xmlRenderThemeStyleMenu.getLayer(themeSelection).getOverlays())
			{
				entries[i] = overlay.getTitle(language);
				values[i] = overlay.getId();
				i++;
				if (overlay.isEnabled())
				{
					defaults[j] = overlay.getId();
					j++;
				}
			}
			StringBuilder sb = new StringBuilder();
			for (i = 0; i < defaultTypes; i++)
			{
				sb.append(defaults[i]);
				if (i < defaultTypes - 1)
					sb.append(MultiSelectListPreference.SEP);
			}
			String defaultPoi = sb.toString();
			Log.e("PREF", "Default: " + defaultPoi);
			String value = poiPreference.getSharedPreferences().getString(poiPreference.getKey() + "_" + themeSelection, "---");
			Log.e("PREF", "Value: " + value);
			if ("---".equals(value))
				poiPreference.setValue(defaultPoi);
			else
				poiPreference.setValue(value);
			poiPreference.setDefaultValue(defaultPoi);
			poiPreference.setEntries(entries);
			poiPreference.setEntryValues(values);
			setPrefSummary(poiPreference);
		}

		private void initProviderList()
		{
			Androzic application = Androzic.getApplication();

			// Enumerate online providers
			MultiSelectListPreference mapPreference = (MultiSelectListPreference) findPreference(getString(R.string.pref_onlinemap));
			// initialize map list
			List<TileProvider> providers = application.getOnlineMaps();
			String[] entries = new String[providers.size()];
			String[] values = new String[providers.size()];
			int i = 0;
			for (TileProvider provider : providers)
			{
				entries[i] = provider.name;
				values[i] = provider.code;
				i++;
			}
			mapPreference.setEntries(entries);
			mapPreference.setEntryValues(values);
		}
	}
}
