package com.androzic.util;

import java.util.ArrayList;
import java.util.List;

public final class CSV
{
	private static final char ESCAPE = '\\';
	private static final char QUOTE = '"';
	private static final char SEPARATOR = ',';

	private static String field(final StringBuffer field, final int begin, final int end)
	{
	     if (begin < 0) {
	         return field.substring(0, end);
	     } else {
	         return field.substring(begin, end);
	     }
	}

	private static char escape(final char c)
	{
		switch (c) {
		case 'n':
		         return '\n';
		     case 't':
		         return '\t';
		     case 'r':
		         return '\r';
		     default:
		         return c;
		     }
	}

	public static String[] parseLine(final String line)
	{
		int length = line.length();
		
		if (length == 0)
		{
			return new String[0];
		}

		// Check here if the last character is an escape character so
		// that we don't need to check in the main loop.
		if (line.charAt(length - 1) == ESCAPE)
		{
			throw new IllegalArgumentException(": last character is an escape character\n" + line);
		}

		// The set of parsed fields.
		List<String> result = new ArrayList<String>();

		// The characters between separators
		StringBuffer buf = new StringBuffer(length);
		// Marks the beginning of the field relative to buffer, -1 indicates the beginning of buffer
		int begin = -1;
		// Marks the end of the field relative to buffer
		int end = 0;

		// Indicates whether or not we're in a quoted string
		boolean quote = false;

		for (int i = 0; i < length; i++)
		{
			char c = line.charAt(i);
			if (quote)
			{
				switch (c)
				{
					case QUOTE:
						quote = false;
						break;
					case ESCAPE:
						buf.append(escape(line.charAt(++i)));
						break;
					default:
						buf.append(c);
						break;
				}

				end = buf.length();
			}
			else
			{
				switch (c)
				{
					case SEPARATOR:
						result.add(field(buf, begin, end));
						buf = new StringBuffer(length);
						begin = -1;
						end = 0;
						break;
					case ESCAPE:
						if (begin < 0) { begin = buf.length(); }
						buf.append(escape(line.charAt(++i)));
						end = buf.length();
						break;
					case QUOTE:
						if (begin < 0) { begin = buf.length(); }
						quote = true;
						end = buf.length();
						break;
					default:
						if (begin < 0 && !Character.isWhitespace(c))
						{
							begin = buf.length();
						}
						buf.append(c);
						if (!Character.isWhitespace(c)) { end = buf.length(); }
						break;
				}
			}
		}

		if (quote)
		{
			throw new IllegalArgumentException("unterminated string\n" + line);
		}
		else
		{
			result.add(field(buf, begin, end));
		}

		String[] fields = new String[result.size()];
		result.toArray(fields);
		return fields;
	}
}
