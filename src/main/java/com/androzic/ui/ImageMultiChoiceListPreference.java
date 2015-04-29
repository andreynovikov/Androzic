package com.androzic.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class ImageMultiChoiceListPreference extends ListPreference
{
	private boolean[] mClickedDialogEntryIndices;
	private String mCurrentValue;
	private static final String SEPARATOR = ",";

	// Constructor
	public ImageMultiChoiceListPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mClickedDialogEntryIndices = new boolean[getEntries().length];
		mCurrentValue = "";
	}

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        return a.getString(index);
    }

    @Override
	protected void onSetInitialValue(boolean restore, Object defaultValue)
	{
		if (restore)
			mCurrentValue = getPersistedString(mCurrentValue);
		else
		{
			mCurrentValue = (String) defaultValue;
		}
		if (shouldPersist())
		{
			persistString(mCurrentValue);
		}
	}

	@Override
	public void setEntries(CharSequence[] entries)
	{
		super.setEntries(entries);
		mClickedDialogEntryIndices = new boolean[entries.length];
	}

	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder)
	{
		CharSequence[] entries = getEntries();
		CharSequence[] entryValues = getEntryValues();
		if (entries == null || entryValues == null || entries.length != entryValues.length)
		{
			throw new IllegalStateException("ListPreference requires an entries array and an entryValues array which are both the same length");
		}

		if (this.isPersistent())
			mCurrentValue = getPersistedString("");
		restoreCheckedEntries();
		
		builder.setMultiChoiceItems(entries, mClickedDialogEntryIndices, new DialogInterface.OnMultiChoiceClickListener() {
			public void onClick(DialogInterface dialog, int which, boolean val)
			{
				mClickedDialogEntryIndices[which] = val;
			}
		});
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		ArrayList<String> values = new ArrayList<String>();

		CharSequence[] entryValues = getEntryValues();
		if (positiveResult && entryValues != null)
		{
			for (int i = 0; i < entryValues.length; i++)
			{
				if (mClickedDialogEntryIndices[i] == true)
				{
					String val = (String) entryValues[i];
					values.add(val);
				}
			}

			mCurrentValue = join(values, SEPARATOR);
			if (callChangeListener(mCurrentValue) && shouldPersist())
				persistString(mCurrentValue);
		}
	}

	public String[] parseStoredValue(String val)
	{
		if ("".equals(val))
		{
			return null;
		}
		else
		{
			return val.split(SEPARATOR);
		}
	}

	private void restoreCheckedEntries()
	{
		CharSequence[] entryValues = getEntryValues();
		String[] vals = parseStoredValue(mCurrentValue);

		if (vals != null)
		{
			List<String> valuesList = Arrays.asList(vals);
			for (int i = 0; i < entryValues.length; i++)
			{
				mClickedDialogEntryIndices[i] = valuesList.contains(entryValues[i].toString());
			}
		}
	}

	// Credits to kurellajunior on this post
	// http://snippets.dzone.com/posts/show/91
	protected static String join(Iterable<? extends Object> pColl, String separator)
	{
		Iterator<? extends Object> oIter;
		if (pColl == null || (!(oIter = pColl.iterator()).hasNext()))
			return "";
		StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
		while (oIter.hasNext())
			oBuilder.append(separator).append(oIter.next());
		return oBuilder.toString();
	}
}
