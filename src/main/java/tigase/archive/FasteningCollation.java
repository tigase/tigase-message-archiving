/*
 * Tigase Message Archiving Component - Implementation of Message Archiving component for Tigase XMPP Server.
 * Copyright (C) 2012 Tigase, Inc. (office@tigase.com)
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

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum FasteningCollation {
	simplified,
	full,
	collate,
	fastenings;

	private static final Map<String, FasteningCollation> nameToCollationMap = EnumSet.allOf(FasteningCollation.class)
			.stream()
			.collect(Collectors.toMap(FasteningCollation::name, Function.identity()));

	public short getValue() {
		switch (this) {
			case simplified:
				return 0;
			case full:
				return 1;
			case collate:
				return 2;
			case fastenings:
				return 3;
			default:
				throw new IllegalArgumentException("Collate is not supported on the database level!");
		}
	}

	public static FasteningCollation forName(String name) {
		return nameToCollationMap.get(name);
	}
}
