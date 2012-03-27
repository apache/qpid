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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

public class AMQShortStringBinding extends TupleBinding<AMQShortString>
{
    private static final AMQShortStringBinding INSTANCE = new AMQShortStringBinding();

    public static AMQShortStringBinding getInstance()
    {
        return INSTANCE;
    }

    /** private constructor forces getInstance instead */
    private AMQShortStringBinding() { }

    public AMQShortString entryToObject(TupleInput tupleInput)
    {
        return AMQShortStringEncoding.readShortString(tupleInput);
    }

    public void objectToEntry(AMQShortString object, TupleOutput tupleOutput)
    {
        AMQShortStringEncoding.writeShortString(object, tupleOutput);
    }
}
