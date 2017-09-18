/*
 * #%~
 * Annotation parser for FMU export
 * %%
 * Copyright (C) 2015 - 2017 Overture
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #~%
 */
package org.overture.fmi.annotation;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RetainVdmCommentsFilter extends FilterInputStream
{
	private final int size;

	public RetainVdmCommentsFilter(InputStream in, String pattern)
	{
		super(in);
		this.size = pattern.length();
		this.pattern = pattern;
		backtrack = new byte[size];
		backtrackIndex = new int[size];
	}

	private byte backtrack[];
	private int backtrackIndex[];

	String pattern = "--@";

	boolean blankLine = true;
	boolean enabled = false;

	private void pushBack(byte c, int index)
	{
		for (int i = size - 1; i > 0; i--)
		{
			backtrack[i] = backtrack[i - 1];
			backtrackIndex[i] = backtrackIndex[i - 1];
		}
		backtrack[0] = c;
		backtrackIndex[0] = index;
	}

	String getBacktrack()
	{
		String tmp = "";
		for (int i = 0; i < size; i++)
		{
			tmp += " " + (char) backtrack[i] + ":" + backtrackIndex[i];
		}
		return tmp;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		int count = super.read(b, off, len);
		for (int i = 0; i < count; i++)
		{
			if (b[i] == '\n' || b[i] == '\r')
			{
				if (enabled)
				{
					enabled = false;
				}

				blankLine = true;
			} else if (b[i] == ' ' || b[i] == '\t')
			{
				continue;
			} else if (pattern.indexOf(b[i]) == -1)
			{
				blankLine = false;
			}

			pushBack(b[i], i);

			if (!enabled && isPatternMatch() && blankLine)
			{
				enabled = true;

				for (int bi = 0; bi < size; bi++)
				{
					b[backtrackIndex[bi]] = backtrack[bi];
				}
			}

			if (!enabled)
			{
				if (b[i] != '\n' && b[i] != '\r')
				{
					b[i] = ' ';
				}
			}
		}

		return count;
	}

	private boolean isPatternMatch()
	{
		boolean ok = true;
		for (int i = 0; i < size; i++)
		{
			ok &= pattern.charAt(i) == backtrack[size - 1 - i];
		}

		return ok;
	}

	@Override
	public int read() throws IOException
	{
		throw new IOException("not implemented");
	}

	@Override
	public int read(byte[] b) throws IOException
	{
		throw new IOException("not implemented");
	}

}
