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
import org.junit.runners.model.Statement;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.sql.SQLException;
import java.util.*;

/**
 * @author andrzej
 */
public class JDBCMessageArchiveRepositoryTest
		extends AbstractMessageArchiveRepositoryTest<DataRepository, MessageArchiveRepository> {

	private static final String PROJECT_ID = "message-archiving";
	private static final String VERSION = "3.0.0-SNAPSHOT";

	@ClassRule
	public static TestRule rule = new TestRule() {
		@Override
		public Statement apply(Statement stmnt, Description d) {
			if (uri == null || !uri.startsWith("jdbc:")) {
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

	@BeforeClass
	public static void loadSchema() throws DBInitException {
		loadSchema(PROJECT_ID, VERSION, Collections.singleton("message-archive"));
	}
	
	// This test to work requires change in archiveMessage method to throw Exception - by default exception is catched in this method
	@Ignore
	@Test
	public void testDeadlocksOnInsert()
			throws InterruptedException, ClassNotFoundException, SQLException, InstantiationException,
				   IllegalAccessException {
		try {
			Map<String, String> params = new HashMap<String, String>();
			params.put(RepositoryFactory.DATA_REPO_POOL_SIZE_PROP_KEY, "40");
			DataRepository dataRepo = RepositoryFactory.getDataRepository(null, uri, params);
			JDBCMessageArchiveRepository repo = new JDBCMessageArchiveRepository();
			repo.setDataSource(dataRepo);

			Queue<Thread> threads = new ArrayDeque<Thread>();
			for (int i = 0; i < 128; i++) {
				final int ti = i;
				final JID jid = JID.jidInstanceNS("user-" + i + "@test-domain.com/res-1");
				Thread t = new Thread() {
					@Override
					public void run() {
						try {
							for (int j = 0; j < 1000; j++) {
								Element message = new Element("message", new String[]{"from", "to", "type", "id"},
															  new String[]{jid.toString(), jid.getBareJID().toString(),
																		   "set", UUID.randomUUID().toString()});
								message.addChild(
										new Element("bind", UUID.randomUUID().toString(), new String[]{"action"},
													new String[]{"login"}));
								repo.archiveMessage(jid.getBareJID(), jid,
													new Date(), message, UUID.randomUUID().toString(), new HashSet<String>());
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
