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
