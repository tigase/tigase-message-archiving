package tigase.archive.db;

import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.Statement;
import tigase.archive.FasteningCollation;
import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.AbstractDataSourceAwareTestCase;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.server.Message;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.text.SimpleDateFormat;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractMessageArchiveRepository_FasteningTest <DS extends DataSource, R extends MessageArchiveRepository> extends
																														AbstractDataSourceAwareTestCase<DS, R> {

	private final static SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");

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
	private static JID owner = null;
	// this is static to pass date from first test to next one
	private static Date testStart = null;
	private static String stanzaId1;
	private static String stableId1;
	private static String stanzaId2;
	private static String stableId2;

	static {
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected DataSource dataSource;
	protected MessageArchiveRepository<QueryCriteria, DataSource> repo;

	@BeforeClass
	public static void initialize() {
		owner = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-1");
		buddy = JID.jidInstanceNS("UA-" + UUID.randomUUID(), "test", "tigase-2");
	}

	@Before
	public void setup() {
		repo = getDataSourceAware();
	}


	@Override
	protected Class<? extends DataSourceAware> getDataSourceAwareIfc() {
		return MessageArchiveRepository.class;
	}

	@Test
	public void test1_archiveMessageAndCheckStableId()
			throws RepositoryException, ComponentException, InterruptedException {
		Date date = new Date();
		testStart = date;
		String body = "Test 1";
		stanzaId1 = UUID.randomUUID().toString();
		stableId1 = UUID.randomUUID().toString();

		archiveMessage(body, stableId1, stanzaId1, date);
		String stableId = repo.getStableId(owner.getBareJID(), buddy.getBareJID(), stanzaId1);
		Assert.assertEquals("Found invalid stable id:", this.stableId1, stableId);

		Thread.sleep(10);
		date = new Date();
		body = "Test 2";
		stanzaId2 = UUID.randomUUID().toString();
		stableId2 = UUID.randomUUID().toString();

		archiveMessage(body, stableId2, stanzaId2, date);
		stableId = repo.getStableId(owner.getBareJID(), buddy.getBareJID(), stanzaId2);
		Assert.assertEquals("Found invalid stable id:", this.stableId2, stableId);
	}

	@Test
	public void test2_archiveMessageDeliveryReceipt() throws RepositoryException, ComponentException {
		archiveMessageDeliveryReceipt(UUID.randomUUID().toString(), stanzaId1, new Date());
		archiveMessageDeliveryReceipt(UUID.randomUUID().toString(), stanzaId2, new Date());
	}

	@Test
	public void test3_retrieval_fasteningCollationSimplified() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.simplified);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(2, items.size());
		Assert.assertEquals("Test 1", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
	}

	@Test
	public void test3_retrieval_fasteningCollationFull() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.full);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(4, items.size());
		Assert.assertEquals(4, (int) crit.getRsm().getCount());
		Assert.assertEquals("Test 1", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals("Test 2", items.get(1).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertNotNull(items.get(2).getMessage().findChild(el -> el.getName() == "received"));
		Assert.assertNotNull(items.get(3).getMessage().findChild(el -> el.getName() == "received"));
	}

	@Test
	public void test3_retrieval_fasteningCollationCollate() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.collate);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(2, items.size());
		Assert.assertEquals("Test 1", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals("Test 2", items.get(1).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
		Assert.assertEquals(1, items.get(0).getFastenings().size());
		Assert.assertEquals(1, items.get(1).getFastenings().size());
		Assert.assertNotNull(items.get(0).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
		Assert.assertNotNull(items.get(1).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
	}
	
	@Test
	public void test3_retrieval_fasteningCollationFastenings() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.fastenings);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(2, items.size());
		Assert.assertNull(items.get(0).getMessage());
		Assert.assertNull(items.get(1).getMessage());
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
		Assert.assertEquals(1, items.get(0).getFastenings().size());
		Assert.assertEquals(1, items.get(1).getFastenings().size());
		Assert.assertNotNull(items.get(0).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
		Assert.assertNotNull(items.get(1).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
	}

	@Test
	public void test4_retrievalAfter_fasteningCollationSimplified() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setAfter(stableId1);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.simplified);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("Test 2", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
	}

	@Test
	public void test4_retrievaAfter_fasteningCollationFull() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setAfter(stableId1);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.full);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(3, items.size());
		Assert.assertEquals(4, (int) crit.getRsm().getCount());
		Assert.assertEquals("Test 2", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertNotNull(items.get(1).getMessage().findChild(el -> el.getName() == "received"));
		Assert.assertNotNull(items.get(2).getMessage().findChild(el -> el.getName() == "received"));
	}

	@Test
	public void test4_retrievalAfter_fasteningCollationCollate() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setAfter(stableId1);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.collate);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("Test 2", items.get(0).getMessage().getCData(Message.MESSAGE_BODY_PATH));
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
		Assert.assertEquals(1, items.get(0).getFastenings().size());
		Assert.assertNotNull(items.get(0).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
	}

	@Test
	public void test4_retrievalAfter_fasteningCollationFastenings() throws RepositoryException, ComponentException {
		QueryCriteria crit = repo.newQuery();
		crit.setQuestionerJID(owner.copyWithoutResource());
		crit.setWith(buddy.copyWithoutResource());
		crit.setStart(testStart);
		crit.getRsm().setIndex(0);
		crit.getRsm().setAfter(stableId1);
		crit.getRsm().setMax(100);
		crit.setFasteningCollation(FasteningCollation.fastenings);
		List<MAMRepository.Item> items = new ArrayList<>();
		repo.queryItems(crit, (QueryCriteria qc, MAMRepository.Item item) -> {
			items.add(item);
		});
		Assert.assertEquals(1, items.size());
		Assert.assertNull(items.get(0).getMessage());
		Assert.assertEquals(2, (int) crit.getRsm().getCount());
		Assert.assertEquals(1, items.get(0).getFastenings().size());
		Assert.assertNotNull(items.get(0).getFastenings().get(0).getMessage().findChild(el -> el.getName() == "received"));
	}

	protected void archiveMessage(String body, String stableId, String stanzaId, Date date) {
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{owner.toString(), buddy.toString(), StanzaType.chat.name()});
		if (stanzaId != null) {
			msg.setAttribute("id", stanzaId);
		}
		msg.addChild(new Element("body", body));
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.outgoing, date, msg,
							stableId, null);
	}

	protected void archiveMessageDeliveryReceipt(String stableId, String refStanzaId, Date date) {
		Element msg = new Element("message", new String[]{"from", "to", "type"},
								  new String[]{buddy.toString(), owner.toString(), StanzaType.chat.name()});
		msg.addChild(new Element("received", new String[] {"xmlns", "id"}, new String[] {"urn:xmpp:receipts", refStanzaId}));
		repo.archiveMessage(owner.getBareJID(), buddy, MessageArchiveRepository.Direction.incoming, date, msg,
							stableId, null);
	}
		
}
