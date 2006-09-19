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
#include "Value.h"
#include "Buffer.h"
#include "FieldTable.h"

void qpid::framing::StringValue::encode(Buffer& buffer){
    buffer.putLongString(value);
}
void qpid::framing::StringValue::decode(Buffer& buffer){
    buffer.getLongString(value);
}

void qpid::framing::IntegerValue::encode(Buffer& buffer){
    buffer.putLong((u_int32_t) value);
}
void qpid::framing::IntegerValue::decode(Buffer& buffer){
    value = buffer.getLong();
}

void qpid::framing::TimeValue::encode(Buffer& buffer){
    buffer.putLongLong(value);
}
void qpid::framing::TimeValue::decode(Buffer& buffer){
    value = buffer.getLongLong();
}

void qpid::framing::DecimalValue::encode(Buffer& buffer){
    buffer.putOctet(decimals);
    buffer.putLong(value);
}
void qpid::framing::DecimalValue::decode(Buffer& buffer){
    decimals = buffer.getOctet();
    value = buffer.getLong();
}

void qpid::framing::FieldTableValue::encode(Buffer& buffer){
    buffer.putFieldTable(value);
}
void qpid::framing::FieldTableValue::decode(Buffer& buffer){
    buffer.getFieldTable(value);
}
