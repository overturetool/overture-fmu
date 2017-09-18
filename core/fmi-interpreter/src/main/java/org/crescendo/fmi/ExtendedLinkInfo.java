/*
 * #%~
 * Fmi interface for the Crescendo Interpreter
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