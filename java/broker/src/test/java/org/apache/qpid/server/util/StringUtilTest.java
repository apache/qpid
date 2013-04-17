package org.apache.qpid.server.util;

import org.apache.qpid.server.util.StringUtil;
import org.apache.qpid.test.utils.QpidTestCase;

public class StringUtilTest extends QpidTestCase
{
    private StringUtil _util;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _util = new StringUtil();
    }

    public void testRandomAlphaNumericStringInt()
    {
        String password = _util.randomAlphaNumericString(10);
        assertEquals("Unexpected password string length", 10, password.length());
        assertCharacters(password);
    }

    private void assertCharacters(String password)
    {
        String numbers = "0123456789";
        String letters = "abcdefghijklmnopqrstuvwxwy";
        String others = "_-";
        String expectedCharacters = (numbers + letters + letters.toUpperCase() + others);
        char[] chars = password.toCharArray();
        for (int i = 0; i < chars.length; i++)
        {
            char ch = chars[i];
            assertTrue("Unexpected character " + ch, expectedCharacters.indexOf(ch) != -1);
        }
    }

}
