#ifndef _framing_FieldValue_h
#define _framing_FieldValue_h
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

#include "qpid/Exception.h"
#include "Buffer.h"
#include "amqp_types.h"

#include "assert.h"

#include <iostream>
#include <memory>
#include <vector>

namespace qpid {
namespace framing {

/**
 * Exception that is base exception for all field table errors 
 * 
 * \ingroup clientapi
 */
class FieldValueException : public qpid::Exception {};

/**
 * Exception thrown when we can't perform requested conversion
 * 
 * \ingroup clientapi
 */
struct InvalidConversionException : public FieldValueException {
    InvalidConversionException() {}
};

/**
 * Value that can appear in an AMQP field table
 *
 * \ingroup clientapi
 */
class FieldValue {
  public:
    /*
     * Abstract type for content of different types
     */
    class Data {
      public:
        virtual ~Data() {};
        virtual uint32_t size() const = 0;        
        virtual void encode(Buffer& buffer) = 0;
        virtual void decode(Buffer& buffer) = 0;
        virtual bool operator==(const Data&) const = 0;

        virtual bool convertsToInt() const { return false; }
        virtual bool convertsToString() const { return false; }
        virtual int64_t getInt() const { throw InvalidConversionException();}
        virtual std::string getString() const { throw InvalidConversionException(); }

        virtual void print(std::ostream& out) const = 0;
    };

    FieldValue(): data(0) {};
    // Default assignment operator is fine
    void setType(uint8_t type);
    uint8_t getType();
    Data& getData() { return *data; }
    uint32_t size() const { return 1 + data->size(); };
    bool empty() const { return data.get() == 0; } 
    void encode(Buffer& buffer);
    void decode(Buffer& buffer);
    bool operator==(const FieldValue&) const;
    bool operator!=(const FieldValue& v) const { return !(*this == v); }
    void print(std::ostream& out) const { out << "(0x" << std::hex << int(typeOctet) << ")"; data->print(out); }
    
    template <typename T> bool convertsTo() const { return false; }
    template <typename T> T get() const { throw InvalidConversionException(); }

  protected:
    FieldValue(uint8_t t, Data* d): typeOctet(t), data(d) {}

  private:
    uint8_t typeOctet;
    std::auto_ptr<Data> data; 
};

template <>
inline bool FieldValue::convertsTo<int>() const { return data->convertsToInt(); }

template <>
inline bool FieldValue::convertsTo<std::string>() const { return data->convertsToString(); }

template <>
inline int FieldValue::get<int>() const { return data->getInt(); }

template <>
inline std::string FieldValue::get<std::string>() const { return data->getString(); }

inline std::ostream& operator<<(std::ostream& out, const FieldValue& v) {
    v.print(out);
    return out;
}

template <int width>
class FixedWidthValue : public FieldValue::Data {
    uint8_t octets[width];
    
  public:
    FixedWidthValue() {}
    FixedWidthValue(const uint8_t (&data)[width]) : octets(data) {}
    FixedWidthValue(uint64_t v)
    {
        for (int i = width; i > 0; --i) {
            octets[i-1] = (uint8_t) (0xFF & v); v >>= 8;
        }
        octets[0] = (uint8_t) (0xFF & v);
    }

    uint32_t size() const { return width; }
    void encode(Buffer& buffer) { buffer.putRawData(octets, width); }
    void decode(Buffer& buffer) { buffer.getRawData(octets, width); }
    bool operator==(const Data& d) const {
        const FixedWidthValue<width>* rhs = dynamic_cast< const FixedWidthValue<width>* >(&d);
        if (rhs == 0) return false;
        else return std::equal(&octets[0], &octets[width], &rhs->octets[0]); 
    }

    bool convertsToInt() const { return true; }
    int64_t getInt() const
    {
        int64_t v = 0;
        for (int i = 0; i < width-1; ++i) {
            v |= octets[i]; v <<= 8;
        }
        v |= octets[width-1];
        return v;
    }

    void print(std::ostream& o) const { o << "F" << width << ":"; };
};

template <>
class FixedWidthValue<0> : public FieldValue::Data {
  public:
    // Implicit default constructor is fine
    uint32_t size() const { return 0; }
    void encode(Buffer&) {};
    void decode(Buffer&) {};
    bool operator==(const Data& d) const {
        const FixedWidthValue<0>* rhs = dynamic_cast< const FixedWidthValue<0>* >(&d);
        return rhs != 0;
    }
    void print(std::ostream& o) const { o << "F0"; };
};

template <int lenwidth>
class VariableWidthValue : public FieldValue::Data {
    std::vector<uint8_t> octets;

  public:
    VariableWidthValue() {}
    VariableWidthValue(const std::vector<uint8_t>& data) : octets(data) {}
    VariableWidthValue(const uint8_t* start, const uint8_t* end) : octets(start, end) {}
    uint32_t size() const { return lenwidth + octets.size(); } 
    void encode(Buffer& buffer) {
        buffer.putUInt<lenwidth>(octets.size());
        buffer.putRawData(&octets[0], octets.size());
    };
    void decode(Buffer& buffer) {
        uint32_t len = buffer.getUInt<lenwidth>();
        octets.resize(len);
        buffer.getRawData(&octets[0], len);
    }
    bool operator==(const Data& d) const {
        const VariableWidthValue<lenwidth>* rhs = dynamic_cast< const VariableWidthValue<lenwidth>* >(&d);
        if (rhs == 0) return false;
        else return octets==rhs->octets; 
    }
    
    bool convertsToString() const { return true; }
    std::string getString() const { return std::string(octets.begin(), octets.end()); }

    void print(std::ostream& o) const { o << "V" << lenwidth << ":" << octets.size() << ":"; };
};

/*
 * Basic string value encodes as iso-8859-15 with 32 bit length
 */ 
class StringValue : public FieldValue {
  public:
    StringValue(const std::string& v);
};

class Str16Value : public FieldValue {
  public:
    Str16Value(const std::string& v);
};

/*
 * Basic integer value encodes as signed 32 bit
 */
class IntegerValue : public FieldValue {
  public:
    IntegerValue(int v);
};

class TimeValue : public FieldValue {
  public:
    TimeValue(uint64_t v);
};

class FieldTableValue : public FieldValue {
  public:
    FieldTableValue(const FieldTable&);
};

}} // qpid::framing

#endif
