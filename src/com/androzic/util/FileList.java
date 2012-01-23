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
