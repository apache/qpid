#ifndef QPID_BROKER_MESSAGEINTERCEPTOR_H
#define QPID_BROKER_MESSAGEINTERCEPTOR_H

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

#include "Observers.h"

namespace qpid {
namespace broker {

class Message;

/**
 * Interface for classes that want to modify a message as it is processed by the queue.
 */
class MessageInterceptor
{
  public:
    virtual ~MessageInterceptor() {}

    /** Modify a message before it is recorded in durable store */
    virtual void record(Message&) {}
    /** Modify a message as it is being published onto the queue. */
    virtual void publish(Message&) {}
};

class MessageInterceptors : public Observers<MessageInterceptor> {
  public:
    void record(Message& m) {
        each(boost::bind(&MessageInterceptor::record, _1, boost::ref(m)));
    }
    void publish(Message& m) {
        each(boost::bind(&MessageInterceptor::publish, _1, boost::ref(m)));
    }
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_MESSAGEINTERCEPTOR_H*/
