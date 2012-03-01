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

#include "qmf/DataAddrImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/DataAddr.h"
#include <iostream>

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<DataAddr> PI;

DataAddr::DataAddr(DataAddrImpl* impl) { PI::ctor(*this, impl); }
DataAddr::DataAddr(const DataAddr& s) : qmf::Handle<DataAddrImpl>() { PI::copy(*this, s); }
DataAddr::~DataAddr() { PI::dtor(*this); }
DataAddr& DataAddr::operator=(const DataAddr& s) { return PI::assign(*this, s); }

bool DataAddr::operator==(const DataAddr& o) { return *impl == *o.impl; }
bool DataAddr::operator==(const DataAddr& o) const { return *impl == *o.impl; }
bool DataAddr::operator<(const DataAddr& o) { return *impl < *o.impl; }
bool DataAddr::operator<(const DataAddr& o) const { return *impl < *o.impl; }

DataAddr::DataAddr(const qpid::types::Variant::Map& m) { PI::ctor(*this, new DataAddrImpl(m)); }
DataAddr::DataAddr(const string& n, const string& a, uint32_t e) { PI::ctor(*this, new DataAddrImpl(n, a, e)); }
const string& DataAddr::getName() const { return impl->getName(); }
const string& DataAddr::getAgentName() const { return impl->getAgentName(); }
uint32_t DataAddr::getAgentEpoch() const { return impl->getAgentEpoch(); }
Variant::Map DataAddr::asMap() const { return impl->asMap(); }

bool DataAddrImpl::operator==(const DataAddrImpl& other) const
{
    return
        agentName == other.agentName &&
        name == other.name &&
        agentEpoch == other.agentEpoch;
}


bool DataAddrImpl::operator<(const DataAddrImpl& other) const
{
    if (agentName < other.agentName) return true;
    if (agentName > other.agentName) return false;
    if (name < other.name) return true;
    if (name > other.name) return false;
    return agentEpoch < other.agentEpoch;
}


DataAddrImpl::DataAddrImpl(const Variant::Map& map) : agentEpoch(0)
{
    Variant::Map::const_iterator iter;

    iter = map.find("_agent_name");
    if (iter != map.end())
        agentName = iter->second.asString();

    iter = map.find("_object_name");
    if (iter != map.end())
        name = iter->second.asString();

    iter = map.find("_agent_epoch");
    if (iter != map.end())
        agentEpoch = (uint32_t) iter->second.asUint64();
}


Variant::Map DataAddrImpl::asMap() const
{
    Variant::Map result;

    result["_agent_name"] = agentName;
    result["_object_name"] = name;
    if (agentEpoch > 0)
        result["_agent_epoch"] = agentEpoch;
    return result;
}


DataAddrImpl& DataAddrImplAccess::get(DataAddr& item)
{
    return *item.impl;
}


const DataAddrImpl& DataAddrImplAccess::get(const DataAddr& item)
{
    return *item.impl;
}
