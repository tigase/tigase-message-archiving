/*
 * MessageArchiveRepositoryPool.java
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
package tigase.archive.db;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.QueryCriteria;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.TigaseDBException;
import tigase.db.beans.MDRepositoryBean;
import tigase.kernel.beans.Bean;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "repositoryPool", parent = MessageArchiveComponent.class)
public class MessageArchiveRepositoryPool<R extends MessageArchiveRepository> extends MDRepositoryBean<R> implements MessageArchiveRepository {

	public MessageArchiveRepositoryPool() {
		domainSelection = SelectorType.MainOnly;
	}

	@Override
	protected Class findClassForDataSource(DataSource dataSource) throws DBInitException {
		return DataSourceHelper.getDefaultClass(MessageArchiveRepository.class, dataSource.getResourceUri());
	}

	@Override
	public void archiveMessage(BareJID owner, JID buddy, Direction direction, Date timestamp, Element msg, Set tags) {
		getRepository(owner.getDomain()).archiveMessage(owner, buddy, direction, timestamp, msg, tags);
	}

	@Override
	public void deleteExpiredMessages(BareJID owner, LocalDateTime before) throws TigaseDBException {
		getRepository(owner.getDomain()).deleteExpiredMessages(owner, before);
	}

	@Override
	public QueryCriteria newCriteriaInstance() {
		return getRepository(defaultDataSourceName).newCriteriaInstance();
	}

	@Override
	public List<Element> getCollections(BareJID owner, QueryCriteria criteria) throws TigaseDBException {
		return getRepository(owner.getDomain()).getCollections(owner, criteria);
	}

	@Override
	public List<Element> getItems(BareJID owner, QueryCriteria criteria) throws TigaseDBException {
		return getRepository(owner.getDomain()).getItems(owner, criteria);
	}

	@Override
	public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException {
		getRepository(owner.getDomain()).removeItems(owner, withJid, start, end);
	}

	@Override
	public List<String> getTags(BareJID owner, String startsWith, QueryCriteria criteria) throws TigaseDBException {
		return getRepository(owner.getDomain()).getTags(owner, startsWith, criteria);
	}

	@Override
	public void setDataSource(DataSource dataSource) {
		// nothing to do
	}
}
