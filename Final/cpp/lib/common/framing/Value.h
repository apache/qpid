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
#include <iostream>
#include <vector>
#include <amqp_types.h>
#include <FieldTable.h>

#ifndef _Value_
#define _Value_

namespace qpid {
namespace framing {

class Buffer;

/**
 * Represents a decimal value.
 * No arithmetic functionality for now, we only care about encoding/decoding.
 */
struct Decimal {
    u_int32_t value;
    u_int8_t decimals;

    Decimal(u_int32_t value_=0, u_int8_t decimals_=0) : value(value_), decimals(decimals_) {}
    bool operator==(const Decimal& d) const {
        return decimals == d.decimals && value == d.value;
    }
    bool operator!=(const Decimal& d) const { return !(*this == d); }
};

std::ostream& operator<<(std::ostream& out, const Decimal& d);

/**
 * Polymorpic base class for values.
 */
class Value {
  public:
    virtual ~Value();
    virtual u_int32_t size() const = 0;
    virtual char getType() const = 0;
    virtual void encode(Buffer& buffer) = 0;
    virtual void decode(Buffer& buffer) = 0;
    virtual bool operator==(const Value&) const = 0;
    bool operator!=(const Value& v) const { return !(*this == v); }
    virtual void print(std::ostream& out) const = 0;

    /** Create a new value by decoding from the buffer */
    static std::auto_ptr<Value> decode_value(Buffer& buffer);
};

std::ostream& operator<<(std::ostream& out, const Value& d);


/**
 * Template for common operations on Value sub-classes.
 */
template <class T>
class ValueOps : public Value
{
  protected:
    T value;
  public:
    ValueOps() {}
    ValueOps(const T& v) : value(v) {}
    const T& getValue() const { return value; }
    T& getValue() { return value; }

    virtual bool operator==(const Value& v) const {
        const ValueOps<T>* vo = dynamic_cast<const ValueOps<T>*>(&v);
        if (vo == 0) return false;
        else return value == vo->value;
    }

    void print(std::ostream& out) const { out << value; }
};


class StringValue : public ValueOps<std::string> {
  public:
    StringValue(const std::string& v) : ValueOps<std::string>(v) {}
    StringValue() {}
    virtual u_int32_t size() const { return 4 + value.length(); }
    virtual char getType() const { return 'S'; }
    virtual void encode(Buffer& buffer);
    virtual void decode(Buffer& buffer);
};

class IntegerValue : public ValueOps<int> {
  public:
    IntegerValue(int v) : ValueOps<int>(v) {}
    IntegerValue(){}
    virtual u_int32_t size() const { return 4; }
    virtual char getType() const { return 'I'; }
    virtual void encode(Buffer& buffer);
    virtual void decode(Buffer& buffer);
};

class TimeValue : public ValueOps<u_int64_t> {
  public:
    TimeValue(u_int64_t v) : ValueOps<u_int64_t>(v){}
    TimeValue(){}
    virtual u_int32_t size() const { return 8; }
    virtual char getType() const { return 'T'; }
    virtual void encode(Buffer& buffer);
    virtual void decode(Buffer& buffer);
};

class DecimalValue : public ValueOps<Decimal> {
  public:
    DecimalValue(const Decimal& d) : ValueOps<Decimal>(d) {} 
    DecimalValue(u_int32_t value_=0, u_int8_t decimals_=0) :
        ValueOps<Decimal>(Decimal(value_, decimals_)){}
    virtual u_int32_t size() const { return 5; }
    virtual char getType() const { return 'D'; }
    virtual void encode(Buffer& buffer);
    virtual void decode(Buffer& buffer);
};


class FieldTableValue : public ValueOps<FieldTable> {
  public:
    FieldTableValue(const FieldTable& v) : ValueOps<FieldTable>(v){}
    FieldTableValue(){}
    virtual u_int32_t size() const { return 4 + value.size(); }
    virtual char getType() const { return 'F'; }
    virtual void encode(Buffer& buffer);
    virtual void decode(Buffer& buffer);
};

class EmptyValue : public Value {
  public:
    ~EmptyValue();
    virtual u_int32_t size() const { return 0; }
    virtual char getType() const { return 0; }
    virtual void encode(Buffer& ) {}
    virtual void decode(Buffer& ) {}
    virtual bool operator==(const Value& v) const {
        return dynamic_cast<const EmptyValue*>(&v);
    }
    virtual void print(std::ostream& out) const;
};

//non-standard types, introduced in java client for JMS compliance
class BinaryValue : public StringValue {
  public:
    BinaryValue(const std::string& v) : StringValue(v) {}
    BinaryValue() {}
    virtual char getType() const { return 'x'; }
};

}} // qpid::framing

#endif
