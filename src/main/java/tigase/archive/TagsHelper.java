/*
 * TagsHelper.java
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

import tigase.xml.Element;

import java.util.HashSet;
import java.util.Set;

/**
 * @author andrzej
 */
public class TagsHelper {

	private static final String[] MESSAGE_BODY_PATH = {"message", "body"};

	private static final char[] TAG_PERFIX = {'#', '@'};

	public static Set<String> extractTags(Element msg) {
		Set<String> tags = new HashSet<String>();

		if (msg == null) {
			return tags;
		}

		String body = msg.getCDataStaticStr(MESSAGE_BODY_PATH);
		return extractTags(tags, body);
	}

	public static Set<String> extractTags(Set<String> tags, String body) {
		if (body == null || body.isEmpty()) {
			return tags;
		}

		String[] parts = body.split("\\s");
		if (parts == null || parts.length == 0) {
			return tags;
		}

		for (String part : parts) {
			if (!matches(part)) {
				continue;
			}

			String tag = process(part);
			if (tag != null) {
				tags.add(tag);
			}
		}

		return tags;
	}

	public static boolean matches(String part) {
		if (part.length() == 0) {
			return false;
		}

		for (char prefix : TAG_PERFIX) {
			if (part.charAt(0) == prefix) {
				return true;
			}
		}

		return false;
	}

	public static String process(String tag) {
		if (tag.length() < 2) {
			return null;
		}

		if (tag.charAt(tag.length() - 1) == '.') {
			tag = process(tag.substring(0, tag.length() - 1));
		}

		return tag;
	}
}
