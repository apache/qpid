#ifndef QPID_BROKER_FAIRSHARE_H
#define QPID_BROKER_FAIRSHARE_H

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
#include "qpid/broker/PriorityQueue.h"
#include <vector>

namespace qpid {
namespace framing {
class FieldTable;
}
namespace broker {

/**
 * Modifies a basic prioirty queue by limiting the number of messages
 * from each priority level that are dispatched before allowing
 * dispatch from the next level.
 */
class Fairshare : public PriorityQueue
{
  public:
    Fairshare(size_t levels, uint limit);
    bool getState(qpid::framing::FieldTable& counts) const;
    bool setState(const qpid::framing::FieldTable& counts);
    void setLimit(size_t level, uint limit);
    bool isNull();
    bool consume(QueuedMessage&);
    static std::auto_ptr<Messages> create(const qpid::framing::FieldTable& settings);
    static bool getState(const Messages&, qpid::framing::FieldTable& counts);
    static bool setState(Messages&, const qpid::framing::FieldTable& counts);
  private:
    std::vector<uint> limits;
    std::vector<uint> counts;

    bool checkLevel(uint level);
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_FAIRSHARE_H*/
