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

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.Statement;
import tigase.archive.AbstractCriteria;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.util.SchemaLoader;
import tigase.util.SchemaLoader.Result;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 *
 * @author andrzej
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractMessageArchiveRepositoryTest {

	private final static SimpleDateFormat formatter2 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ssZ");	

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
	
	private MessageArchiveRepository repo;
	
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

		owner = JID.jidInstanceNS("ua-" + UUID.randomUUID(), "test", "tigase-1");
		buddy = JID.jidInstanceNS("ua-" + UUID.randomUUID(), "test", "tigase-2");
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
	public void setup() throws DBInitException, InstantiationException, IllegalAccessException {
		if (uri == null)
			return;
		
		repo = RepositoryFactory.getRepoClass(MessageArchiveRepository.class, uri).newInstance();
		repo.initRepository(uri, new HashMap<String,String>());
	}
	
	@After
	public void tearDown() {
		if (uri == null)
			return;

		repo.destroy();
		repo = null;
	}
	
	@Test
	public void test1_archiveMessage1() throws TigaseDBException {
		if (uri == null)
			return;
		Date date = new Date();
		testStart = date;
		String body = "Test 1";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.outgoing, date, msg, null);
	
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(date);
		crit.setSize(1);
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName()+"/body"));
	}
	
	@Test
	public void test2_archiveMessage2withTags() throws InterruptedException, TigaseDBException {
		Thread.sleep(2000);
		Date date = new Date();
		String body = "Test 2 with #Test123";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		Set<String> tags = new HashSet<String>();
		tags.add("#Test123");
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.incoming, date, msg, tags);
		
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(date);
		crit.setSize(1);
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", body, res.getChildCData(res.getName()+"/body"));
	}
	
	@Test
	public void test3_getCollections() throws TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<Element> chats = repo.getCollections(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());
		
		Element chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID().toString(), chat.getAttribute("with"));
		Assert.assertEquals("Incorrect timestamp", formatter2.format(testStart), chat.getAttribute("start"));
	}

	@Test
	public void test3_getCollectionsByTag() throws TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		crit.addTag("#Test123");
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<Element> chats = repo.getCollections(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());
		Element chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID().toString(), chat.getAttribute("with"));
	}
	
	@Test
	public void test4_getItems() throws InterruptedException, TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of message", 2, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1", res.getChildCData(res.getName()+"/body"));
		
		res = msgs.get(1);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));
		
		crit = repo.newCriteriaInstance();
		crit.setStart(testStart);
		
		msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);		
	}

	@Test
	public void test4_getItemsWithTag() throws InterruptedException, TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		crit.addTag("#Test123");
		
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.incoming.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 2 with #Test123", res.getChildCData(res.getName()+"/body"));
		
		crit = repo.newCriteriaInstance();
		crit.setStart(testStart);
		crit.addTag("#Test123");
		
		msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertTrue("Incorrect number of message", msgs.size() >= 1);		
	}
	
	@Test
	public void test5_getCollectionsContains() throws TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		crit.addContains("Test 1");
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		List<Element> chats = repo.getCollections(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of collections", 1, chats.size());
		
		Element chat = chats.get(0);
		Assert.assertEquals("Incorrect buddy", buddy.getBareJID().toString(), chat.getAttribute("with"));
		Assert.assertEquals("Incorrect timestamp", formatter2.format(testStart), chat.getAttribute("start"));		
		
		crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		crit.addContains("Test 123");
		
		System.out.println("owner: " + owner + " buddy: " + buddy + " date: " + testStart);
		chats = repo.getCollections(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of collections", 0, chats.size());	
	}
	
	@Test
	public void test6_getItems() throws InterruptedException, TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		crit.addContains("Test 1");
		
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of message", 1, msgs.size());
		
		Element res = msgs.get(0);
		Assert.assertEquals("Incorrect direction", MessageArchiveRepository.Direction.outgoing.toElementName(), res.getName());
		Assert.assertEquals("Incorrect message body", "Test 1", res.getChildCData(res.getName()+"/body"));
	}	
	
	@Test
	public void test7_removeItems() throws TigaseDBException {
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(testStart);
		
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertNotEquals("No messages in repository to execute test - we should have some already", 0, msgs.size());
		repo.removeItems(owner.getBareJID(), buddy.getBareJID().toString(), testStart, new Date());
		msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Still some messages, while in this duration all should be deleted", 0, msgs.size());
	}

	@Test
	public void test8_removeExpiredItems() throws TigaseDBException, TigaseStringprepException {
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
	
		AbstractCriteria crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.addContains(uuid);
		crit.setSize(1);
		List<Element> msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of messages", 1, msgs.size());		
		
		crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.setStart(date);
		crit.addContains(uuid);
		crit.setSize(1);
		msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of messages", 0, msgs.size());

		LocalDateTime before = LocalDateTime.now().minusDays(1);
		repo.deleteExpiredMessages(BareJID.bareJIDInstance(owner.getDomain()), before);
		
		crit = repo.newCriteriaInstance();
		crit.setWith(buddy.getBareJID().toString());
		crit.addContains(uuid);
		crit.setSize(1);
		msgs = repo.getItems(owner.getBareJID(), crit);
		Assert.assertEquals("Incorrect number of messages", 0, msgs.size());		
	}
}
