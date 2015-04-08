/*
 * Settings.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
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
package tigase.archive;

import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.db.TigaseDBException;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;
import static tigase.archive.MessageArchivePlugin.*;

/**
 *
 * @author andrzej
 */
public class Settings {
	
	private static final Logger log = Logger.getLogger(Settings.class.getCanonicalName());
	
	public static boolean getAutoSave(final XMPPResourceConnection session, StoreMethod globalRequiredStoreMethod)
					throws NotAuthorizedException {
		StoreMethod requiredStoreMethod = getRequiredStoreMethod(session, globalRequiredStoreMethod);

		if (requiredStoreMethod != StoreMethod.False)
			return true;
		
		Boolean auto = (Boolean) session.getCommonSessionData(ID + "/" + AUTO);

		if (auto == null) {
			try {
				String data = session.getData(SETTINGS, AUTO, "false");

				auto = Boolean.parseBoolean(data);
				
				// if message archive is enabled but it is not allowed for domain
				// then we should disable it
				if (!VHostItemHelper.isEnabled(session.getDomain()) && auto) {
					auto = false;
					session.setData(SETTINGS, AUTO, String.valueOf(auto));
				}
				
				session.putCommonSessionData(ID + "/" + AUTO, auto);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}", ex
						.getMessage());
				auto = false;
			}
		}

		return auto;
	}	
	
	protected static StoreMethod getRequiredStoreMethod(XMPPResourceConnection session, StoreMethod globalRequiredStoreMethod) {
		return StoreMethod.valueof(VHostItemHelper.getRequiredStoreMethod(session.getDomain(), globalRequiredStoreMethod.toString()));
	}	
}
