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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class About extends DialogFragment
{
	private boolean isModal = false;

	// When fragment is called as a dialog, set flag
	public static About newInstance()
	{
		About fragment = new About();
		fragment.isModal = true;
		return fragment;
	}

	public About()
	{
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		if (isModal)
		{
			return super.onCreateView(inflater, container, savedInstanceState);
		}
		else
		{
			View view = inflater.inflate(R.layout.dlg_about, container, false);
			updateAboutInfo(view);
			return view;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.app_name));
		View view = getActivity().getLayoutInflater().inflate(R.layout.dlg_about, null);
		builder.setView(view);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				About.this.dismiss();
			}
		});
		updateAboutInfo(view);
		return builder.create();
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private void updateAboutInfo(final View view)
	{
		// version
		String versionName = null;
		int versionBuild = 0;
		try
		{
			versionName = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
			versionBuild = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException ex)
		{
			versionName = "unable to retreive version";
		}
		final TextView version = (TextView) view.findViewById(R.id.version);
		version.setText(getString(R.string.version, versionName, versionBuild));

		// home links
		StringBuilder links = new StringBuilder();
		links.append("<a href=\"");
		links.append(": http://androzic.com");
		links.append("\">");
		links.append(getString(R.string.homepage));
		links.append("</a><br /><a href=\"");
		links.append(getString(R.string.faquri));
		links.append("\">");
		links.append(getString(R.string.faq));
		links.append("</a><br /><a href=\"");
		links.append(getString(R.string.featureuri));
		links.append("\">");
		links.append(getString(R.string.feedback));
		links.append("</a>");
		final TextView homelinks = (TextView) view.findViewById(R.id.homelinks);
		homelinks.setText(Html.fromHtml(links.toString()));
		homelinks.setMovementMethod(LinkMovementMethod.getInstance());

		// community links
		StringBuilder communities = new StringBuilder();
		communities.append("<a href=\"");
		communities.append(getString(R.string.googleplusuri));
		communities.append("\">");
		communities.append(getString(R.string.googleplus));
		communities.append("</a><br /><a href=\"");
		communities.append(getString(R.string.facebookuri));
		communities.append("\">");
		communities.append(getString(R.string.facebook));
		communities.append("</a><br /><a href=\"");
		communities.append(getString(R.string.twitteruri));
		communities.append("\">");
		communities.append(getString(R.string.twitter));
		communities.append("</a>");
		final TextView communitylinks = (TextView) view.findViewById(R.id.communitylinks);
		communitylinks.setText(Html.fromHtml(communities.toString()));
		communitylinks.setMovementMethod(LinkMovementMethod.getInstance());

		// donations
		StringBuilder donations = new StringBuilder();
		donations.append("<a href=\"");
		donations.append(getString(R.string.playuri));
		donations.append("\">");
		donations.append(getString(R.string.donate_google));
		donations.append("</a><br /><a href=\"");
		donations.append(getString(R.string.paypaluri));
		donations.append("\">");
		donations.append(getString(R.string.donate_paypal));
		donations.append("</a>");

		final TextView donationlinks = (TextView) view.findViewById(R.id.donationlinks);
		donationlinks.setText(Html.fromHtml(donations.toString()));
		donationlinks.setMovementMethod(LinkMovementMethod.getInstance());

		Androzic application = Androzic.getApplication();
		if (application.isPaid)
		{
			view.findViewById(R.id.donations).setVisibility(View.GONE);
			view.findViewById(R.id.donationtext).setVisibility(View.GONE);
			donationlinks.setVisibility(View.GONE);
		}

		// license
		final SpannableString message = new SpannableString(Html.fromHtml(getString(R.string.app_eula).replace("/n", "<br/>")));
		Linkify.addLinks(message, Linkify.WEB_URLS);
		final TextView license = (TextView) view.findViewById(R.id.license);
		license.setText(message);
		license.setMovementMethod(LinkMovementMethod.getInstance());

		// credits
		String[] names = getResources().getStringArray(R.array.credit_names);
		String[] merits = getResources().getStringArray(R.array.credit_merits);

		StringBuilder credits = new StringBuilder();
		for (int i = 0; i < names.length; i++)
		{
			credits.append("<b>");
			credits.append(merits[i]);
			credits.append("</b> &mdash; ");
			credits.append(names[i]);
			credits.append("<br />");
		}

		final TextView creditlist = (TextView) view.findViewById(R.id.credits);
		creditlist.setText(Html.fromHtml(credits.toString()));

		// dedication
		final TextView dedicated = (TextView) view.findViewById(R.id.dedicated);
		dedicated.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v)
			{
				clicks = 1;
				dedicated.setVisibility(View.GONE);
				View photo = view.findViewById(R.id.photo);
				photo.setVisibility(View.VISIBLE);
				photo.setOnClickListener(redirect);
			}
		});
	}

	private int clicks;

	private View.OnClickListener redirect = new View.OnClickListener() {

		@Override
		public void onClick(View v)
		{
			clicks++;
			if (clicks > 3)
			{
				startActivity(new Intent(Intent.ACTION_VIEW,  Uri.parse("http://andreynovikov.info/galleries/slideshow.xhtml?-filt.starred=1;-filt.labels=7;theme=classic;any=1")));
				clicks = -1;
			}
			else if (clicks == 0)
			{
				v.setVisibility(View.GONE);
			}
			else
			{
				((ImageView) v).setImageResource(getResources().getIdentifier("olya" + clicks, "drawable" , getActivity().getPackageName()));
			}
		}};

}
