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
#include "FieldTable.h"
#include "NamedValue.h"
#include "QpidError.h"
#include "Buffer.h"
#include "Value.h"

qpid::framing::FieldTable::~FieldTable(){
    int count(values.size());
    for(int i = 0; i < count; i++){
	delete values[i];
    }
}

u_int32_t qpid::framing::FieldTable::size() const {
    u_int32_t size(4);
    int count(values.size());
    for(int i = 0; i < count; i++){
	size += values[i]->size();
    }
    return size;
}

int qpid::framing::FieldTable::count() const {
    return values.size();
}

std::ostream& qpid::framing::operator<<(std::ostream& out, const FieldTable& t){
    out << "field_table{}";
    return out;
}

void qpid::framing::FieldTable::setString(const std::string& name, const std::string& value){
    setValue(name, new StringValue(value));
}

void qpid::framing::FieldTable::setInt(const std::string& name, int value){
    setValue(name, new IntegerValue(value));
}

void qpid::framing::FieldTable::setTimestamp(const std::string& name, u_int64_t value){
    setValue(name, new TimeValue(value));
}

void qpid::framing::FieldTable::setTable(const std::string& name, const FieldTable& value){
    setValue(name, new FieldTableValue(value));
}

std::string qpid::framing::FieldTable::getString(const std::string& name){
    StringValue* val = dynamic_cast<StringValue*>(getValue(name));
    return (val == 0 ? "" : val->getValue());
}

int qpid::framing::FieldTable::getInt(const std::string& name){
    IntegerValue* val = dynamic_cast<IntegerValue*>(getValue(name));
    return (val == 0 ? 0 : val->getValue());
}

u_int64_t qpid::framing::FieldTable::getTimestamp(const std::string& name){
    TimeValue* val = dynamic_cast<TimeValue*>(getValue(name));
    return (val == 0 ? 0 : val->getValue());
}

void qpid::framing::FieldTable::getTable(const std::string& name, FieldTable& value){
    FieldTableValue* val = dynamic_cast<FieldTableValue*>(getValue(name));
    if(val != 0) value = val->getValue();
}

qpid::framing::NamedValue* qpid::framing::FieldTable::find(const std::string& name) const{
    int count(values.size());
    for(int i = 0; i < count; i++){
	if(values[i]->getName() == name) return values[i];
    }
    return 0;
}

qpid::framing::Value* qpid::framing::FieldTable::getValue(const std::string& name) const{
    NamedValue* val = find(name);
    return val == 0 ? 0 : val->getValue();
}

void qpid::framing::FieldTable::setValue(const std::string& name, Value* value){
    NamedValue* val = find(name);
    if(val == 0){
	val = new NamedValue(name, value);
	values.push_back(val);
    }else{
	Value* old = val->getValue();
	if(old != 0) delete old;
	val->setValue(value);
    }
}

void qpid::framing::FieldTable::encode(Buffer& buffer) const{
    buffer.putLong(size() - 4);
    int count(values.size());
    for(int i = 0; i < count; i++){
	values[i]->encode(buffer);
    }
}

void qpid::framing::FieldTable::decode(Buffer& buffer){
    u_int32_t size = buffer.getLong();
    int leftover = buffer.available() - size;
    
    while(buffer.available() > leftover){
	NamedValue* value = new NamedValue();
	value->decode(buffer);
	values.push_back(value);
    }    
}
