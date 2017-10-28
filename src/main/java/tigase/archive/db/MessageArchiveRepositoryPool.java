/*
 * MessageArchiveRepositoryPool.java
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
package tigase.archive.db;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.TigaseDBException;
import tigase.db.beans.MDRepositoryBeanWithStatistics;
import tigase.kernel.beans.Bean;
import tigase.server.BasicComponent;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "repositoryPool", parent = MessageArchiveComponent.class, active = true)
public class MessageArchiveRepositoryPool<Q extends QueryCriteria, R extends MessageArchiveRepository<Q, DataSource>>
		extends MDRepositoryBeanWithStatistics<R>
		implements MessageArchiveRepository<Q, DataSource> {

	public MessageArchiveRepositoryPool() {
		super(MessageArchiveRepository.class, MAMRepository.class);
	}

	public MessageArchiveRepositoryPool(Class<? extends MessageArchiveRepository>... classess) {
		super(Stream.concat(Stream.of(classess), Stream.of(MessageArchiveRepository.class, MAMRepository.class))
					  .toArray(value -> new Class[value]));
	}

	@Override
	protected Class findClassForDataSource(DataSource dataSource) throws DBInitException {
		return DataSourceHelper.getDefaultClass(MessageArchiveRepository.class, dataSource.getResourceUri());
	}

	@Override
	public boolean belongsTo(Class<? extends BasicComponent> component) {
		return MessageArchiveComponent.class.isAssignableFrom(component);
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
	public Q newQuery() {
		return getRepository("default").newQuery();
	}

	@Override
	public void queryCollections(Q query, CollectionHandler<Q> collectionHandler) throws TigaseDBException {
		getRepository(query.getQuestionerJID().getDomain()).queryCollections(query, collectionHandler);
	}

	@Override
	public void queryItems(Q query, MAMRepository.ItemHandler<Q, MAMRepository.Item> itemHandler)
			throws RepositoryException, ComponentException {
		getRepository(query.getQuestionerJID().getDomain()).queryItems(query, itemHandler);
	}

	@Override
	public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException {
		getRepository(owner.getDomain()).removeItems(owner, withJid, start, end);
	}

	@Override
	public List<String> getTags(BareJID owner, String startsWith, Q criteria) throws TigaseDBException {
		return getRepository(owner.getDomain()).getTags(owner, startsWith, criteria);
	}

	@Override
	public void setDataSource(DataSource dataSource) {
		// nothing to do
	}

	@Override
	public Class<?> getDefaultBeanClass() {
		return MessageArchiveRepositoryConfigBean.class;
	}

	public static class MessageArchiveRepositoryConfigBean extends MDRepositoryConfigBean<MessageArchiveRepository<QueryCriteria, DataSource>> {

	}
}
