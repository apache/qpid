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
#include <FieldTable.h>
#include <QpidError.h>
#include <Buffer.h>
#include <Value.h>
#include <assert.h>

namespace qpid {
namespace framing {

FieldTable::~FieldTable() {}

u_int32_t FieldTable::size() const {
    u_int32_t len(4);
    for(ValueMap::const_iterator i = values.begin(); i != values.end(); ++i) {
        // 2 = shortstr_len_byyte + type_char_byte
	len += 2 + (i->first).size() + (i->second)->size();
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
    FieldTable::ValueMap::const_iterator i = t.getMap().begin();
    if (i != t.getMap().end()) out << *i++;
    while (i != t.getMap().end()) 
    {
        out << "," << *i++;
    }
    return out << "}";
}

void FieldTable::setString(const std::string& name, const std::string& value){
    values[name] = ValuePtr(new StringValue(value));
}

void FieldTable::setInt(const std::string& name, int value){
    values[name] = ValuePtr(new IntegerValue(value));
}

void FieldTable::setTimestamp(const std::string& name, u_int64_t value){
    values[name] = ValuePtr(new TimeValue(value));
}

void FieldTable::setTable(const std::string& name, const FieldTable& value){
    values[name] = ValuePtr(new FieldTableValue(value));
}

namespace {
template <class T> T default_value() { return T(); }
template <> int default_value<int>() { return 0; }
template <> u_int64_t default_value<u_int64_t>() { return 0; }
}

template <class T>
T FieldTable::getValue(const std::string& name) const
{
    ValueMap::const_iterator i = values.find(name);
    if (i == values.end()) return default_value<T>();
    const ValueOps<T> *vt = dynamic_cast<const ValueOps<T>*>(i->second.get());
    return vt->getValue();
}

std::string FieldTable::getString(const std::string& name) const {
    return getValue<std::string>(name);
}

int FieldTable::getInt(const std::string& name) const {
    return getValue<int>(name);
}

u_int64_t FieldTable::getTimestamp(const std::string& name) const {
    return getValue<u_int64_t>(name);
}

void FieldTable::getTable(const std::string& name, FieldTable& value) const {
    value = getValue<FieldTable>(name);
}

void FieldTable::encode(Buffer& buffer) const{
    buffer.putLong(size() - 4);
    for (ValueMap::const_iterator i = values.begin(); i!=values.end(); ++i) {
        buffer.putShortString(i->first);
        buffer.putOctet(i->second->getType());
	i->second->encode(buffer);
    }
}

void FieldTable::decode(Buffer& buffer){
    u_int32_t len = buffer.getLong();
    u_int32_t available = buffer.available();
    if (available < len)
        THROW_QPID_ERROR(FRAMING_ERROR, "Not enough data for  field table.");
    u_int32_t leftover = available - len;
    while(buffer.available() > leftover){
        std::string name;
        buffer.getShortString(name);
        std::auto_ptr<Value> value(Value::decode_value(buffer));
        values[name] = ValuePtr(value.release());
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

void FieldTable::erase(const std::string& name) 
{
    values.erase(values.find(name));
}

}
}
