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

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseException;
import org.apache.log4j.Logger;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;
import org.apache.qpid.server.store.berkeleydb.FieldTableEncoding;
import org.apache.qpid.server.store.berkeleydb.records.QueueRecord;

public class QueueTuple_5 extends QueueTuple_4
{
    protected static final Logger _logger = Logger.getLogger(QueueTuple_5.class);

    protected FieldTable _arguments;

    public QueueTuple_5()
    {
        super();
    }

    public QueueRecord entryToObject(TupleInput tupleInput)
    {
        try
        {
            AMQShortString name = AMQShortStringEncoding.readShortString(tupleInput);
            AMQShortString owner = AMQShortStringEncoding.readShortString(tupleInput);
            // Addition for Version 2 of this table, read the queue arguments
            FieldTable arguments = FieldTableEncoding.readFieldTable(tupleInput);
            // Addition for Version 3 of this table, read the queue exclusivity
            boolean exclusive = tupleInput.readBoolean();

            return new QueueRecord(name, owner, exclusive, arguments);
        }
        catch (DatabaseException e)
        {
            _logger.error("Unable to create binding: " + e, e);
            return null;
        }

    }

    public void objectToEntry(QueueRecord queue, TupleOutput tupleOutput)
    {
        AMQShortStringEncoding.writeShortString(queue.getNameShortString(), tupleOutput);
        AMQShortStringEncoding.writeShortString(queue.getOwner(), tupleOutput);
        // Addition for Version 2 of this table, store the queue arguments
        FieldTableEncoding.writeFieldTable(queue.getArguments(), tupleOutput);
        // Addition for Version 3 of this table, store the queue exclusivity
        tupleOutput.writeBoolean(queue.isExclusive());
    }
}
