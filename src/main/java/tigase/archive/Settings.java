/*
 * Settings.java
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

import tigase.xml.*;

/**
 * Created by andrzej on 22.07.2016.
 */
public class Settings {

	private static final SimpleParser parser = SingletonFactory.getParserInstance();

	private boolean auto = false;
	private StoreMethod storeMethod = StoreMethod.Message;
	private boolean archiveMucMessages = false;
	private boolean archiveOnlyForContactsInRoster = false;

	public boolean isAutoArchivingEnabled() {
		return auto;
	}

	public boolean archiveOnlyForContactsInRoster() {
		return archiveOnlyForContactsInRoster;
	}

	public StoreMethod getStoreMethod() {
		return storeMethod;
	}

	public boolean archiveMucMessages() {
		return archiveMucMessages;
	}

	public String serialize() {
		Element prefs = new Element("prefs");
		if (auto)
			prefs.setAttribute("auto", "true");
		if (storeMethod != null)
			prefs.setAttribute("method", storeMethod.toString());
		if (archiveMucMessages)
			prefs.setAttribute("muc", "true");
		if (archiveOnlyForContactsInRoster)
			prefs.setAttribute("rosterOnly", "true");
		return prefs.toString();
	}

	public void parse(String data) {
		if (data == null || data.isEmpty())
			return;

		DomBuilderHandler handler = new DomBuilderHandler();
		char[] ch = data.toCharArray();
		parser.parse(handler, ch, 0, ch.length);
		Element pref = handler.getParsedElements().poll();
		if (pref == null)
			return;

		String val = pref.getAttributeStaticStr("auto");
		if (val != null) {
			auto = Boolean.parseBoolean(val);
		} else {
			auto = false;
		}
		val = pref.getAttributeStaticStr("method");
		if (val != null) {
			storeMethod = StoreMethod.valueof(val);
		} else {
			storeMethod = StoreMethod.Message;
		}
		val = pref.getAttributeStaticStr("muc");
		if (val != null) {
			archiveMucMessages = Boolean.parseBoolean(val);
		} else {
			archiveMucMessages = false;
		}
		val = pref.getAttributeStaticStr("rosterOnly");
		if (val != null) {
			archiveOnlyForContactsInRoster = Boolean.parseBoolean(val);
		} else {
			archiveOnlyForContactsInRoster = false;
		}
	}

	public void setAuto(boolean auto) {
		this.auto = auto;
		this.archiveOnlyForContactsInRoster = false;
	}

	public void setStoreMethod(StoreMethod storeMethod) {
		this.storeMethod = storeMethod;
		if (storeMethod == StoreMethod.False) {
			auto = false;
		}
	}

	public void setArchiveMucMessages(boolean archiveMucMessages) {
		this.archiveMucMessages = archiveMucMessages;
	}

	public void setArchiveOnlyForContactsInRoster(boolean archiveOnlyForContactsInRoster) {
		this.archiveOnlyForContactsInRoster = archiveOnlyForContactsInRoster;
	}

	public boolean updateRequirements(StoreMethod requiredStoreMethod, StoreMuc storeMuc) {
		boolean change = false;

		if (storeMethod.ordinal() < requiredStoreMethod.ordinal()) {
			storeMethod = requiredStoreMethod;
			change = true;
		}

		if (requiredStoreMethod != StoreMethod.False) {
			auto = true;
			change = true;
		}

		switch (storeMuc) {
			case True:
				if (!archiveMucMessages) {
					archiveMucMessages = true;
					change = true;
				}
				break;
			case False:
				if (archiveMucMessages) {
					archiveMucMessages = false;
					change = true;
				}
				break;
			default:
				break;
		}

		return change;
	}

}
