package org.crescendo.fmi;

import java.util.List;

import org.destecs.core.vdmlink.LinkInfo;

class ExtendedLinkInfo extends LinkInfo {
	public enum Type {
		Real, Boolean, Integer, String
	}

	public final ExtendedLinkInfo.Type type;

	public ExtendedLinkInfo(String identifier, List<String> qualifiedName,
			int line, ExtendedLinkInfo.Type type) {
		super(identifier, qualifiedName, line);
		this.type = type;
	}

}