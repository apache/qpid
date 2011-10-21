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
#include "qpid/broker/QueuedMessage.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>

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

bool Fairshare::findFrontLevel(uint& p, PriorityLevels& messages)
{
    const uint start = p = currentLevel();
    do {
        if (!messages[p].empty()) return true;
    } while ((p = nextLevel()) != start);
    return false;
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

int getIntegerSetting(const qpid::framing::FieldTable& settings, const std::string& key)
{
    qpid::framing::FieldTable::ValuePtr v = settings.get(key);
    if (!v) {
        return 0;
    } else if (v->convertsTo<int>()) {
        return v->get<int>();
    } else if (v->convertsTo<std::string>()){
        std::string s = v->get<std::string>();
        try { 
            return boost::lexical_cast<int>(s); 
        } catch(const boost::bad_lexical_cast&) {
            QPID_LOG(warning, "Ignoring invalid integer value for " << key << ": " << s);
            return 0;
        }
    } else {
        QPID_LOG(warning, "Ignoring invalid integer value for " << key << ": " << *v);
        return 0;
    }
}

int getSetting(const qpid::framing::FieldTable& settings, const std::string& key, int minvalue, int maxvalue)
{
    return std::max(minvalue,std::min(getIntegerSetting(settings, key), maxvalue));
}

std::auto_ptr<Messages> Fairshare::create(const qpid::framing::FieldTable& settings)
{
    std::auto_ptr<Messages> result;
    size_t levels = getSetting(settings, "x-qpid-priorities", 1, 100);
    if (levels) {
        uint defaultLimit = getIntegerSetting(settings, "x-qpid-fairshare");
        std::auto_ptr<Fairshare> fairshare(new Fairshare(levels, defaultLimit));
        for (uint i = 0; i < levels; i++) {
            std::string key = (boost::format("x-qpid-fairshare-%1%") % i).str();
            if(settings.isSet(key)) {
                fairshare->setLimit(i, getIntegerSetting(settings, key));
            }
        }
        
        if (fairshare->isNull()) {
            result = std::auto_ptr<Messages>(new PriorityQueue(levels));
        } else {
            result = fairshare;
        }
    }
    return result;
}

}} // namespace qpid::broker
