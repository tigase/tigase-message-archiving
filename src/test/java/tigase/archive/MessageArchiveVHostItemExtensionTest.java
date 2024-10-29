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

import org.junit.Test;
import tigase.xml.Element;

import static org.junit.Assert.*;

public class MessageArchiveVHostItemExtensionTest {

	@Test
	public void initFromElement() {
		final MessageArchiveVHostItemExtension extension = new MessageArchiveVHostItemExtension();
		final Element item = new Element(MessageArchiveVHostItemExtension.ID);
		extension.initFromElement(item);
		assertTrue(extension.isEnabled());
		assertNull(extension.toElement());
		extension.initFromElement(new Element(extension.getId(), new String[]{"enabled"}, new String[] {"false"}));
		assertFalse(extension.isEnabled());
		assertNotNull(extension.toElement());
		extension.initFromElement(new Element(extension.getId(), new String[]{"enabled"}, new String[] {"true"}));
		assertTrue(extension.isEnabled());
		assertNull(extension.toElement());
	}

	@Test
	public void testMerge() {
		final MessageArchiveVHostItemExtension defExtension = new MessageArchiveVHostItemExtension();
		defExtension.initFromElement(new Element(defExtension.getId(), new String[]{"retention-type"},
		                                         new String[]{RetentionType.unlimited.toString()}));

		final MessageArchiveVHostItemExtension domainExtension = new MessageArchiveVHostItemExtension();
		assertEquals(RetentionType.userDefined, domainExtension.getRetentionType());
		domainExtension.initFromElement(new Element(domainExtension.getId(), new String[]{"retention-type"}, new String[] {"numberOfDays"}));
		assertEquals(RetentionType.numberOfDays, domainExtension.getRetentionType());
		final MessageArchiveVHostItemExtension merged = domainExtension.mergeWithDefaults(
			defExtension);
		assertEquals(RetentionType.numberOfDays, merged.getRetentionType());
	}
}