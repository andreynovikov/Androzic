package com.androzic.ui;

import java.io.File;
import java.io.FileFilter;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class FolderPickerPreference extends DialogPreference implements OnItemClickListener
{
//	private String mDefaultValue;
	private String mCurrentValue;
	
	private TextView mValueText;
	private ListView mFolderList;
	
	public FolderPickerPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mCurrentValue = "";
	}

	@Override
	protected View onCreateDialogView()
	{
		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(6, 6, 6, 6);

		if (getDialogMessage() != null)
		{
			TextView dialogText = new TextView(getContext());
			dialogText.setText(getDialogMessage());
			dialogText.setPadding(0, 0, 0, 12);
			layout.addView(dialogText);
		}

		mValueText = new TextView(getContext());
		mValueText.setTextSize(26);
		layout.addView(mValueText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		mFolderList = new ListView(getContext());
		layout.addView(mFolderList, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
		mFolderList.setOnItemClickListener(this);

		if (this.isPersistent())
			mCurrentValue = getPersistedString("");

		populateList();

		return layout;
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
		
		if (mCurrentValue == null)
			mCurrentValue = Environment.getExternalStorageDirectory().toString();
		File def = new File(mCurrentValue);
		if (! def.isAbsolute())
		{
			def = new File(Environment.getExternalStorageDirectory(), mCurrentValue);
			mCurrentValue = def.getAbsolutePath();
		}
		if (shouldPersist())
		{
			persistString(mCurrentValue);
		}
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
	    super.onDialogClosed(positiveResult);

	    if (!positiveResult)
	        return;
	    if (shouldPersist())
	        persistString(mCurrentValue);

	    notifyChanged();
	}
	
	@Override
	public CharSequence getSummary()
	{
		CharSequence summary = super.getSummary();
		if (summary != null)
		{
			return summary.toString();
		}
		else
		{
			return getPersistedString(mCurrentValue);
		}
	}
	
	private void populateList()
	{
		File initial = new File(mCurrentValue);
		if (! initial.exists() || ! initial.isDirectory())
			initial = Environment.getExternalStorageDirectory();

		mValueText.setText(initial.getAbsolutePath());

		int parent = initial.getParent() == null ? 0 : 1;
			
		File[] dirs = initial.listFiles(dirFilter);
		int length = dirs == null ? 0 : dirs.length;
		String[] folders = new String[length + parent];
		if (parent > 0)
			folders[0] = "..";
		for (int i = 0; i < length; i++)
		{
			folders[i + parent] = dirs[i].getName();
		}

		ArrayAdapter<String> folderAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, android.R.id.text1, folders);
		mFolderList.setAdapter(folderAdapter);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		String folder = (String) mFolderList.getItemAtPosition(position);
		File newFolder = null;
		if ("..".equals(folder))
		{
			newFolder = new File(mCurrentValue);
			newFolder = newFolder.getParentFile();
		}
		else
		{
			newFolder = new File(mCurrentValue, folder);			
		}
		mCurrentValue = newFolder.getAbsolutePath();
		populateList();
	}

	private FileFilter dirFilter = new FileFilter()
	{
		@Override
		public boolean accept(File pathname)
		{
			return pathname.isDirectory();
		}
	};
}
