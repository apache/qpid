#ifndef QPID_BROKER_QUEUEREPLICATOR_H
#define QPID_BROKER_QUEUEREPLICATOR_H

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
#include "qpid/broker/Exchange.h"
#include "qpid/framing/SequenceSet.h"

namespace qpid {
namespace broker {

class QueueRegistry;

/**
 * Dummy exchange for processing replication messages
 */
class QueueReplicator : public Exchange
{
  public:
    QueueReplicator(const std::string& name, boost::shared_ptr<Queue>);
    ~QueueReplicator();
    std::string getType() const;
    bool bind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*);
    bool unbind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*);
    void route(Deliverable&, const std::string&, const qpid::framing::FieldTable*);
    bool isBound(boost::shared_ptr<Queue>, const std::string* const, const qpid::framing::FieldTable* const);
    static bool isReplicatingLink(const std::string&);
    static boost::shared_ptr<Exchange> create(const std::string&, QueueRegistry&);
    static bool initReplicationSettings(const std::string&, QueueRegistry&, qpid::framing::FieldTable&);
    static const std::string typeName;
  private:
    boost::shared_ptr<Queue> queue;
    qpid::framing::SequenceNumber current;
    qpid::framing::SequenceSet dequeued;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEREPLICATOR_H*/
