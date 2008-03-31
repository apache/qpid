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
#include "Array.h"
#include "Buffer.h"
#include "FieldValue.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include <assert.h>

namespace qpid {
namespace framing {

Array::Array() : typeOctet(0xF0/*void*/) {}

Array::Array(uint8_t type) : typeOctet(type) {}

Array::Array(const std::vector<std::string>& in)
{
    typeOctet = 0xA4;
    for (std::vector<std::string>::const_iterator i = in.begin(); i != in.end(); ++i) {
        ValuePtr value(new StringValue(*i));
        values.push_back(value);
    }
} 


uint32_t Array::size() const {
    //note: size is only included when used as a 'top level' type
    uint32_t len(4/*size*/ + 1/*type*/ + 4/*count*/);
    for(ValueVector::const_iterator i = values.begin(); i != values.end(); ++i) {
	len += (*i)->getData().size();
    }
    return len;
}

int Array::count() const {
    return values.size();
}

std::ostream& operator<<(std::ostream& out, const Array& t) {
    out << "{";
    for(Array::ValueVector::const_iterator i = t.values.begin(); i != t.values.end(); ++i) {
        if (i != t.values.begin()) out << ", ";
        out << *(i->get());
    }
    return out << "}";
}

void Array::encode(Buffer& buffer) const{
    buffer.putLong(size() - 4);//size added only when array is a top-level type
    buffer.putOctet(typeOctet);
    buffer.putLong(count());
    for (ValueVector::const_iterator i = values.begin(); i!=values.end(); ++i) {
    	(*i)->getData().encode(buffer);
    }
}

void Array::decode(Buffer& buffer){
    uint32_t size = buffer.getLong();//size added only when array is a top-level type
    uint32_t available = buffer.available();
    if (available < size) {
        throw SyntaxErrorException(QPID_MSG("Not enough data for array, expected " 
                                            << size << " bytes but only " << available << " available"));
    }
    typeOctet = buffer.getOctet();
    uint32_t count = buffer.getLong();

    FieldValue dummy;
    dummy.setType(typeOctet);
    available = buffer.available();
    if (available < count * dummy.getData().size()) {
        throw SyntaxErrorException(QPID_MSG("Not enough data for array, expected " 
                                            << count << " items of " << dummy.getData().size()
                                            << " bytes each  but only " << available << " bytes available"));
    }

    for (uint32_t i = 0; i < count; i++) {
        ValuePtr value(new FieldValue);
        value->setType(typeOctet);
        value->getData().decode(buffer);
        values.push_back(ValuePtr(value));
    }    
}


bool Array::operator==(const Array& x) const {
    if (typeOctet != x.typeOctet) return false;
    if (values.size() != x.values.size()) return false;

    for (ValueVector::const_iterator i =  values.begin(), j = x.values.begin(); i != values.end(); ++i, ++j) {
        if (*(i->get()) != *(j->get())) return false;
    }

    return true;
}

void Array::add(ValuePtr value)
{
    if (typeOctet != value->getType()) {
        throw SyntaxErrorException(QPID_MSG("Wrong type of value, expected " << typeOctet));
    }
    values.push_back(value);
}


}
}
