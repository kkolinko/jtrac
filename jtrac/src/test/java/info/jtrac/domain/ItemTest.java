package info.jtrac.domain;

import junit.framework.TestCase;

public class ItemTest extends TestCase {
	public void testHtmlEscaping() {
		assertEquals("&nbsp;&nbsp;&nbsp;&nbsp;", Item.fixWhiteSpace("    "));
		assertEquals("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;", Item.fixWhiteSpace(" \t"));
		assertEquals("Hello World", Item.fixWhiteSpace("Hello World"));
		assertEquals("", Item.fixWhiteSpace(""));
		assertEquals("", Item.fixWhiteSpace(null));
		assertEquals("Hello<br/>World", Item.fixWhiteSpace("Hello\nWorld"));
		assertEquals("Hello<br/>&nbsp;&nbsp;World", Item.fixWhiteSpace("Hello\n  World"));
		assertEquals("Hello<br/>&nbsp;World<br/>&nbsp;&nbsp;&nbsp;&nbsp;Everyone", Item.fixWhiteSpace("Hello\n World\n\tEveryone"));
		assertEquals("Hello&nbsp;&nbsp;&nbsp;&nbsp;World", Item.fixWhiteSpace("Hello\tWorld"));
	}

	public void testSetAndGetForCustomInteger() {
		Item item = new Item();
		item.setCusInt01(5);
		assertEquals(item.getCusInt01().intValue(), 5);
	}

}
