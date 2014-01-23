/*
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
 */
package org.apache.qpid.server.filter;

import junit.framework.TestCase;

public class JMSSelectorFilterTest extends TestCase
{
    public void testEqualsAndHashCodeUsingSelectorString() throws Exception
    {
        final String selectorString = "1 = 1";

        JMSSelectorFilter filter1 = new JMSSelectorFilter(new String(selectorString));
        JMSSelectorFilter filter2 = new JMSSelectorFilter(new String(selectorString));

        assertEquals(filter1 + " should equal itself", filter1, filter1);
        assertFalse(filter1 + " should not equal null", filter1.equals(null));
        assertEqualsAndHashCodeMatch(filter1, filter2);

        JMSSelectorFilter differentFilter = new JMSSelectorFilter("2 = 2");
        assertNotEqual(filter1, differentFilter);
    }

    private void assertEqualsAndHashCodeMatch(JMSSelectorFilter filter1, JMSSelectorFilter filter2)
    {
        String message = filter1 + " and " + filter2 + " should be equal";

        assertEquals(message, filter1, filter2);
        assertEquals(message, filter2, filter1);

        assertEquals("HashCodes of " + filter1 + " and " + filter2 + " should be equal",
                filter1.hashCode(), filter2.hashCode());
    }

    private void assertNotEqual(JMSSelectorFilter filter, JMSSelectorFilter differentFilter)
    {
        assertFalse(filter.equals(differentFilter));
        assertFalse(differentFilter.equals(filter));
    }
}
