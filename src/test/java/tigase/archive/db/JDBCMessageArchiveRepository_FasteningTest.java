package tigase.archive.db;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import tigase.db.DBInitException;
import tigase.db.DataRepository;

import java.util.Collections;

public class JDBCMessageArchiveRepository_FasteningTest extends AbstractMessageArchiveRepository_FasteningTest<DataRepository, MessageArchiveRepository> {

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

}
