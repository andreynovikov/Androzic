package com.androzic.util;

public class FileUtils
{
	public static String unusable = "*+~|<>!?\\/:";

	/**
	 * Replace illegal characters in a filename with "_" Illegal characters: : \
	 * / * ? | < >
	 * 
	 * @param name
	 * @return sanitized string
	 */
	public static String sanitizeFilename(String name)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++)
		{
			if (unusable.indexOf(name.charAt(i)) > -1)
				sb.append("_");
			else
				sb.append(name.charAt(i));
		}
		return sb.toString();
	}
}