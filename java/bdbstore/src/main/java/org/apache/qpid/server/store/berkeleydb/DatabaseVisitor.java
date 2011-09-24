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
package org.apache.qpid.server.store.berkeleydb;

import org.apache.qpid.AMQStoreException;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;

/** Visitor Interface so that each DatabaseEntry for a database can easily be processed. */
public abstract class DatabaseVisitor
{
    protected int _count;

    abstract public void visit(DatabaseEntry entry, DatabaseEntry value) throws AMQStoreException, DatabaseException;

    public int getVisitedCount()
    {
        return _count;
    }

    public void resetVisitCount()
    {
        _count = 0;
    }
}
