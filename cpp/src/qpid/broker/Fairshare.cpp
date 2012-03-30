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
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include <algorithm>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/assign/list_of.hpp>

namespace qpid {
namespace broker {

Fairshare::Fairshare(size_t levels, uint limit) :
    PriorityQueue(levels),
    limits(levels, limit), counts(levels, 0) {}


void Fairshare::setLimit(size_t level, uint limit)
{
    limits[level] = limit;
}

bool Fairshare::isNull()
{
    for (int i = 0; i < levels; i++) if (limits[i]) return false;
    return true;
}

bool Fairshare::getState(qpid::framing::FieldTable& state) const
{
    for (int i = 0; i < levels; i++) {
        if (counts[i]) {
            std::string key = (boost::format("fairshare-count-%1%") % i).str();
            state.setInt(key, counts[i]);
        }
    }
    return true;
}

bool Fairshare::checkLevel(uint level)
{
    if (!limits[level] || counts[level] < limits[level]) {
        counts[level]++;
        return true;
    } else {
        return false;
    }
}

bool Fairshare::consume(QueuedMessage& message)
{
    for (Available::iterator i = available.begin(); i != available.end(); ++i) {
        QueuedMessage* next = *i;
        if (checkLevel(getPriorityLevel(*next))) {
            messages[next->position].status = QueuedMessage::ACQUIRED;
            message = *next;
            available.erase(i);
            return true;
        }
    }
    if (!available.empty()) {
        std::fill(counts.begin(), counts.end(), 0);//reset counts
        return consume(message);
    } else {
        return false;
    }
}


bool Fairshare::getState(const Messages& m, qpid::framing::FieldTable& counts)
{
    const Fairshare* fairshare = dynamic_cast<const Fairshare*>(&m);
    return fairshare && fairshare->getState(counts);
}

bool Fairshare::setState(Messages& m, const qpid::framing::FieldTable& counts)
{
    Fairshare* fairshare = dynamic_cast<Fairshare*>(&m);
    return fairshare && fairshare->setState(counts);
}

int getIntegerSetting(const qpid::framing::FieldTable& settings, const std::vector<std::string>& keys)
{
    qpid::framing::FieldTable::ValuePtr v;
    std::vector<std::string>::const_iterator i = keys.begin(); 
    while (!v && i != keys.end()) {
        v = settings.get(*i++);
    }

    if (!v) {
        return 0;
    } else if (v->convertsTo<int>()) {
        return v->get<int>();
    } else if (v->convertsTo<std::string>()){
        std::string s = v->get<std::string>();
        try {
            return boost::lexical_cast<int>(s);
        } catch(const boost::bad_lexical_cast&) {
            QPID_LOG(warning, "Ignoring invalid integer value for " << *i << ": " << s);
            return 0;
        }
    } else {
        QPID_LOG(warning, "Ignoring invalid integer value for " << *i << ": " << *v);
        return 0;
    }
}

int getIntegerSettingForKey(const qpid::framing::FieldTable& settings, const std::string& key)
{
    return getIntegerSetting(settings, boost::assign::list_of<std::string>(key));
}
bool Fairshare::setState(const qpid::framing::FieldTable& state)
{
    for (int i = 0; i < levels; i++) {
        std::string key = (boost::format("fairshare-count-%1%") % i).str();
        counts[i] = state.isSet(key) ? getIntegerSettingForKey(state, key) : 0;
    }
    return true;
}
int getSetting(const qpid::framing::FieldTable& settings, const std::vector<std::string>& keys, int minvalue, int maxvalue)
{
    return std::max(minvalue,std::min(getIntegerSetting(settings, keys), maxvalue));
}

std::auto_ptr<Fairshare> getFairshareForKey(const qpid::framing::FieldTable& settings, uint levels, const std::string& key)
{
    uint defaultLimit = getIntegerSettingForKey(settings, key);
    std::auto_ptr<Fairshare> fairshare(new Fairshare(levels, defaultLimit));
    for (uint i = 0; i < levels; i++) {
        std::string levelKey = (boost::format("%1%-%2%") % key % i).str();
        if(settings.isSet(levelKey)) {
            fairshare->setLimit(i, getIntegerSettingForKey(settings, levelKey));
        }
    }
    if (!fairshare->isNull()) {
        return fairshare;
    } else {
        return std::auto_ptr<Fairshare>();
    }
}

std::auto_ptr<Fairshare> getFairshare(const qpid::framing::FieldTable& settings,
                                      uint levels,
                                      const std::vector<std::string>& keys)
{
    std::auto_ptr<Fairshare> fairshare;
    for (std::vector<std::string>::const_iterator i = keys.begin(); i != keys.end() && !fairshare.get(); ++i) {
        fairshare = getFairshareForKey(settings, levels, *i);
    }
    return fairshare;
}

std::auto_ptr<Messages> Fairshare::create(const qpid::framing::FieldTable& settings)
{
    using boost::assign::list_of;
    std::auto_ptr<Messages> result;
    size_t levels = getSetting(settings, list_of<std::string>("qpid.priorities")("x-qpid-priorities"), 0, 100);
    if (levels) {
        std::auto_ptr<Fairshare> fairshare =
            getFairshare(settings, levels, list_of<std::string>("qpid.fairshare")("x-qpid-fairshare"));
        if (fairshare.get()) result = fairshare;
        else result = std::auto_ptr<Messages>(new PriorityQueue(levels));
    }
    return result;
}

}} // namespace qpid::broker
