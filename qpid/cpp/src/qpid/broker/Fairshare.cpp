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
#include "qpid/broker/Fairshare.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/assign/list_of.hpp>

namespace qpid {
namespace broker {

Fairshare::Fairshare(size_t levels, uint limit) :
    PriorityQueue(levels),
    limits(levels, limit), priority(levels-1), count(0) {}


void Fairshare::setLimit(size_t level, uint limit)
{
    limits[level] = limit;
}

bool Fairshare::limitReached()
{
    uint l = limits[priority];
    return l && ++count > l;
}

uint Fairshare::currentLevel()
{
    if (limitReached()) {
        return nextLevel();
    } else {
        return priority;
    }
}

uint Fairshare::nextLevel()
{
    count = 1;
    if (priority) --priority;
    else priority = levels-1;
    return priority;
}

bool Fairshare::isNull()
{
    for (int i = 0; i < levels; i++) if (limits[i]) return false;
    return true;
}

bool Fairshare::getState(uint& p, uint& c) const
{
    p = priority;
    c = count;
    return true;
}

bool Fairshare::setState(uint p, uint c)
{
    priority = p;
    count = c;
    return true;
}

bool Fairshare::getState(const Messages& m, uint& priority, uint& count)
{
    const Fairshare* fairshare = dynamic_cast<const Fairshare*>(&m);
    return fairshare && fairshare->getState(priority, count);
}

bool Fairshare::setState(Messages& m, uint priority, uint count)
{
    Fairshare* fairshare = dynamic_cast<Fairshare*>(&m);
    return fairshare && fairshare->setState(priority, count);
}

PriorityQueue::Priority Fairshare::firstLevel()
{
    return Priority(currentLevel());
}

bool Fairshare::nextLevel(Priority& p)
{
    int next = nextLevel();
    if (next == p.start) {
        return false;
    } else {
        p.current = next;
        return true;
    }
}

std::auto_ptr<Messages> Fairshare::create(const QueueSettings& settings)
{
    std::auto_ptr<Fairshare> fairshare(new Fairshare(settings.priorities, settings.defaultFairshare));
    for (uint i = 0; i < settings.priorities; i++) {
        std::map<uint32_t,uint32_t>::const_iterator l = settings.fairshare.find(i);
        if (l != settings.fairshare.end()) fairshare->setLimit(i, l->second);
    }
    return std::auto_ptr<Messages>(fairshare.release());
}

}} // namespace qpid::broker
