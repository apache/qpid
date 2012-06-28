#ifndef QPID_BROKER_LEGACYLVQ_H
#define QPID_BROKER_LEGACYLVQ_H

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
#include "qpid/broker/MessageMap.h"
#include <memory>

namespace qpid {
namespace broker {
class Broker;

/**
 * This class encapsulates the behaviour of the old style LVQ where a
 * message replacing another messages for the given key will use the
 * position in the queue of the previous message. This however causes
 * problems for browsing. Either browsers stop the coalescing of
 * messages by key (default) or they may mis updates (if the no-browse
 * option is specified).
 */
class LegacyLVQ : public MessageMap
{
  public:
    LegacyLVQ(const std::string& key, bool noBrowse = false, Broker* broker = 0);
    bool deleted(const QueuedMessage&);
    bool acquire(const framing::SequenceNumber&, QueuedMessage&);
    bool browse(const framing::SequenceNumber&, QueuedMessage&, bool);
    bool push(const QueuedMessage& added, QueuedMessage& removed);
    void removeIf(Predicate);
    void setNoBrowse(bool);
    static std::auto_ptr<Messages> updateOrReplace(std::auto_ptr<Messages> current, 
                                                   const std::string& key, bool noBrowse,
                                                   Broker* broker);
  protected:
    bool noBrowse;
    Broker* broker;

    const QueuedMessage& replace(const QueuedMessage&, const QueuedMessage&);
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_LEGACYLVQ_H*/
