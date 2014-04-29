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
package org.apache.qpid.server.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;

public class AbstractConfiguredObjectTest extends TestCase
{


    public void testNonPersistAttributes()
    {
        Model model = TestModel.getInstance();

        final String objectName = "testNonPersistAttributes";
        TestRootCategory object =
                model.getObjectFactory().create(TestRootCategory.class,
                                                Collections.<String, Object>singletonMap(ConfiguredObject.NAME,
                                                                                         objectName));

        assertEquals(objectName, object.getName());
        assertNull(object.getAutomatedNonPersistedValue());
        assertNull(object.getAutomatedPersistedValue());

        ConfiguredObjectRecord record = object.asObjectRecord();

        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));

        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_PERSISTED_VALUE));
        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE));


        Map<String,Object> updatedAttributes = new HashMap<>();

        final String newValue = "newValue";

        updatedAttributes.put(TestRootCategory.AUTOMATED_PERSISTED_VALUE, newValue);
        updatedAttributes.put(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE, newValue);
        object.setAttributes(updatedAttributes);

        assertEquals(newValue, object.getAutomatedPersistedValue());
        assertEquals(newValue, object.getAutomatedNonPersistedValue());

        record = object.asObjectRecord();
        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));
        assertEquals(newValue, record.getAttributes().get(TestRootCategory.AUTOMATED_PERSISTED_VALUE));

        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE));

    }
}
