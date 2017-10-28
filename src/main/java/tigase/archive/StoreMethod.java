/*
 * StoreMethod.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.archive;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author andrzej
 */
public enum StoreMethod {
	// order of values is important for this enum
	False,		// 0
	Body,		// 1
	Message,	// 2
	Stream;		// 3
	
	private final String value;
	
	private StoreMethod() {
		this.value = name().toLowerCase();
	}
	
	private static final Map<String,StoreMethod> values = new HashMap<>();
	static {
		values.put(False.toString(), False);
		values.put(Body.toString(), Body);
		values.put(Message.toString(), Message);
		values.put(Stream.toString(), Stream);
	}
	
	public static StoreMethod valueof(String v) {
		if (v == null || v.isEmpty()) {
			return False;
		}
		StoreMethod result = values.get(v);
		if (result == null)
			throw new IllegalArgumentException();
		return result;
	}
	
	@Override
	public String toString() {
		return value;
	}
	
}
