/**
* Recursive file listing under a specified directory.
* 
* @author Andrey Novikov
*/
package com.androzic.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FileList
{
	/**
	 * Recursively walk a directory tree and return a List of all
	 * files found; the List is sorted using File.compareTo().
	 *
	 * @param aStartingDir root directory, must be valid directory which can be read.
	 * @param aFilter <code>FilenameFilter</code> to filter files.
	 * @return <code>List</code> containing found <code>File</code> objects or empty <code>List</code> otherwise.
	 */

	static public List<File> getFileListing(final File aStartingDir, final FilenameFilter aFilter)
	{
		List<File> result = getFileListingNoSort(aStartingDir, aFilter);
		Collections.sort(result);
		return result;
	}

	static private List<File> getFileListingNoSort(final File aStartingDir, final FilenameFilter aFilter)
	{
		List<File> result = new ArrayList<File>();
		
		// find files
		File[] files = aStartingDir.listFiles(aFilter);
		if (files != null)
			result.addAll(Arrays.asList(files));

		// go deeper
		DirFileFilter dirFilter = new DirFileFilter();
		files = aStartingDir.listFiles(dirFilter);
		if (files != null)
		{
			List<File> dirs = Arrays.asList(files);
			for (File dir : dirs)
			{
				List<File> deeperList = getFileListingNoSort(dir, aFilter);
				result.addAll(deeperList);
			}
		}
		return result;
	}
	
}
