package tigase.archive;

import org.junit.Test;
import tigase.xml.Element;

import static org.junit.Assert.*;

public class MessageArchiveVHostItemExtensionTest {

	@Test
	public void initFromElement() {
		final MessageArchiveVHostItemExtension extension = new MessageArchiveVHostItemExtension();
		final Element item = new Element(MessageArchiveVHostItemExtension.ID);
		extension.initFromElement(item);
		assertTrue(extension.isEnabled());
		assertNull(extension.toElement());
	}
}