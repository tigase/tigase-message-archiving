/*
 * AbstractMessageArchiveRepositoryTest.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2014 "Tigase, Inc." <office@tigase.com>
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

import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.Statement;
import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.db.*;
import tigase.util.SchemaLoader;
import tigase.util.SchemaLoader.Result;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.mam.MAMRepository;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 *
 * @author andrzej
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractMessageArchiveRepositoryTest {

	private final static SimpleDateFormat formatter2 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ssXX");

	private static final String PROJECT_ID = "message-archiving";
	private static final String VERSION = "1.3.0";

	static {
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private static String uri = System.getProperty("testDbUri");

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
	
	private MessageArchiveRepository<QueryCriteria, DataSource> repo;

	// this is static to pass date from first test to next one
	private static Date testStart = null;

	private static JID owner = null;
	private static JID buddy = null;	
	
	@BeforeClass
	public static void loadSchema() {
		if (uri.startsWith("jdbc:")) {
			String dbType;
			String dbName = null;
			String dbHostname = null;
			String dbUser = null;
			String dbPass = null;
			
			int idx = uri.indexOf(":", 5);
			dbType = uri.substring(5, idx);
			if ("jtds".equals(dbType)) dbType = "sqlserver";
			
			String rest = null;
			switch (dbType) {
				case "derby":
					dbName = uri.substring(idx+1, uri.indexOf(";"));
					cleanDerby();
					break;
				case "sqlserver":
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					for (String x : rest.split(";")) {
						if (!x.contains("=")) {
							dbHostname = x;
						} else {
							String p[] = x.split("=");
							switch (p[0]) {
								case "databaseName":
									dbName = p[1];
									break;
								case "user":
									dbUser = p[1];
									break;
								case "password":
									dbPass = p[1];
									break;
								default:
									// unknown setting
									break;
							}
						}
					}
					break;
				default:
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					idx = rest.indexOf("/");
					dbHostname = rest.substring(0, idx);
					rest = rest.substring(idx+1);
					idx = rest.indexOf("?");
					dbName = rest.substring(0, idx);
					rest = rest.substring(idx + 1);
					for (String x : rest.split("&")) {
						String p[] = x.split("=");
						if (p.length < 2)
							continue;
						switch (p[0]) {
							case "user":
								dbUser = p[1];
								break;
							case "password":
								dbPass = p[1];
								break;
							default:
								break;
						}
					}
					break;
			}

			Properties props = new Properties();
			if (dbType != null)
				props.put("dbType", dbType);
			if (dbName != null)		
				props.put("dbName", dbName);
			if (dbHostname != null)
				props.put("dbHostname", dbHostname);
			if (dbUser != null)
				props.put("rootUser", dbUser);
			if (dbPass != null)
				props.put("rootPass", dbPass);
			if (dbUser != null)
				props.put("dbUser", dbUser);
			if (dbPass != null)
				props.put("dbPass", dbPass);

			SchemaLoader loader = SchemaLoader.newInstance(props);
			loader.validateDBConnection(props);
			loader.validateDBExists(props);
			props.put("file", "database/" + dbType + "-" + PROJECT_ID + "-schema-" + VERSION + ".sql");
			Assert.assertEquals(Result.ok, loader.loadSchemaFile(props));
			loader.shutdown(props);			
		} 

		owner = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-1");
		buddy = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-2");
	}
	
	@AfterClass
	public static void cleanDerby() {
		if (uri.contains("jdbc:derby:")) {
			File f = new File("derby_test");
			if (f.exists()) {
				if (f.listFiles() != null) {
					Arrays.asList(f.listFiles()).forEach(f2 -> {
						if (f2.listFiles() != null) {
							Arrays.asList(f2.listFiles()).forEach(f3 -> f3.delete());
						}
						f2.delete();
					});
				}
				f.delete();
			}			
		}
	}

	
	@Before
	public void setup() throws DBInitException, InstantiationException, IllegalAccessException, SQLException, ClassNotFoundException {
		if (uri == null)
			return;

		DataRepository dataRepo = RepositoryFactory.getDataRepository(null, uri, new HashMap<>());
		repo = DataSourceHelper.getDefaultClass(MessageArchiveRepository.class, uri).newInstance();
		repo.setDataSource(dataRepo);
	}
	
	@After
	public void tearDown() {
		if (uri == null)
			return;

		repo.destroy();
		repo = null;
	}
	
	@Test
	public void test1_archiveMessage1() throws TigaseDBException, ComponentException {
		if (uri == null)
			return;
		Date date = new Date();
		testStart = date;
		String body = "Test 1";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
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
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName()+"/body"));
	}
	
	@Test
	public void test2_archiveMessage2withTags() throws InterruptedException, TigaseDBException, ComponentException {
		Thread.sleep(2000);
		Date date = new Date();
		String body = "Test 2 with #Test123";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		Set<String> tags = new HashSet<String>();
		tags.add("#Test123");
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
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName()+"/body"));
	}
	
	@Test
	public void test3_getCollections() throws TigaseDBException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(new ColItem(with, ts)));
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
		crit.addTag("#Test123");
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());

		ColItem chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID(), BareJID.bareJIDInstanceNS(chat.with));
	}
	
	@Test
	public void test4_getItems_withIndex() throws InterruptedException, TigaseDBException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null)
				qc.getRsm().setFirst(item.getId());
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 2, msgs.size());

		System.out.println(msgs);

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1", res.getChildCData(res.getName()+"/body"));
		
		res = msgs.get(1);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));

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
	public void test4_getItems_withUID() throws InterruptedException, TigaseDBException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null)
				qc.getRsm().setFirst(item.getId());
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 2, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1", res.getChildCData(res.getName()+"/body"));

		res = msgs.get(1);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));

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
	public void test4_getItemsWithTag_withIndex() throws InterruptedException, TigaseDBException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null)
				qc.getRsm().setFirst(item.getId());
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));

		String first = crit.getRsm().getFirst();

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");
		crit.getRsm().setAfter(first);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(0, msgs.size());

		crit = repo.newQuery();
		crit.setUseMessageIdInRsm(false);
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);		
	}

	@Test
	public void test4_getItemsWithTag_withUID() throws InterruptedException, TigaseDBException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");

		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			item.getMessage().setName(((MessageArchiveRepository.Item) item).getDirection().toElementName());
			msgs.add(item.getMessage());
			if (qc.getRsm().getFirst() == null)
				qc.getRsm().setFirst(item.getId());
			qc.getRsm().setLast(item.getId());
		});
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());

		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));

		String first = crit.getRsm().getFirst();

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");
		crit.getRsm().setAfter(first);

		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals(0, msgs.size());

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setStart(testStart);
		crit.addTag("#Test123");

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
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<ColItem> chats = new ArrayList<>();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());
		
		ColItem chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID(), BareJID.bareJIDInstanceNS(chat.with));
		Assert.assertEquals("Incorrect timestamp", testStart.getTime()/1000, chat.ts.getTime()/1000);

		crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.addContains("Test 123");
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		chats.clear();
		repo.queryCollections(crit, (QueryCriteria qc, String with, Date ts, String type) -> chats.add(new ColItem(with, ts)));
		Assert.assertEquals("Incorrect number of collections", 0, chats.size());	
	}
	
	@Test
	public void test6_getItems() throws InterruptedException, TigaseDBException, ComponentException {
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
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1", res.getChildCData(res.getName()+"/body"));
	}	
	
	@Test
	public void test7_removeItems() throws TigaseDBException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		
		List<Element> msgs = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertNotEquals("No messages in repository to execute test - we should have some already", 0, msgs.size());
		repo.removeItems(owner.getBareJID(), buddy.getBareJID().toString(), testStart, new Date());
		msgs.clear();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> msgs.add(item.getMessage()));
		Assert.assertEquals("Still some messages, while in this duration all should be deleted", 0, msgs.size());
	}

	@Test
	public void test8_removeExpiredItems() throws TigaseDBException, TigaseStringprepException, ComponentException {
		Date date = new Date();
		String uuid = UUID.randomUUID().toString();
		testStart = date;
		String body = "Test 1 " + uuid;
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		Element delay = new Element("delay");
		LocalDateTime time = LocalDateTime.now().minusDays(1).minusHours(1);
		Date originalTime = new Date(time.toEpochSecond(ZoneOffset.UTC) * 1000);
		delay.setAttribute("stamp", formatter2.format(originalTime));
		msg.addChild(delay);
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.outgoing, originalTime, msg, null);

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

	private class ColItem {
		private String with;
		private Date ts;

		public ColItem(String with, Date ts) {
			this.with = with;
			this.ts = ts;
		}
	}
}
