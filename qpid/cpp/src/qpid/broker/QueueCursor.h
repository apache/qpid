#ifndef QPID_BROKER_QUEUECURSOR_H
#define QPID_BROKER_QUEUECURSOR_H

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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/sys/IntegerTypes.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class Message;

enum SubscriptionType
{
    CONSUMER,
    BROWSER,
    PURGE,
    REPLICATOR
};

class CursorContext {
  public:
    virtual ~CursorContext() {}
};
/**
 *
 */
class QueueCursor
{
  public:
    QPID_BROKER_EXTERN QueueCursor(SubscriptionType type = CONSUMER);

  private:
    SubscriptionType type;
    int32_t position;
    int32_t version;
    bool valid;
    boost::shared_ptr<CursorContext> context;

    void setPosition(int32_t p, int32_t v);
    bool check(const Message& m);
    bool isValid(int32_t v);

  friend class MessageDeque;
  friend class MessageMap;
  friend class PriorityQueue;
  friend class PagedQueue;
  template <typename T> friend class IndexedDeque;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUECURSOR_H*/
