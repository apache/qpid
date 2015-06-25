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

namespace qpid {
namespace broker {
struct QueueSettings;

/**
 * Modifies a basic priority queue by limiting the number of messages
 * from each priority level that are dispatched before allowing
 * dispatch from the next level.
 */
class Fairshare : public PriorityQueue
{
  public:
    Fairshare(size_t levels, uint limit);
    bool getState(uint& priority, uint& count) const;
    bool setState(uint priority, uint count);
    void setLimit(size_t level, uint limit);
    bool isNull();
    static std::auto_ptr<Messages> create(const QueueSettings& settings);
    static bool getState(const Messages&, uint& priority, uint& count);
    static bool setState(Messages&, uint priority, uint count);
  private:
    std::vector<uint> limits;

    uint priority;
    uint count;

    uint currentLevel();
    uint nextLevel();
    bool limitReached();
    Priority firstLevel();
    bool nextLevel(Priority& );
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_FAIRSHARE_H*/
