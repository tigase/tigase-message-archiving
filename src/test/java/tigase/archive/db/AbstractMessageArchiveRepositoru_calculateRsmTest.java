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

import org.junit.Test;
import tigase.archive.xep0136.Query;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.util.MAMUtil;
import tigase.xmpp.mam.util.Range;
import tigase.xmpp.rsm.RSM;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class AbstractMessageArchiveRepositoru_calculateRsmTest {

	private DummyMessageArchiveRepository repo = new DummyMessageArchiveRepository();

	@Test
	public void testRangeSize1() {
		Range range = new Range(5, 10);
		assertEquals(5, range.size());
	}

	@Test
	public void testRangeSize2() {
		Range range =MAMUtil.rangeFromPositions(5, 10);
		assertEquals(4, range.size());
	}

	@Test
	public void testRsmCalculcationPlainRSM() {
		int count = 50;
		RSM rsm = new RSM(100);
		repo.calculateOffsetAndPosition(rsm, count, null, null);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(100, rsm.getMax());
	}

	@Test
	public void testRsmCalculcationPlain_RSMAfterId() {
		int count = 50;
		RSM rsm = new RSM(100);
		repo.calculateOffsetAndPosition(rsm, count, null, 10);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(11, (int) rsm.getIndex());
		assertEquals(100, rsm.getMax());
	}

	@Test
	public void testRsmCalculcationPlain_RSMBeforeId() {
		int count = 50;
		RSM rsm = new RSM(20);
		repo.calculateOffsetAndPosition(rsm, count, 30, null);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(10, (int) rsm.getIndex());
		assertEquals(20, rsm.getMax());
	}

	@Test
	public void testRsmCalculcationPlain_RSMHasBefore() {
		int count = 50;
		RSM rsm = new RSM(15);
		repo.calculateOffsetAndPosition(rsm, count, 30, null);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(15, (int) rsm.getIndex());
		assertEquals(15, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_AfterId() {
		int count = 50;
		RSM rsm = new RSM(100);
		Range range = MAMUtil.rangeFromPositions(9, null);
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, null, range);
		assertEquals(40, (int) rsm.getCount());
		assertEquals(0, (int) rsm.getIndex());
		assertEquals(40, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_BeforeId() {
		int count = 50;
		RSM rsm = new RSM(100);
		Range range = MAMUtil.rangeFromPositions(null, 40);
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, null, range);
		assertEquals(40, (int) rsm.getCount());
		assertEquals(0, (int) rsm.getIndex());
		assertEquals(40, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_AfterIdAndBeforeId() {
		int count = 50;
		RSM rsm = new RSM(100);
		Range range = MAMUtil.rangeFromPositions(9, 40);
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, null, range);
		assertEquals(30, (int) rsm.getCount());
		assertEquals(0, (int) rsm.getIndex());
		assertEquals(30, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_AfterIdAndBeforeIdAndRSMAfterId() {
		int count = 50;
		RSM rsm = new RSM(100);
		Range range = MAMUtil.rangeFromPositions(9, 40);
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, 14, range);
		assertEquals(30, (int) rsm.getCount());
		assertEquals(5, (int) rsm.getIndex());
		assertEquals(25, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_AfterIdAndBeforeIdAndRSMBeforeId() {
		int count = 50;
		RSM rsm = new RSM(20);
		Range range = MAMUtil.rangeFromPositions(9, 40);
		MAMUtil.calculateOffsetAndPosition(rsm, count, 36, null, range);
		assertEquals(30, (int) rsm.getCount());
		assertEquals(6, (int) rsm.getIndex());
		assertEquals(20, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_AfterIdAndBeforeIdAndRSMHasBefore() {
		int count = 50;
		RSM rsm = new RSM(20);
		rsm.setHasBefore(true);
		Range range = MAMUtil.rangeFromPositions(9, 40);
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, null, range);
		assertEquals(30, (int) rsm.getCount());
		assertEquals(10, (int) rsm.getIndex());
		assertEquals(20, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_RSMAfterId() {
		int count = 50;
		RSM rsm = new RSM(100);
		Range range = Range.FULL;
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, 14, range);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(15, (int) rsm.getIndex());
		assertEquals(35, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_RSMBeforeId() {
		int count = 50;
		RSM rsm = new RSM(20);
		Range range = Range.FULL;
		MAMUtil.calculateOffsetAndPosition(rsm, count, 35, null, range);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(15, (int) rsm.getIndex());
		assertEquals(20, rsm.getMax());
	}

	@Test
	public void testRsmCalculationExtended_RSMHasBefore() {
		int count = 50;
		RSM rsm = new RSM(20);
		rsm.setHasBefore(true);
		Range range = Range.FULL;
		MAMUtil.calculateOffsetAndPosition(rsm, count, null, null, range);
		assertEquals(50, (int) rsm.getCount());
		assertEquals(30, (int) rsm.getIndex());
		assertEquals(20, rsm.getMax());
	}
	
	private class DummyMessageArchiveRepository extends AbstractMessageArchiveRepository {

		@Override
		public void archiveMessage(BareJID owner, JID buddy, Date timestamp, Element msg, String stableId, Set tags) {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public void deleteExpiredMessages(BareJID owner, LocalDateTime before) throws TigaseDBException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public String getStableId(BareJID owner, BareJID buddy, String stanzaId) throws TigaseDBException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public void queryItems(tigase.xmpp.mam.Query query, ItemHandler itemHandler)
				throws RepositoryException, ComponentException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public tigase.xmpp.mam.Query newQuery() {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public List<String> getTags(BareJID owner, String startsWith, Query criteria) throws TigaseDBException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public void queryCollections(Query query, CollectionHandler collectionHandler) throws TigaseDBException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		public void setDataSource(DataSource dataSource) throws RepositoryException {
			throw new RuntimeException("Feature not implemented");
		}

		@Override
		protected void archiveMessage(BareJID owner, BareJID buddy, Date timestamp, Element msg, String stableId,
									  String stanzaId, String refStableId, Set tags,
									  AddMessageAdditionalDataProvider additionParametersProvider) {
			throw new RuntimeException("Feature not implemented");
		}
	}

}
