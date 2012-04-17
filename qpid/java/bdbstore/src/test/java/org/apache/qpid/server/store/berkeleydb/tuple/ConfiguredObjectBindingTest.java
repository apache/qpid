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
package org.apache.qpid.server.store.berkeleydb.tuple;

import junit.framework.TestCase;

import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.ConfiguredObjectRecord;

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class ConfiguredObjectBindingTest extends TestCase
{

    private ConfiguredObjectRecord _object;

    private static final String DUMMY_ATTRIBUTES_STRING = "dummyAttributes";
    private static final String DUMMY_TYPE_STRING = "dummyType";
    private ConfiguredObjectBinding _configuredObjectBinding;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _configuredObjectBinding = ConfiguredObjectBinding.getInstance();
        _object = new ConfiguredObjectRecord(UUIDGenerator.generateUUID(), DUMMY_TYPE_STRING, DUMMY_ATTRIBUTES_STRING);
    }

    public void testObjectToEntryAndEntryToObject()
    {
        TupleOutput tupleOutput = new TupleOutput();

        _configuredObjectBinding.objectToEntry(_object, tupleOutput);

        byte[] entryAsBytes = tupleOutput.getBufferBytes();
        TupleInput tupleInput = new TupleInput(entryAsBytes);

        ConfiguredObjectRecord storedObject = _configuredObjectBinding.entryToObject(tupleInput);
        assertEquals("Unexpected attributes", DUMMY_ATTRIBUTES_STRING, storedObject.getAttributes());
        assertEquals("Unexpected type", DUMMY_TYPE_STRING, storedObject.getType());
    }
}
