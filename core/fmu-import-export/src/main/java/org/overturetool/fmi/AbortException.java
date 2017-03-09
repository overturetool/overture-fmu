package org.overturetool.fmi;

public class AbortException extends Exception
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AbortException(String message)
	{
		super(message);
	}

	public AbortException(String message, Exception e)
	{
		super(message, e);
	}
}
