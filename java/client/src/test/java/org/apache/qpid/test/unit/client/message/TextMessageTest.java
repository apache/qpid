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
package org.apache.qpid.test.unit.client.message;

import org.apache.qpid.client.message.TestMessageHelper;
import org.apache.qpid.client.message.JMSTextMessage;

import junit.framework.TestCase;

public class TextMessageTest extends TestCase
{
    public void testTextOnConstruction() throws Exception
    {
        JMSTextMessage tm = TestMessageHelper.newJMSTextMessage();
        tm.setText("pies");
        String val = tm.getText();
        assertEquals(val, "pies");
    }

    public void testClearBody() throws Exception
    {
        JMSTextMessage tm = TestMessageHelper.newJMSTextMessage();
        tm.setText("pies");
        tm.clearBody();
        String val = tm.getText();
        assertNull(val);
        tm.setText("Banana");
        val = tm.getText();
        assertEquals(val, "Banana");
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TextMessageTest.class);
    }
}
