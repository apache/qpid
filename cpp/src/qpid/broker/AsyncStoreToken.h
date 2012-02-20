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

#ifndef qpid_broker_AsyncStoreToken_h_
#define qpid_broker_AsyncStoreToken_h_

#include "qpid/RefCounted.h"
#include <stdint.h> // uint64_t

namespace qpid {
namespace broker {

/**
 * Pointer to data to be persisted.
 */
class StoredData : public qpid::RefCounted
{
public:
    virtual ~StoredData() {}
    virtual uint64_t getSize() = 0;
    virtual void write(char*) = 0;
};

/**
 * Token classes, to be implemented by store:
 * <pre>
 *
 *                 +---------------+
 *                 | IdentityToken |
 *                 +---------------+
 *                         ^
 *                         |
 *         +---------------+--------------+
 *         |               |              |
 *  +------------+  +-------------+  +----------+
 *  | EventToken |  | ConfigToken |  | TxnToken |
 *  +------------+  +-------------+  +----------+
 *         ^               ^
 *         |               |
 * +--------------+  +------------+
 * | MessageToken |  | QueueToken |
 * +--------------+  +------------+
 *
 * </pre>
 */

class IdentityToken : public qpid::RefCounted
{
public :
    virtual ~IdentityToken() {}
};

class EventToken : public IdentityToken
{
public :
    virtual ~EventToken() {}
};

class MessageToken : public EventToken
{
public :
    virtual ~MessageToken() {}
};

class ConfigToken : public IdentityToken
{
public :
    virtual ~ConfigToken() {}
};

class QueueToken : public ConfigToken
{
public :
    virtual ~QueueToken() {}
};

class TxnToken : public IdentityToken
{
public :
    virtual ~TxnToken() {}
};

}} // namespace qpid::broker

#endif // qpid_broker_AsyncStoreToken_h_
