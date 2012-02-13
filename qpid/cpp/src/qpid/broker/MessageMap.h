#ifndef QPID_BROKER_MESSAGEMAP_H
#define QPID_BROKER_MESSAGEMAP_H

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
#include "qpid/broker/Messages.h"
#include "qpid/framing/SequenceNumber.h"
#include <map>
#include <string>

namespace qpid {
namespace broker {

/**
 * Provides a last value queue behaviour, whereby a messages replace
 * any previous message with the same value for a defined property
 * (i.e. the key).
 */
class MessageMap : public Messages
{
  public:
    MessageMap(const std::string& key);
    virtual ~MessageMap() {}

    size_t size();
    bool empty();

    bool deleted(const QueuedMessage&);
    void release(const QueuedMessage&);
    virtual bool acquire(const framing::SequenceNumber&, QueuedMessage&);
    bool find(const framing::SequenceNumber&, QueuedMessage&);
    virtual bool browse(const framing::SequenceNumber&, QueuedMessage&, bool);
    bool consume(QueuedMessage&);
    virtual bool push(const QueuedMessage& added, QueuedMessage& removed);

    void foreach(Functor);
    virtual void removeIf(Predicate);

  protected:
    typedef std::map<std::string, QueuedMessage> Index;
    typedef std::map<framing::SequenceNumber, QueuedMessage> Ordering;
    const std::string key;
    Index index;
    Ordering messages;

    std::string getKey(const QueuedMessage&);
    virtual const QueuedMessage& replace(const QueuedMessage&, const QueuedMessage&);
    void erase(Ordering::iterator);
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_MESSAGEMAP_H*/
