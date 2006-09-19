/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "NamedValue.h"
#include "QpidError.h"
#include "Buffer.h"
#include "FieldTable.h"

qpid::framing::NamedValue::NamedValue() : value(0){}

qpid::framing::NamedValue::NamedValue(const string& n, Value* v) : name(n), value(v){}

qpid::framing::NamedValue::~NamedValue(){
    if(value != 0){
	delete value;
    }
}

u_int32_t qpid::framing::NamedValue::size() const{
    return value ? 1/*size of name*/ + name.length() + 1/*type char*/ + value->size() : 0;
}

void qpid::framing::NamedValue::encode(Buffer& buffer){
    buffer.putShortString(name);
    u_int8_t type = value->getType();
    buffer.putOctet(type);
    value->encode(buffer);
}

void qpid::framing::NamedValue::decode(Buffer& buffer){
    buffer.getShortString(name);
    u_int8_t type = buffer.getOctet();
    switch(type){
    case 'S':
	value = new StringValue();
	break;
    case 'I':
	value = new IntegerValue();
	break;
    case 'D':
	value = new DecimalValue();
	break;
    case 'T':
	value = new TimeValue();
	break;
    case 'F':
	value = new FieldTableValue();
	break;
    default:
	THROW_QPID_ERROR(FRAMING_ERROR, "Unknown field table value type");
    }
    value->decode(buffer);
}
