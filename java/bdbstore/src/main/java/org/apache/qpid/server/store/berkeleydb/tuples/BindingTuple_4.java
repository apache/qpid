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
package org.apache.qpid.server.store.berkeleydb.tuples;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseException;
import org.apache.log4j.Logger;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;
import org.apache.qpid.server.store.berkeleydb.BindingKey;
import org.apache.qpid.server.store.berkeleydb.FieldTableEncoding;

public class BindingTuple_4 extends TupleBinding<BindingKey> implements BindingTuple
{
    protected static final Logger _log = Logger.getLogger(BindingTuple.class);

    public BindingTuple_4()
    {
        super();
    }

    public BindingKey entryToObject(TupleInput tupleInput)
    {
        AMQShortString exchangeName = AMQShortStringEncoding.readShortString(tupleInput);
        AMQShortString queueName = AMQShortStringEncoding.readShortString(tupleInput);
        AMQShortString routingKey = AMQShortStringEncoding.readShortString(tupleInput);

        FieldTable arguments;

        // Addition for Version 2 of this table
        try
        {
            arguments = FieldTableEncoding.readFieldTable(tupleInput);
        }
        catch (DatabaseException e)
        {
            _log.error("Unable to create binding: " + e, e);
            return null;
        }

        return new BindingKey(exchangeName, queueName, routingKey, arguments);
    }

    public void objectToEntry(BindingKey binding, TupleOutput tupleOutput)
    {
        AMQShortStringEncoding.writeShortString(binding.getExchangeName(), tupleOutput);
        AMQShortStringEncoding.writeShortString(binding.getQueueName(), tupleOutput);
        AMQShortStringEncoding.writeShortString(binding.getRoutingKey(), tupleOutput);

        // Addition for Version 2 of this table
        FieldTableEncoding.writeFieldTable(binding.getArguments(), tupleOutput);
    }

}