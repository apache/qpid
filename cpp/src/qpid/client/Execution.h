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
#ifndef _Execution_
#define _Execution_

#include "qpid/framing/SequenceNumber.h"
#include "Demux.h"

namespace qpid {
namespace client {

/**
 * Provides more detailed access to the amqp 'execution layer'.
 */
class Execution 
{
public:
    virtual ~Execution() {}
    virtual void sendSyncRequest() = 0;
    virtual void sendFlushRequest() = 0;
    virtual void completed(const framing::SequenceNumber& id, bool cumulative, bool send) = 0;
    virtual Demux& getDemux() = 0;
    virtual bool isComplete(const framing::SequenceNumber& id) = 0;
    virtual bool isCompleteUpTo(const framing::SequenceNumber& id) = 0;
    virtual void setCompletionListener(boost::function<void()>) = 0;
    virtual void syncWait(const framing::SequenceNumber& id) = 0;
    virtual framing::SequenceNumber lastSent() const = 0;
};

}}

#endif
