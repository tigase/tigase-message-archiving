/*
 * AbstractModule.java
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
package tigase.archive.modules;

import tigase.archive.MessageArchiveConfig;
import tigase.archive.QueryCriteria;
import tigase.archive.db.MessageArchiveRepository;
import tigase.component.PacketWriter;
import tigase.component.modules.Module;
import tigase.db.DataSource;
import tigase.kernel.beans.Inject;

/**
 * Created by andrzej on 16.07.2016.
 */
public abstract class AbstractModule implements Module {

	protected static final String MA_XMLNS = "urn:xmpp:archive";

	@Inject
	protected MessageArchiveConfig config;

	@Inject
	protected MessageArchiveRepository<QueryCriteria, DataSource> msg_repo;

	@Inject
	protected PacketWriter packetWriter;

}
