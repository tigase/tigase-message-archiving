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
package tigase.archive.db;

//~--- non-JDK imports --------------------------------------------------------

import tigase.annotations.TigaseDeprecated;
import tigase.archive.FasteningCollation;
import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.db.util.RepositoryVersionAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.util.MAMUtil;
import tigase.xmpp.mam.util.Range;
import tigase.xmpp.rsm.RSM;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Repository.Meta(supportedUris = {"jdbc:[^:]+:.*"}, isDefault = true)
@Repository.SchemaId(id = Schema.MA_SCHEMA_ID, name = Schema.MA_SCHEMA_NAME)
public class JDBCMessageArchiveRepository<Q extends QueryCriteria>
		extends AbstractMessageArchiveRepository<Q, DataRepository, JDBCMessageArchiveRepository.AddMessageAdditionalDataProvider>
		implements RepositoryVersionAware {

	private static final Logger log = Logger.getLogger(JDBCMessageArchiveRepository.class.getCanonicalName());
	private static final long LONG_NULL = 0;

	private static final SimpleParser parser = SingletonFactory.getParserInstance();

	private static final String STORE_PLAINTEXT_BODY_KEY = "store-plaintext-body";

	private static final String DELETE_EXPIRED_QUERY_TIMEOUT_KEY = "remove-expired-messages-query-timeout";
	private static final int DEF_DELETE_EXPIRED_QUERY_TIMEOUT_VAL = 5 * 60;

	private static final String DEF_GET_MESSAGE_QUERY = "{ call Tig_MA_GetMessage(?,?) }";
	private static final String DEF_GET_MESSAGES_QUERY = "{ call Tig_MA_GetMessages(?,?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_MESSAGES_COUNT_QUERY = "{ call Tig_MA_GetMessagesCount(?,?,?,?,?,?,?) }";
	private static final String DEF_GET_MESSAGES_POSITION_QUERY = "{ call Tig_MA_GetMessagePosition(?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_QUERY = "{ call Tig_MA_GetCollections(?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_COUNT_QUERY = "{ call Tig_MA_GetCollectionsCount(?,?,?,?,?,?) }";
	private static final String DEF_ADD_MESSAGE_QUERY = "{ call Tig_MA_AddMessage(?,?,?,?,?,?,?,?) }";
	private static final String DEF_ADD_TAG_TO_MESSAGE_QUERY = "{ call Tig_MA_AddTagToMessage(?,?,?) }";
	private static final String DEF_REMOVE_MESSAGES_QUERY = "{ call Tig_MA_RemoveMessages(?,?,?,?) }";
	private static final String DEF_DELETE_EXPIRED_MESSAGES_QUERY = "{ call Tig_MA_DeleteExpiredMessages(?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_QUERY = "{ call Tig_MA_GetTagsForUser(?,?,?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_COUNT_QUERY = "{ call Tig_MA_GetTagsForUserCount(?,?) }";
	@ConfigField(desc = "Query to add message to store", alias = "add-message-query")
	protected String ADD_MESSAGE_QUERY = DEF_ADD_MESSAGE_QUERY;
	@ConfigField(desc = "Query to add tag to message in store", alias = "add-tag-to-message-query")
	protected String ADD_TAG_TO_MESSAGE_QUERY = DEF_ADD_TAG_TO_MESSAGE_QUERY;
	@ConfigField(desc = "Query to delete expired messages", alias = "delete-expired-messages-query")
	protected String DELETE_EXPIRED_MESSAGES_QUERY = DEF_DELETE_EXPIRED_MESSAGES_QUERY;
	@ConfigField(desc = "Query to retrieve number of collections", alias = "get-collections-count-query")
	protected String GET_COLLECTIONS_COUNT_QUERY = DEF_GET_COLLECTIONS_COUNT_QUERY;
	@ConfigField(desc = "Query to retrieve list of collections", alias = "get-collections-query")
	protected String GET_COLLECTIONS_QUERY = DEF_GET_COLLECTIONS_QUERY;
	@ConfigField(desc = "Query to retrieve message with id", alias = "get-message-query")
	protected String GET_MESSAGE_QUERY = DEF_GET_MESSAGE_QUERY;
	@ConfigField(desc = "Query to retrieve number of messages", alias = "get-messages-count-query")
	protected String GET_MESSAGES_COUNT_QUERY = DEF_GET_MESSAGES_COUNT_QUERY;
	@ConfigField(desc = "Query to retrieve list of messages", alias = "get-messages-query")
	protected String GET_MESSAGES_QUERY = DEF_GET_MESSAGES_QUERY;
	@ConfigField(desc = "Query to retrieve message possition", alias = "get-message-position-query")
	protected String GET_MESSAGE_POSITION_QUERY = DEF_GET_MESSAGES_POSITION_QUERY;
	@ConfigField(desc = "Query to retrieve number of tags used by user", alias = "get-tags-for-user-count-query")
	protected String GET_TAGS_FOR_USER_COUNT_QUERY = DEF_GET_TAGS_FOR_USER_COUNT_QUERY;
	@ConfigField(desc = "Query to retrieve tags used by user", alias = "get-tags-for-user-query")
	protected String GET_TAGS_FOR_USER_QUERY = DEF_GET_TAGS_FOR_USER_QUERY;
	@ConfigField(desc = "Query to remove messages", alias = "remove-messages-query")
	protected String REMOVE_MESSAGES_QUERY = DEF_REMOVE_MESSAGES_QUERY;

	//~--- fields ---------------------------------------------------------------
	protected DataRepository data_repo = null;
	@ConfigField(desc = "Delete expired messages statement query timeout", alias = DELETE_EXPIRED_QUERY_TIMEOUT_KEY)
	private int delete_expired_timeout = DEF_DELETE_EXPIRED_QUERY_TIMEOUT_VAL;
	@ConfigField(desc = "Store plaintext body in separate field", alias = STORE_PLAINTEXT_BODY_KEY)
	private boolean storePlaintextBody = true;

	//~--- methods --------------------------------------------------------------

	@Override
	public void setDataSource(DataRepository data_repo) {
		try {
			initPreparedStatements(data_repo);
			this.data_repo = data_repo;
		} catch (SQLException ex) {
			throw new RuntimeException("MessageArchiveDB initialization exception", ex);
		}
	}

	@Override
	public void archiveMessage(BareJID owner, JID buddy, Date timestamp, Element msg, String stableId,
							   Set<String> tags) {
		archiveMessage(owner, buddy.getBareJID(), timestamp, msg, stableId, tags, null);
	}

	@Override
	public void deleteExpiredMessages(BareJID owner, LocalDateTime before) throws TigaseDBException {
		try {
			PreparedStatement delete_expired_msgs_st = data_repo.getPreparedStatement(owner,
																					  DELETE_EXPIRED_MESSAGES_QUERY);
			long timestamp_long = before.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			Timestamp ts = new java.sql.Timestamp(timestamp_long);
			synchronized (delete_expired_msgs_st) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST,
							"executing removal of expired messages for domain {0} with timeout set to {1} seconds",
							new Object[]{owner, delete_expired_timeout});
				}
				delete_expired_msgs_st.setQueryTimeout(delete_expired_timeout);
				delete_expired_msgs_st.setString(1, owner.toString());
				data_repo.setTimestamp(delete_expired_msgs_st, 2, ts);
				delete_expired_msgs_st.executeUpdate();
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not remove expired messages", ex);
		}
	}

	@Override
	public String getStableId(BareJID owner, BareJID buddy, String stanzaId) throws TigaseDBException {
		return null;
	}

	@Override
	public void queryCollections(Q crit, CollectionHandler<Q, MessageArchiveRepository.Collection> collectionHandler) throws TigaseDBException {
		try {
			log.log(Level.FINEST, () -> "Querying collections: crit: " + crit);
			Integer count = getCollectionsCount(crit);
			if (count == null) {
				count = 0;
			}

			Integer after = getColletionPosition(crit.getRsm().getAfter(), crit);
			Integer before = getColletionPosition(crit.getRsm().getBefore(), crit);

			calculateOffsetAndPosition(crit, count, before, after, Range.FULL);

			getCollectionsItems(crit, collectionHandler);
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve collections", ex);
		}
	}

	@Override
	public void queryItems(Q crit, ItemHandler<Q, MAMRepository.Item> itemHandler)
			throws TigaseDBException, ComponentException {
		try {
			log.log(Level.FINEST, () -> "Querying items, criteria: " + crit);
			if (!crit.getIds().isEmpty()) {
				ArrayDeque<MAMRepository.Item> items = new ArrayDeque<>();
				for (String id : crit.getIds()) {
					MAMRepository.Item item = getItem(crit, id);
					if (item == null) {
						throw new ComponentException(Authorization.ITEM_NOT_FOUND, "Item with ID '" + id + "' does not exist.");
					}
					items.add(item);
				}
				for (MAMRepository.Item item : items) {
					itemHandler.itemFound(crit, item);
				}
			} else {
				Integer count = getItemsCount(crit);
				if (count == null) {
					count = 0;
				}

				Range range = MAMUtil.rangeFromPositions(getItemPosition(crit.getAfterId(), crit), getItemPosition(crit.getBeforeId(), crit));

				Integer afterPosRSM = getItemPosition(crit.getRsm().getAfter(), crit);
				Integer beforePosRSM = getItemPosition(crit.getRsm().getBefore(), crit);

				calculateOffsetAndPosition(crit, count, beforePosRSM, afterPosRSM, range);

				getItemsItems(crit, range, itemHandler);
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}
	}

	@Override
	public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException {
		try {
			PreparedStatement remove_msgs_st = data_repo.getPreparedStatement(owner, REMOVE_MESSAGES_QUERY);

			synchronized (remove_msgs_st) {
				synchronized (remove_msgs_st) {
					remove_msgs_st.setString(1, owner.toString());
					remove_msgs_st.setString(2, withJid);
					data_repo.setTimestamp(remove_msgs_st, 3,
										   start == null ? null : new java.sql.Timestamp(start.getTime()));
					data_repo.setTimestamp(remove_msgs_st, 4,
										   end == null ? null : new java.sql.Timestamp(end.getTime()));
					remove_msgs_st.executeUpdate();
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not remove items", ex);
		}
	}

	//~--- get methods ----------------------------------------------------------

	@Override
	public List<String> getTags(BareJID owner, String startsWith, Q crit) throws TigaseDBException {
		List<String> results = new ArrayList<String>();
		try {
			ResultSet rs = null;
			int count = 0;
			startsWith = startsWith + "%";

			PreparedStatement get_tags_count_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_COUNT_QUERY);
			synchronized (get_tags_count_st) {
				try {
					get_tags_count_st.setString(1, owner.toString());
					get_tags_count_st.setString(2, startsWith);

					rs = get_tags_count_st.executeQuery();
					if (rs.next()) {
						count = rs.getInt(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			String beforeStr = crit.getRsm().getBefore();
			String afterStr = crit.getRsm().getAfter();
			calculateOffsetAndPosition(crit, count, beforeStr == null ? null : Integer.parseInt(beforeStr),
									   afterStr == null ? null : Integer.parseInt(afterStr), Range.FULL);

			PreparedStatement get_tags_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_QUERY);
			synchronized (get_tags_st) {
				try {
					int i = 1;
					get_tags_st.setString(i++, owner.toString());
					get_tags_st.setString(i++, startsWith);

					get_tags_st.setInt(i++, crit.getRsm().getMax());
					get_tags_st.setInt(i++, crit.getRsm().getIndex());

					rs = get_tags_st.executeQuery();
					while (rs.next()) {
						results.add(rs.getString(1));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}

			RSM rsm = crit.getRsm();
			rsm.setResults(count, rsm.getIndex());
			if (results != null && !results.isEmpty()) {
				rsm.setFirst(String.valueOf(rsm.getIndex()));
				rsm.setLast(String.valueOf(rsm.getIndex() + (results.size() - 1)));
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not retrieve known tags from database", ex);
		}

		return results;
	}

	@TigaseDeprecated(removeIn = "4.0.0", note = "Use method with `range` parameter", since = "3.1.0")
	@Deprecated
	public void setItemsQueryParams(PreparedStatement stmt, Q crit, FasteningCollation fasteningCollation)
			throws SQLException {
		int i = setQueryParams(stmt, crit, fasteningCollation);
		stmt.setInt(i++, crit.getRsm().getMax());
		stmt.setInt(i++, crit.getRsm().getIndex());
	}

	public void setItemsQueryParams(PreparedStatement stmt, Q crit, Range range, FasteningCollation fasteningCollation)
			throws SQLException {
		int i = setQueryParams(stmt, crit, fasteningCollation);
		stmt.setInt(i++, Math.min(range.size(), crit.getRsm().getMax()));
		stmt.setInt(i++, range.getLowerBound() + crit.getRsm().getIndex());
	}

	//~--- methods --------------------------------------------------------------

	@Override
	public Q newQuery() {
		return (Q) new QueryCriteria();
	}

	protected void initPreparedStatements(DataRepository data_repo) throws SQLException {
		data_repo.initPreparedStatement(GET_MESSAGE_QUERY, GET_MESSAGE_QUERY);
		data_repo.initPreparedStatement(GET_MESSAGES_QUERY, GET_MESSAGES_QUERY);
		data_repo.initPreparedStatement(GET_MESSAGES_COUNT_QUERY, GET_MESSAGES_COUNT_QUERY);
		data_repo.initPreparedStatement(GET_MESSAGE_POSITION_QUERY, GET_MESSAGE_POSITION_QUERY);
		data_repo.initPreparedStatement(GET_COLLECTIONS_QUERY, GET_COLLECTIONS_QUERY);
		data_repo.initPreparedStatement(GET_COLLECTIONS_COUNT_QUERY, GET_COLLECTIONS_COUNT_QUERY);
		data_repo.initPreparedStatement(ADD_MESSAGE_QUERY, ADD_MESSAGE_QUERY);
		data_repo.initPreparedStatement(ADD_TAG_TO_MESSAGE_QUERY, ADD_TAG_TO_MESSAGE_QUERY);
		data_repo.initPreparedStatement(REMOVE_MESSAGES_QUERY, REMOVE_MESSAGES_QUERY);
		data_repo.initPreparedStatement(DELETE_EXPIRED_MESSAGES_QUERY, DELETE_EXPIRED_MESSAGES_QUERY);
		data_repo.initPreparedStatement(GET_TAGS_FOR_USER_QUERY, GET_TAGS_FOR_USER_QUERY);
		data_repo.initPreparedStatement(GET_TAGS_FOR_USER_COUNT_QUERY, GET_TAGS_FOR_USER_COUNT_QUERY);
	}
	
	protected void archiveMessage(BareJID owner, BareJID buddy, Date timestamp, Element msg, String stableId, String stanzaId, String refStableId,
								  Set<String> tags, AddMessageAdditionalDataProvider additionParametersProvider) {
		try {
			java.sql.Timestamp mtime = new java.sql.Timestamp(timestamp.getTime());

			String msgStr = msg.toString();
			String body = storePlaintextBody ? msg.getChildCData(MSG_BODY_PATH) : null;
			PreparedStatement add_message_st = data_repo.getPreparedStatement(owner, ADD_MESSAGE_QUERY);

			synchronized (add_message_st) {
				int i = 1;
				add_message_st.setString(i++, owner.toString());
				add_message_st.setString(i++, buddy.toString());
				data_repo.setTimestamp(add_message_st, i++, mtime);
				add_message_st.setString(i++, stableId);
				add_message_st.setString(i++, stanzaId);
				add_message_st.setString(i++, refStableId);
				add_message_st.setString(i++, body);
				add_message_st.setString(i++, msgStr);

				if (additionParametersProvider != null) {
					additionParametersProvider.apply(add_message_st, i);
				}

				add_message_st.executeUpdate();
			}

			if (tags != null && !tags.isEmpty()) {
				PreparedStatement add_message_tag_st = data_repo.getPreparedStatement(owner, ADD_TAG_TO_MESSAGE_QUERY);
				synchronized (add_message_tag_st) {
					for (String tag : tags) {
						add_message_tag_st.setString(1, owner.toString());
						add_message_tag_st.setString(2, stableId);
						add_message_tag_st.setString(3, tag);
						add_message_tag_st.addBatch();
					}
					add_message_tag_st.executeBatch();
				}
			}
		} catch (SQLException ex) {
			if (ex.getErrorCode() == 1366 || ex.getMessage() != null && ex.getMessage().startsWith("Incorrect string value")) {
				log.log(Level.WARNING, "Your MySQL configuration can't handle extended Unicode (for example emoji) correctly. Please refer to <Support for emoji and other icons> section of the server documentation");
			} else {
				log.log(Level.WARNING, "Problem adding new entry to DB: " + msg, ex);
			}
		}
	}
	
	protected Timestamp convertToTimestamp(Date date) {
		if (date == null) {
			return null;
		}
		return new Timestamp(date.getTime());
	}

	protected int setCountQueryParams(PreparedStatement stmt, Q crit, FasteningCollation fasteningCollation)
			throws SQLException {
		return setQueryParams(stmt, crit, fasteningCollation);
	}
	
	protected int setQueryParams(PreparedStatement stmt, Q crit, FasteningCollation fasteningCollation) throws SQLException {
		int i = 1;
		stmt.setString(i++, crit.getQuestionerJID().getBareJID().toString());
		if (crit.getWith() != null) {
			stmt.setString(i++, crit.getWith().getBareJID().toString());
		} else {
			stmt.setObject(i++, null);
		}
		if (crit.getStart() != null) {
			if (data_repo.getDatabaseType() == DataRepository.dbTypes.mysql && crit.getStart().getTime() == 0) {
				stmt.setObject(i++, null);
			} else {
				data_repo.setTimestamp(stmt, i++, convertToTimestamp(crit.getStart()));
			}
		} else {
			stmt.setObject(i++, null);
		}
		if (crit.getEnd() != null) {
			if (data_repo.getDatabaseType() == DataRepository.dbTypes.mysql && crit.getEnd().getTime() == 0) {
				stmt.setObject(i++, null);
			} else {
				data_repo.setTimestamp(stmt, i++, convertToTimestamp(crit.getEnd()));
			}
		} else {
			stmt.setObject(i++, null);
		}
		if (fasteningCollation != null) {
			stmt.setShort(i++, fasteningCollation.getValue());
		}
		if (crit.getTags().isEmpty()) {
			stmt.setObject(i++, null);
		} else {
			StringBuilder sb = new StringBuilder();
			for (String tag : crit.getTags()) {
				if (sb.length() != 0) {
					sb.append(",");
				}
				sb.append('\'').append(tag.replace("'", "''")).append("'");
			}
			stmt.setString(i++, sb.toString());
		}
		if (crit.getContains().isEmpty()) {
			stmt.setObject(i++, null);
		} else {
			StringBuilder sb = new StringBuilder();
			for (String contain : crit.getContains()) {
				if (sb.length() != 0) {
					sb.append(",");
				}
				sb.append("'%").append(contain.replace("'", "''")).append("%'");
			}
			stmt.setString(i++, sb.toString());
		}
		log.log(Level.FINEST, () -> "Setting PS parameters: `" + stmt + "`, crit: " + crit);
		return i;
	}

	protected Collection newCollectionInstance() {
		return new Collection();
	}

	protected Item newItemInstance() {
		return new Item();
	}

	private void getCollectionsItems(Q crit, CollectionHandler<Q, MessageArchiveRepository.Collection> collectionHandler) throws SQLException {
		log.log(Level.FINEST, () -> "Getting collections items: " + crit);
		ResultSet selectRs = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_collections_st = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_QUERY);

		int i = 2;
		synchronized (get_collections_st) {
			try {
				setItemsQueryParams(get_collections_st, crit, null);

				selectRs = get_collections_st.executeQuery();
				while (selectRs.next()) {
					Collection collection = newCollectionInstance();
					collection.read(data_repo, selectRs, crit);

					collectionHandler.collectionFound(crit, collection);
				}
			} finally {
				data_repo.release(null, selectRs);
			}
		}

		List<Element> collections = crit.getCollections();
		if (collections != null) {
			int first = crit.getRsm().getIndex();
			crit.getRsm().setFirst(String.valueOf(first));
			crit.getRsm().setLast(String.valueOf(first + collections.size() - 1));
		}
	}

	private Integer getCollectionsCount(Q crit) throws SQLException {
		log.log(Level.FINEST, () -> "Getting collections: " + crit);
		ResultSet countRs = null;
		Integer count = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_collections_count = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_COUNT_QUERY);
		synchronized (get_collections_count) {
			try {
				setCountQueryParams(get_collections_count, crit, null);
				countRs = get_collections_count.executeQuery();
				if (countRs.next()) {
					count = countRs.getInt(1);
				}
			} finally {
				data_repo.release(null, countRs);
			}
		}
		return count;
	}

	private Integer getColletionPosition(String uid, Q query) {
		if (uid == null || uid.isEmpty()) {
			return null;
		}

		return Integer.parseInt(uid);
	}

	private MAMRepository.Item getItem(Q crit, String itemId) throws SQLException {
		log.log(Level.FINEST, () -> "Getting MAM item: " + crit + ", itemId: " + itemId);
		ResultSet rs = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_message_st = data_repo.getPreparedStatement(owner, GET_MESSAGE_QUERY);
		synchronized (get_message_st) {
			try {
				get_message_st.setString(1, owner.toString());
				get_message_st.setString(2, itemId);
				rs = get_message_st.executeQuery();
				if (rs.next()) {
					Item item = newItemInstance();
					item.read(data_repo, rs, crit);

					DomBuilderHandler domHandler = new DomBuilderHandler();
					parser.parse(domHandler, item.messageStr.toCharArray(), 0, item.messageStr.length());

					Queue<Element> queue = domHandler.getParsedElements();
					item.messageStr = null;
					item.messageEl = queue.poll();
					queue.clear();
					return item;
				}
			} finally {
				data_repo.release(null, rs);
			}
		}
		return null;
	}

	private void getItemsItems(Q crit, Range range, ItemHandler<Q, MAMRepository.Item> itemHandler) throws SQLException {
		ResultSet rs = null;
		Queue<Item> results = new ArrayDeque<Item>();
		BareJID owner = crit.getQuestionerJID().getBareJID();

		log.log(Level.FINER, () -> "Getting items items, criteria: " + crit + ", range: " + range);
		// there is no point to execute query if limit is estimated to be 0
		if (Math.min(range.size(), crit.getRsm().getMax()) > 0) {
			PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_QUERY);
			synchronized (get_messages_st) {
				try {
					setItemsQueryParams(get_messages_st, crit, range, FasteningCollation.full);

					log.log(Level.FINEST, () -> "Executing getting items items, criteria: " + crit + ", get_messages_st: " + get_messages_st);
					rs = get_messages_st.executeQuery();
					while (rs.next()) {
						Item item = newItemInstance();
						item.read(data_repo, rs, crit);
						results.offer(item);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		}

		if (!results.isEmpty()) {
			DomBuilderHandler domHandler = new DomBuilderHandler();

			Date startTimestamp = crit.getStart();
			Item item = null;
			int idx = crit.getRsm().getIndex();
			int i = 0;
			while ((item = results.poll()) != null) {
				// workaround for case in which start was not specified
				if (startTimestamp == null) {
					startTimestamp = item.timestamp;
				}

				parser.parse(domHandler, item.messageStr.toCharArray(), 0, item.messageStr.length());

				Queue<Element> queue = domHandler.getParsedElements();
				Element msg = null;

				item.messageStr = null;
				item.messageEl = queue.poll();
				if (!crit.getUseMessageIdInRsm()) {
					item.id = String.valueOf(idx + i);
				}
				itemHandler.itemFound(crit, item);
				queue.clear();
				i++;
			}

			crit.setStart(startTimestamp);
		}
	}
	
	private Integer getItemsCount(Q crit) throws SQLException {
		log.log(Level.FINEST, () -> "Getting items count, criteria: " + crit);
		Integer count = null;
		ResultSet rs = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_COUNT_QUERY);
		synchronized (get_messages_st) {
			try {
				setCountQueryParams(get_messages_st, crit, FasteningCollation.full);

				rs = get_messages_st.executeQuery();
				if (rs.next()) {
					count = rs.getInt(1);
				}
			} finally {
				data_repo.release(null, rs);
			}
		}
		return count;
	}

	private Integer getItemPosition(String uid, Q query) throws SQLException, ComponentException {
		log.log(Level.FINEST, () -> "Getting item position, criteria: " + query + ", uid: " + uid);
		if (uid == null || uid.isEmpty()) {
			return null;
		}

		if (!query.getUseMessageIdInRsm()) {
			return Integer.parseInt(uid);
		}

		Integer position = null;
		ResultSet rs = null;
		BareJID owner = query.getQuestionerJID().getBareJID();
		PreparedStatement get_message_position_st = data_repo.getPreparedStatement(owner, GET_MESSAGE_POSITION_QUERY);
		synchronized (get_message_position_st) {
			try {
				int i = setCountQueryParams(get_message_position_st, query, FasteningCollation.full);
				get_message_position_st.setString(i++, uid);

				rs = get_message_position_st.executeQuery();
				if (rs.next()) {
					position = rs.getInt(1);
				}
			} finally {
				data_repo.release(null, rs);
			}
		}

		if (position == null || position < 1) {
			throw new ComponentException(Authorization.ITEM_NOT_FOUND, "Item with id " + uid + " not found");
		}

		return position - 1;
	}

	public static class Collection<Q extends QueryCriteria> implements MessageArchiveRepository.Collection {

		private Date startTs;
		private String with;

		@Override
		public Date getStartTs() {
			return startTs;
		}

		@Override
		public String getWith() {
			return with;
		}

		protected int read(DataRepository repo, ResultSet rs, Q crit) throws SQLException {
			int i = 1;
			startTs = repo.getTimestamp(rs, i++);
			with = rs.getString(i++);
			return i;
		}

	}


	public static class Item<Q extends QueryCriteria>
			implements MessageArchiveRepository.Item {

		BareJID owner;
		String id;
		Element messageEl;
		String messageStr;
		Date timestamp;
		String with;
		String refId;

		@Override
		public String getId() {
			return id;
		}

		@Override
		public Direction getDirection() {
			JID jid = JID.jidInstanceNS(getMessage().getAttributeStaticStr("to"));
			if (jid == null || !owner.equals(jid.getBareJID())) {
				return Direction.outgoing;
			} else {
				return Direction.incoming;
			}
		}

		@Override
		public Element getMessage() {
			return messageEl;
		}

		@Override
		public Date getTimestamp() {
			return timestamp;
		}

		@Override
		public String getWith() {
			return with;
		}

		protected int read(DataRepository repo, ResultSet rs, Q crit) throws SQLException {
			owner = crit.getQuestionerJID().getBareJID();
			int i = 1;
			messageStr = rs.getString(i++);
			timestamp = repo.getTimestamp(rs, i++);
			if (crit.getWith() == null) {
				with = rs.getString(i);
			}
			i++;
			id = rs.getString(i++).toLowerCase();
			refId = rs.getString(i++);
			if (refId != null) {
				refId = refId.toLowerCase();
			}
			return i;
		}
	}

	@FunctionalInterface
	public interface AddMessageAdditionalDataProvider extends AbstractMessageArchiveRepository.AddMessageAdditionalDataProvider {
		void apply(PreparedStatement stmt, int idx) throws SQLException;
	}
}

//~ Formatted in Tigase Code Convention on 13/02/20
