/*
 * Query.java
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
package tigase.archive.xep0136;

import tigase.xml.Element;

import java.util.List;
import java.util.Set;

/**
 * Created by andrzej on 21.07.2016.
 */
public interface Query
		extends tigase.xmpp.mam.Query {

	Set<String> getContains();

	void addContains(String contain);

	Set<String> getTags();

	void addTag(String tag);

	boolean getUseMessageIdInRsm();

	void setUseMessageIdInRsm(boolean value);

	void prepareResult(Element retList);

	void addCollection(Element collection);

	List<Element> getCollections();

	void addItem(Element item);

	List<Element> getItems();

}
