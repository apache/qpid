/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.configuration;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.configuration.PropertyException;
import org.apache.qpid.configuration.PropertyUtils;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

// TODO: This belongs in the "common" module.
public class TestPropertyUtils
{
    @Test
    public void testSimpleExpansion() throws PropertyException
    {
        System.setProperty("banana", "fruity");
        String expandedProperty = PropertyUtils.replaceProperties("${banana}");
        assertEquals(expandedProperty, "fruity");
    }

    @Test
    public void testDualExpansion() throws PropertyException
    {
        System.setProperty("banana", "fruity");
        System.setProperty("concrete", "horrible");
        String expandedProperty = PropertyUtils.replaceProperties("${banana}xyz${concrete}");
        assertEquals(expandedProperty, "fruityxyzhorrible");
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TestPropertyUtils.class);
    }
}
