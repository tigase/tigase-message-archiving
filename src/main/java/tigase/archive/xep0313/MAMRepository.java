/*
 * MAMRepository.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.archive.xep0313;

import tigase.component.exceptions.ComponentException;
import tigase.db.TigaseDBException;
import tigase.xml.Element;

import java.util.Date;

/**
 * Created by andrzej on 19.07.2016.
 */
public interface MAMRepository<Q extends Query, I extends MAMRepository.Item> {

//	int countItems(Q query);

	void queryItems(Q query, ItemHandler<Q,I> itemHandler) throws TigaseDBException, ComponentException;

	Q newQuery();

	interface Item {
		String getId();
		Element getMessage();
		Date getTimestamp();
	}

	interface ItemHandler<Q extends Query, I extends Item> {

		void itemFound(Q query, I item);

	}

}
