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
#include "FieldTable.h"
#include "Buffer.h"
#include "FieldValue.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include <assert.h>

namespace qpid {
namespace framing {

FieldTable::~FieldTable() {}

uint32_t FieldTable::size() const {
    uint32_t len(4/*size field*/ + 4/*count field*/);
    for(ValueMap::const_iterator i = values.begin(); i != values.end(); ++i) {
        // shortstr_len_byte + key size + value size
	len += 1 + (i->first).size() + (i->second)->size();
    }
    return len;
}

int FieldTable::count() const {
    return values.size();
}

namespace 
{
std::ostream& operator<<(std::ostream& out, const FieldTable::ValueMap::value_type& i) {
    return out << i.first << ":" << *i.second;
}
}

std::ostream& operator<<(std::ostream& out, const FieldTable& t) {
    out << "{";
    FieldTable::ValueMap::const_iterator i = t.begin();
    if (i != t.end()) out << *i++;
    while (i != t.end()) 
    {
        out << "," << *i++;
    }
    return out << "}";
}

void FieldTable::set(const std::string& name, const ValuePtr& value){
    values[name] = value;
}

void FieldTable::setString(const std::string& name, const std::string& value){
    values[name] = ValuePtr(new Str16Value(value));
}

void FieldTable::setInt(const std::string& name, int value){
    values[name] = ValuePtr(new IntegerValue(value));
}

void FieldTable::setTimestamp(const std::string& name, uint64_t value){
    values[name] = ValuePtr(new TimeValue(value));
}

void FieldTable::setTable(const std::string& name, const FieldTable& value){
    values[name] = ValuePtr(new FieldTableValue(value));
}

FieldTable::ValuePtr FieldTable::get(const std::string& name) const
{
    ValuePtr value;
    ValueMap::const_iterator i = values.find(name);
    if ( i!=values.end() )
        value = i->second;
    return value;
}

namespace {
    template <class T> T default_value() { return T(); }
    template <> int default_value<int>() { return 0; }
    template <> uint64_t default_value<uint64_t>() { return 0; }
}

template <class T>
T getValue(const FieldTable::ValuePtr value)
{
    if (!value || !value->convertsTo<T>())
        return default_value<T>();

    return value->get<T>();
}

std::string FieldTable::getString(const std::string& name) const {
    return getValue<std::string>(get(name));
}

int FieldTable::getInt(const std::string& name) const {
    return getValue<int>(get(name));
}

//uint64_t FieldTable::getTimestamp(const std::string& name) const {
//    return getValue<uint64_t>(name);
//}
//
//void FieldTable::getTable(const std::string& name, FieldTable& value) const {
//    value = getValue<FieldTable>(name);
//}

void FieldTable::encode(Buffer& buffer) const{    
    buffer.putLong(size() - 4);
    buffer.putLong(values.size());
    for (ValueMap::const_iterator i = values.begin(); i!=values.end(); ++i) {
        buffer.putShortString(i->first);
    	i->second->encode(buffer);
    }
}

void FieldTable::decode(Buffer& buffer){
    uint32_t len = buffer.getLong();
    if (len) {
        uint32_t available = buffer.available();
        if (available < len)
            throw IllegalArgumentException(QPID_MSG("Not enough data for  field table."));
        uint32_t count = buffer.getLong();
        uint32_t leftover = available - len;
        while(buffer.available() > leftover && count--){
            std::string name;
            ValuePtr value(new FieldValue);
            
            buffer.getShortString(name);
            value->decode(buffer);
            values[name] = ValuePtr(value);
        }    
    }
}


bool FieldTable::operator==(const FieldTable& x) const {
    if (values.size() != x.values.size()) return false;
    for (ValueMap::const_iterator i =  values.begin(); i != values.end(); ++i) {
        ValueMap::const_iterator j = x.values.find(i->first);
        if (j == x.values.end()) return false;
        if (*(i->second) != *(j->second)) return false;
    }
    return true;
}

//void FieldTable::erase(const std::string& name) 
//{
//    values.erase(values.find(name));
//}

}
}
