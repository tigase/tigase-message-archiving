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

import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.Statement;
import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.AbstractDataSourceAwareTestCase;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.db.TigaseDBException;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * @author andrzej
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractMessageArchiveRepositoryTest<DS extends DataSource, R extends MessageArchiveRepository> extends AbstractDataSourceAwareTestCase<DS, R> {

	private final static SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");
	protected static String emoji = "\uD83D\uDE97\uD83D\uDCA9\uD83D\uDE21";

	@ClassRule
	public static TestRule rule = new TestRule() {
		@Override
		public Statement apply(Statement stmnt, Description d) {
			if (uri == null) {
				return new Statement() {
					@Override
					public void evaluate() throws Throwable {
						Assume.assumeTrue("Ignored due to not passed DB URI!", false);
					}
				};
			}
			return stmnt;
		}
	};
	
	private static JID buddy = null;
	private static JID buddy2 = null;
	private static JID owner = null;
	// this is static to pass date from first test to next one
	private static Date testStart = null;

	static {
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected boolean checkEmoji = true;
	protected DataSource dataSource;
	protected MessageArchiveRepository<QueryCriteria, DataSource> repo;

	@BeforeClass
	public static void initialize() {
		owner = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-1");
		buddy = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-2");
		buddy2 = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-3");
	}

	@Before
	public void setup() {
		repo = getDataSourceAware();
	}

	@Test
	public void test1_archiveMessage1() throws RepositoryException, ComponentException {
		if (uri == null) {
			return;
		}
		Date date = new Date();
		String body = "Test 1";
		if (checkEmoji) {
			body += emoji;
		}
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{owner.toString(), buddy2.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy2, MessageArchiveRepository.Direction.outgoing, date, msg, null);

		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy2.copyWithoutResource());
		crit.setStart(date);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName() + "/body"));
	}
	
	@Test
	public void test1_archiveMessage2() throws RepositoryException, ComponentException {
		if (uri == null) {
			return;
		}
		Date date = new Date();
		testStart = date;
		String body = "Test 1";
		if (checkEmoji) {
			body += emoji;
		}
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.outgoing, date, msg, null);

		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(date);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName() + "/body"));
	}

	@Test
	public void test2_archiveMessage2withTags() throws InterruptedException, RepositoryException, ComponentException {
		Thread.sleep(2000);
		Date date = new Date();
		String body = "Test 2 with #Test123";
		if (checkEmoji) {
			body += emoji;
		}
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		Set<String> tags = new HashSet<String>();
		if (checkEmoji) {
			tags.add("#Test123" + emoji);
		} else {
			tags.add("#Test123");
		}
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.incoming, date, msg, tags);

		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(date);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName() + "/body"));
	}

	@Test
	public void test3_getCollections() throws TigaseDBException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(
				new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());

		ColItem chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID(), BareJID.bareJIDInstanceNS(chat.with));
		Assert.assertEquals("Incorrect timestamp", testStart.getTime() / 1000, chat.ts.getTime() / 1000);
	}

	@Test
	public void test3_getCollectionsByTag() throws TigaseDBException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}
		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(
				new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());

		ColItem chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID(), BareJID.bareJIDInstanceNS(chat.with));
	}

	@Test
	public void test4_getItems_withIndex() throws InterruptedException, RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null) {
				qc.getRsm().setFirst(item.getId());
			}
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 2, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		res = msgs.get(1);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		String first = crit.getRsm().getFirst();
		String last = crit.getRsm().getLast();

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setAfter(first);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(1, msgs.size());

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setBefore(last);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(1, msgs.size());

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);
	}

	@Test
	public void test4_getItems_withUID() throws InterruptedException, RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null) {
				qc.getRsm().setFirst(item.getId());
			}
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 2, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		res = msgs.get(1);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		String first = crit.getRsm().getFirst();
		String last = crit.getRsm().getLast();

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setAfter(first);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(1, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setBefore(last);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(1, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);
	}

	@Test
	public void test4_getItemsWithTag_withIndex() throws InterruptedException, RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null) {
				qc.getRsm().setFirst(item.getId());
			}
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		String first = crit.getRsm().getFirst();

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}
		crit.getRsm().setAfter(first);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(0, msgs.size());

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);
	}

	@Test
	public void test4_getItemsWithTag_withUID() throws InterruptedException, RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null) {
				qc.getRsm().setFirst(item.getId());
			}
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));

		String first = crit.getRsm().getFirst();

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}
		crit.getRsm().setAfter(first);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(0, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addTag("#Test123" + emoji);
		} else {
			crit.addTag("#Test123");
		}

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);
	}

	@Test
	public void test5_getCollectionsContains() throws TigaseDBException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addContains("Test 1");

		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(
				new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());

		ColItem chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID(), BareJID.bareJIDInstanceNS(chat.with));
		Assert.assertEquals("Incorrect timestamp", testStart.getTime() / 1000, chat.ts.getTime() / 1000);

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		if (checkEmoji) {
			crit.addContains("Test 123" + emoji);
		} else {
			crit.addContains("Test 123");
		}

		chats.clear();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(
				new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 0, chats.size());
	}

	@Test
	public void test6_getItems() throws InterruptedException, RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addContains("Test 1");

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(),
							res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1" + (checkEmoji ? emoji : ""),
							res.getChildCData(res.getName() + "/body"));
	}

	@Test
	public void test7_removeItems() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertNotEquals("No messages in repository to execute test - we should have some already", 0,
							   msgs.size());
		repo.removeItems(owner.getBareJID(), buddy.getBareJID().toString(), testStart, new Date());
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Still some messages, while in this duration all should be deleted", 0, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertNotEquals("No messages in repository to execute test - we should have some already", 0,
							   msgs.size());
		repo.removeItems(owner.getBareJID(), null, null, null);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Still some messages, while in this duration all should be deleted", 0, msgs.size());
	}

	@Test
	public void test8_removeExpiredItems() throws RepositoryException, TigaseStringprepException, ComponentException {
		Date date = new Date();
		String uuid = UUID.randomUUID().toString();
		testStart = date;
		String body = "Test 1 " + uuid;
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		Element delay = new Element("delay");
		LocalDateTime time = LocalDateTime.now().minusDays(1).minusHours(1);
		Date originalTime = new Date(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		delay.setAttribute("stamp", formatter2.format(originalTime));
		msg.addChild(delay);
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.outgoing, originalTime, msg,
							null);

		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.addContains(uuid);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Incorrect number of messages", 1, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(date);
		crit.addContains(uuid);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Incorrect number of messages", 0, msgs.size());

		LocalDateTime before = LocalDateTime.now().minusDays(1);
		repo.deleteExpiredMessages(BareJID.bareJIDInstance(owner.getDomain()), before);

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.addContains(uuid);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Incorrect number of messages", 0, msgs.size());
	}

	@Test
	public void test9_jidComparison() throws TigaseStringprepException, ComponentException, RepositoryException {
		Date date = new Date();
		String uuid = UUID.randomUUID().toString();
		testStart = date;
		String body = "Test 1 " + uuid;
		// owner and buddy jids starts with "UA-" so using lowercased version of this jid will ensure proper
		// comparison of jids
		BareJID ownerLower = BareJID.bareJIDInstance(owner.getLocalpart().toLowerCase(), owner.getDomain());
		JID buddyLower = JID.jidInstance(buddy.getLocalpart().toLowerCase(), buddy.getDomain(), buddy.getResource());
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{ownerLower.toString(), buddyLower.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(ownerLower, buddyLower, MessageArchiveRepository.Direction.outgoing, testStart, msg, null);

		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.addContains(uuid);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(1);
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Incorrect number of messages", 1, msgs.size());

		repo.removeItems(owner.getBareJID(), buddy.getBareJID().toString(), new Date(date.getTime() - 1000),
						 new Date());
	}

	@Override
	protected Class<? extends DataSourceAware> getDataSourceAwareIfc() {
		return MessageArchiveRepository.class;
	}

	private class ColItem {

		private Date ts;
		private String with;

		public ColItem(String with, Date ts) {
			this.with = with;
			this.ts = ts;
		}
	}
}
