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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import tigase.archive.AbstractCriteria;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
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
	
	static {
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
	}		
	
	//private String uri = "jdbc:sqlserver://172.16.0.94:1433;databaseName=test;user=test;password=test";
	private String uri = System.getProperty("testDbUri");
	private MessageArchiveRepository repo;
	
	// this is static to pass date from first test to next one
	private static Date testStart = null;

	private JID owner = JID.jidInstanceNS("test1@zeus/tigase-1");
	private JID buddy = JID.jidInstanceNS("test2@zeus/tigase-2");	
	
	
	@Before
	public void setup() throws DBInitException, InstantiationException, IllegalAccessException {
		Assume.assumeNotNull(uri);
		repo = RepositoryFactory.getRepoClass(MessageArchiveRepository.class, uri).newInstance();
		repo.initRepository(uri, new HashMap<String,String>());
	}
	
	@After
	public void tearDown() {
		repo.destroy();
		repo = null;
	}
	
	@Test
	public void test1_archiveMessage1() throws TigaseDBException {
		Date date = new Date();
		testStart = date;
		String body = "Test 1";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy.getBareJID(), MessageArchiveRepository.Direction.outgoing, date, msg);
	
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
	public void test2_archiveMessage2() throws InterruptedException, TigaseDBException {
		Thread.sleep(2000);
		Date date = new Date();
		String body = "Test 2";
		Element msg = new Element("message", new String[] { "from", "to", "type"}, new String[] { owner.toString(), buddy.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy.getBareJID(), MessageArchiveRepository.Direction.incoming, date, msg);
		
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
		Assert.assertEquals("Incorrect message body", "Test 2", res.getChildCData(res.getName()+"/body"));
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

	
}
