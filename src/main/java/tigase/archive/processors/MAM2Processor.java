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
package tigase.archive.processors;

import tigase.kernel.beans.Bean;
import tigase.server.xmppsession.SessionManager;
import tigase.xmpp.impl.annotation.DiscoFeatures;
import tigase.xmpp.impl.annotation.Handle;
import tigase.xmpp.impl.annotation.Handles;
import tigase.xmpp.impl.annotation.Id;

import java.util.logging.Logger;

import static tigase.archive.processors.MAM2Processor.ID;

/**
 * Created by andrzej on 22.07.2016.
 */
@Id(ID)
@Handles({@Handle(path = {"iq", "query"}, xmlns = ID), @Handle(path = {"iq", "prefs"}, xmlns = ID)})
@DiscoFeatures({ID, "urn:xmpp:mix:pam:2#archive", "tigase:mamfc:0"})
@Bean(name = ID, parent = SessionManager.class, active = true)
public class MAM2Processor
		extends AbstractMAMProcessor {

	public static final String ID = "urn:xmpp:mam:2";
	private static final Logger log = Logger.getLogger(
			MAM2Processor.class.getCanonicalName());

	@Override
	protected String getXMLNS() {
		return ID;
	}

	@Override
	protected boolean hasStanzaIdSupport() {
		return true;
	}
}
