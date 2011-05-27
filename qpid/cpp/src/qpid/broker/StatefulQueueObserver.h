#ifndef QPID_BROKER_STATEFULQUEUEOBSERVER_H
#define QPID_BROKER_STATEFULQUEUEOBSERVER_H

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
#include "qpid/broker/QueueObserver.h"
#include "qpid/framing/FieldTable.h"

namespace qpid {
namespace broker {

/**
 * Specialized type of QueueObserver that maintains internal state that has to
 * be replicated across clustered brokers.
 */
class StatefulQueueObserver : public QueueObserver
{
  public:
    StatefulQueueObserver(std::string _id) : id(_id) {}
    virtual ~StatefulQueueObserver() {}

    /** This identifier must uniquely identify this particular observer amoung
     * all observers on a queue.  For cluster replication, this id will be used
     * to identify the peer queue observer for synchronization across
     * brokers.
     */
    const std::string& getId() const { return id; }

    /** This method should return the observer's internal state as an opaque
     * map.
     */
    virtual void getState(qpid::framing::FieldTable& state ) const = 0;

    /** The input map represents the internal state of the peer observer that
     * this observer should synchonize to.
     */
    virtual void setState(const qpid::framing::FieldTable&) = 0;


  private:
    std::string id;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_STATEFULQUEUEOBSERVER_H*/
