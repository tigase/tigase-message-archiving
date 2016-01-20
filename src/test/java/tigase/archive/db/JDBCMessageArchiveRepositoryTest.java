/*
 * JDBCMessageArchiveRepository.java
 *
 * Tigase Jabber/XMPP Server
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

import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
@Ignore
public class JDBCMessageArchiveRepositoryTest {

	// private String uri = "jdbc:mysql://172.16.0.93:3306/tigase?user=test&password=test&zeroDateTimeBehavior=convertToNull";
	private String uri = System.getProperty("testDbUri");	
	
	@Before
	public void setup() throws DBInitException, InstantiationException, IllegalAccessException {
		Assume.assumeNotNull(uri);
	}

	// This test to work requires change in archiveMessage method to throw Exception - by default exception is catched in this method
	@Test
	public void testDeadlocksOnInsert() throws InterruptedException {
		try {
			Map<String,String> params = new HashMap<String,String>();
			params.put(RepositoryFactory.DATA_REPO_POOL_SIZE_PROP_KEY, "40");
			JDBCMessageArchiveRepository repo = new JDBCMessageArchiveRepository();
			repo.initRepository(uri, params);
			
			Queue<Thread> threads = new ArrayDeque<Thread>();
			for (int i=0; i<128; i++) {
				final int ti = i;
				final JID jid = JID.jidInstanceNS("user-"+i+"@test-domain.com/res-1");
				Thread t = new Thread() {
					@Override
					public void run() {
						try {
						for (int j=0; j<1000; j++) {
							Element message = new Element("message", new String[] { "from", "to", "type", "id"}, new String[] { jid.toString(), jid.getBareJID().toString(), "set", UUID.randomUUID().toString() });
							message.addChild(new Element("bind", UUID.randomUUID().toString(), new String[] { "action" }, new String[] { "login" }));
							repo.archiveMessage(jid.getBareJID(), jid, MessageArchiveRepository.Direction.incoming, new Date(), message, new HashSet<String>());
						}
							System.out.println("executed last insert for thread " + ti);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				};
				threads.offer(t);
				t.start();
			}
			Thread t = null;
			while ((t = threads.poll()) != null) {
				t.join();
			}
		} catch (DBInitException ex) {
			ex.printStackTrace();
		}
	}
}
