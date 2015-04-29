package com.androzic.ui;

import java.io.File;
import java.io.FileFilter;

import android.R;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class FolderPickerDialog extends AlertDialog implements OnClickListener
{
	private ListView folderList;
	
	public FolderPickerDialog(Context context, String initialPath, String defaultPath)
	{
		super(context);

		setTitle(initialPath);
		String[] folders = null;
		File initial = new File(initialPath);
		if (initial.exists() && initial.isDirectory())
		{
			File[] dirs = initial.listFiles(dirFilter);
			folders = new String[dirs.length];
			for (int i = 0; i < folders.length; i++)
			{
				folders[i] = dirs[i].getName();
			}
		}
		
		folderList = new ListView(getContext());
		ArrayAdapter<String> folderAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, android.R.id.text1, folders);
		folderList.setAdapter(folderAdapter);

        setView(folderList);

        setButton(AlertDialog.BUTTON_POSITIVE, context.getText(R.string.ok), this);
        setButton(AlertDialog.BUTTON_NEGATIVE, context.getText(R.string.cancel), (OnClickListener) null);
	}

	@Override
	public void onClick(DialogInterface arg0, int arg1)
	{
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
