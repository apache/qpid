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
#include <Value.h>
#include <Buffer.h>
#include <FieldTable.h>
#include <QpidError.h>
#include <sstream>

namespace qpid {
namespace framing {

Value::~Value() {}

void StringValue::encode(Buffer& buffer){
    buffer.putLongString(value);
}
void StringValue::decode(Buffer& buffer){
    buffer.getLongString(value);
}

void IntegerValue::encode(Buffer& buffer){
    buffer.putLong((u_int32_t) value);
}
void IntegerValue::decode(Buffer& buffer){
    value = buffer.getLong();
}

void TimeValue::encode(Buffer& buffer){
    buffer.putLongLong(value);
}
void TimeValue::decode(Buffer& buffer){
    value = buffer.getLongLong();
}

void DecimalValue::encode(Buffer& buffer){
    buffer.putOctet(value.decimals);
    buffer.putLong(value.value);
}
void DecimalValue::decode(Buffer& buffer){
    value = Decimal(buffer.getLong(), buffer.getOctet());
}

void FieldTableValue::encode(Buffer& buffer){
    buffer.putFieldTable(value);
}
void FieldTableValue::decode(Buffer& buffer){
    buffer.getFieldTable(value);
}

std::auto_ptr<Value> Value::decode_value(Buffer& buffer)
{
    std::auto_ptr<Value> value;
    u_int8_t type = buffer.getOctet();
    switch(type){
      case 'S':
        value.reset(new StringValue());
	break;
      case 'I':
        value.reset(new IntegerValue());
	break;
      case 'D':
        value.reset(new DecimalValue());
	break;
      case 'T':
        value.reset(new TimeValue());
	break;
      case 'F':
        value.reset(new FieldTableValue());
	break;

      //non-standard types, introduced in java client for JMS compliance
      case 'x':
        value.reset(new BinaryValue());
	break;
      default:
        std::stringstream out;
        out << "Unknown field table value type: " << type;
	THROW_QPID_ERROR(FRAMING_ERROR, out.str());
    }
    value->decode(buffer);
    return value;
}

EmptyValue::~EmptyValue() {}

void EmptyValue::print(std::ostream& out) const 
{
    out << "<empty field value>";
}

std::ostream& operator<<(std::ostream& out, const Value& v) {
    v.print(out);
    return out;
}

std::ostream& operator<<(std::ostream& out, const Decimal& d) 
{
    return out << "Decimal(" << d.value << "," << d.decimals << ")";
}

}}



