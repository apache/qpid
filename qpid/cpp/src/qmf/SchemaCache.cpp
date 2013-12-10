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

#include "qmf/SchemaCache.h"
#include "qmf/exceptions.h"

using namespace std;
using namespace qmf;

bool SchemaCache::declareSchemaId(const SchemaId& id)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    SchemaMap::const_iterator iter = schemata.find(id);
    if (iter == schemata.end()) {
        schemata[id] = Schema();
        return false;
    }
    return true;
}


void SchemaCache::declareSchema(const Schema& schema)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    SchemaMap::const_iterator iter = schemata.find(schema.getSchemaId());
    if (iter == schemata.end() || !iter->second.isValid()) {
        schemata[schema.getSchemaId()] = schema;

        //
        // If there are any threads blocking in SchemaCache::getSchema waiting for
        // this schema, unblock them all now.
        //
        CondMap::iterator cIter = conditions.find(schema.getSchemaId());
        if (cIter != conditions.end())
            cIter->second->notifyAll();
    }
}


bool SchemaCache::haveSchema(const SchemaId& id) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    SchemaMap::const_iterator iter = schemata.find(id);
    return iter != schemata.end() && iter->second.isValid();
}


const Schema& SchemaCache::getSchema(const SchemaId& id, qpid::messaging::Duration timeout) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    SchemaMap::const_iterator iter = schemata.find(id);
    if (iter != schemata.end() && iter->second.isValid())
        return iter->second;

    //
    // The desired schema is not in the cache.  Assume that the caller knows this and has
    // sent a schema request to the remote agent and now wishes to wait until the schema
    // information arrives.
    //
    CondMap::iterator cIter = conditions.find(id);
    if (cIter == conditions.end())
        conditions[id] = boost::shared_ptr<qpid::sys::Condition>(new qpid::sys::Condition());

    uint64_t milliseconds = timeout.getMilliseconds();
    conditions[id]->wait(lock, qpid::sys::AbsTime(qpid::sys::now(),
                                                  qpid::sys::Duration(milliseconds * qpid::sys::TIME_MSEC)));
    iter = schemata.find(id);
    if (iter != schemata.end() && iter->second.isValid())
        return iter->second;

    throw QmfException("Schema lookup timed out");
}

