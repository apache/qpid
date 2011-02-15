#ifndef QPID_BROKER_THRESHOLDALERTS_H
#define QPID_BROKER_THRESHOLDALERTS_H

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
#include "qpid/sys/Time.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qpid {
namespace framing {
class FieldTable;
}
namespace management {
class ManagementAgent;
}
namespace broker {

class Queue;
/**
 * Class to manage generation of QMF alerts when particular thresholds
 * are breached on a queue.
 */
class ThresholdAlerts : public QueueObserver
{
  public:
    ThresholdAlerts(const std::string& name,
                    qpid::management::ManagementAgent& agent,
                    const uint32_t countThreshold,
                    const uint64_t sizeThreshold,
                    const long repeatInterval);
    void enqueued(const QueuedMessage&);
    void dequeued(const QueuedMessage&);
    static void observe(Queue& queue, qpid::management::ManagementAgent& agent,
                        const uint64_t countThreshold,
                        const uint64_t sizeThreshold,
                        const long repeatInterval);
    static void observe(Queue& queue, qpid::management::ManagementAgent& agent,
                        const qpid::framing::FieldTable& settings);
    static void observe(Queue& queue, qpid::management::ManagementAgent& agent,
                        const qpid::types::Variant::Map& settings);
  private:
    const std::string name;
    qpid::management::ManagementAgent& agent;
    const uint32_t countThreshold;
    const uint64_t sizeThreshold;
    const qpid::sys::Duration repeatInterval;
    uint64_t count;
    uint64_t size;
    qpid::sys::AbsTime lastAlert;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_THRESHOLDALERTS_H*/
