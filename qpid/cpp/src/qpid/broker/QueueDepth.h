#ifndef QPID_BROKER_QUEUEDEPTH_H
#define QPID_BROKER_QUEUEDEPTH_H

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
#include <ostream>

namespace qpid {
namespace broker {

/**
 * Represents a queue depth in message count and/or aggregate message
 * size.
 */
class QueueDepth
{
  public:
    QPID_BROKER_EXTERN QueueDepth();
    QPID_BROKER_EXTERN QueueDepth(uint32_t count, uint64_t size);
    QPID_BROKER_EXTERN QueueDepth& operator+=(const QueueDepth&);
    QPID_BROKER_EXTERN QueueDepth& operator-=(const QueueDepth&);
    QPID_BROKER_EXTERN bool operator==(const QueueDepth&) const;
    QPID_BROKER_EXTERN bool operator!=(const QueueDepth&) const;
    QPID_BROKER_EXTERN bool operator<(const QueueDepth& other) const;
    QPID_BROKER_EXTERN bool operator>(const QueueDepth& other) const;
    QPID_BROKER_EXTERN operator bool() const;
    QPID_BROKER_EXTERN bool hasCount() const;
    QPID_BROKER_EXTERN uint32_t getCount() const;
    QPID_BROKER_EXTERN void setCount(uint32_t);
    QPID_BROKER_EXTERN bool hasSize() const;
    QPID_BROKER_EXTERN uint64_t getSize() const;
    QPID_BROKER_EXTERN void setSize(uint64_t);
  friend QPID_BROKER_EXTERN QueueDepth operator-(const QueueDepth&, const QueueDepth&);
  friend QPID_BROKER_EXTERN QueueDepth operator+(const QueueDepth&, const QueueDepth&);
    template <typename T> struct Optional
    {
        T value;
        bool valid;

        Optional(T v) : value(v), valid(true) {}
        Optional() : value(0), valid(false) {}
    };
  private:
    Optional<uint32_t> count;
    Optional<uint64_t> size;
};

QPID_BROKER_EXTERN QueueDepth operator-(const QueueDepth&, const QueueDepth&);
QPID_BROKER_EXTERN QueueDepth operator+(const QueueDepth&, const QueueDepth&);
std::ostream& operator<<(std::ostream&, const QueueDepth&);

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEDEPTH_H*/
