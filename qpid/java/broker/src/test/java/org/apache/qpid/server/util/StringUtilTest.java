/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
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
