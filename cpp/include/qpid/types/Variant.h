#ifndef QPID_TYPES_VARIANT_H
#define QPID_TYPES_VARIANT_H

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
#include <list>
#include <map>
#include <ostream>
#include <string>
#include "Uuid.h"
#include "qpid/Exception.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/CommonImportExport.h"

namespace qpid {
namespace types {

/**
 * Thrown when an illegal conversion of a variant is attempted.
 */
struct InvalidConversion : public qpid::Exception 
{
    InvalidConversion(const std::string& msg);
};

enum VariantType {
    VAR_VOID = 0,
    VAR_BOOL,
    VAR_UINT8,
    VAR_UINT16,
    VAR_UINT32,
    VAR_UINT64,
    VAR_INT8,
    VAR_INT16,
    VAR_INT32,
    VAR_INT64,
    VAR_FLOAT,
    VAR_DOUBLE,
    VAR_STRING,
    VAR_MAP,
    VAR_LIST,
    VAR_UUID
};

class VariantImpl;

/**
 * Represents a value of variable type.
 */
class Variant
{
  public:
    typedef std::map<std::string, Variant> Map;
    typedef std::list<Variant> List;

    QPID_COMMON_EXTERN Variant();
    QPID_COMMON_EXTERN Variant(bool);
    QPID_COMMON_EXTERN Variant(uint8_t);
    QPID_COMMON_EXTERN Variant(uint16_t);
    QPID_COMMON_EXTERN Variant(uint32_t);
    QPID_COMMON_EXTERN Variant(uint64_t);
    QPID_COMMON_EXTERN Variant(int8_t);
    QPID_COMMON_EXTERN Variant(int16_t);
    QPID_COMMON_EXTERN Variant(int32_t);
    QPID_COMMON_EXTERN Variant(int64_t);
    QPID_COMMON_EXTERN Variant(float);
    QPID_COMMON_EXTERN Variant(double);
    QPID_COMMON_EXTERN Variant(const std::string&);
    QPID_COMMON_EXTERN Variant(const char*);
    QPID_COMMON_EXTERN Variant(const Map&);
    QPID_COMMON_EXTERN Variant(const List&);
    QPID_COMMON_EXTERN Variant(const Variant&);
    QPID_COMMON_EXTERN Variant(const Uuid&);

    QPID_COMMON_EXTERN ~Variant();

    QPID_COMMON_EXTERN VariantType getType() const;
    QPID_COMMON_EXTERN bool isVoid() const;
    
    QPID_COMMON_EXTERN Variant& operator=(bool);
    QPID_COMMON_EXTERN Variant& operator=(uint8_t);
    QPID_COMMON_EXTERN Variant& operator=(uint16_t);
    QPID_COMMON_EXTERN Variant& operator=(uint32_t);
    QPID_COMMON_EXTERN Variant& operator=(uint64_t);
    QPID_COMMON_EXTERN Variant& operator=(int8_t);
    QPID_COMMON_EXTERN Variant& operator=(int16_t);
    QPID_COMMON_EXTERN Variant& operator=(int32_t);
    QPID_COMMON_EXTERN Variant& operator=(int64_t);
    QPID_COMMON_EXTERN Variant& operator=(float);
    QPID_COMMON_EXTERN Variant& operator=(double);
    QPID_COMMON_EXTERN Variant& operator=(const std::string&);
    QPID_COMMON_EXTERN Variant& operator=(const char*);
    QPID_COMMON_EXTERN Variant& operator=(const Map&);
    QPID_COMMON_EXTERN Variant& operator=(const List&);
    QPID_COMMON_EXTERN Variant& operator=(const Variant&);
    QPID_COMMON_EXTERN Variant& operator=(const Uuid&);

    QPID_COMMON_EXTERN bool asBool() const;
    QPID_COMMON_EXTERN uint8_t asUint8() const;
    QPID_COMMON_EXTERN uint16_t asUint16() const;
    QPID_COMMON_EXTERN uint32_t asUint32() const;
    QPID_COMMON_EXTERN uint64_t asUint64() const;
    QPID_COMMON_EXTERN int8_t asInt8() const;
    QPID_COMMON_EXTERN int16_t asInt16() const;
    QPID_COMMON_EXTERN int32_t asInt32() const;
    QPID_COMMON_EXTERN int64_t asInt64() const;
    QPID_COMMON_EXTERN float asFloat() const;
    QPID_COMMON_EXTERN double asDouble() const;
    QPID_COMMON_EXTERN std::string asString() const;
    QPID_COMMON_EXTERN Uuid asUuid() const;

    QPID_COMMON_EXTERN operator bool() const;
    QPID_COMMON_EXTERN operator uint8_t() const;
    QPID_COMMON_EXTERN operator uint16_t() const;
    QPID_COMMON_EXTERN operator uint32_t() const;
    QPID_COMMON_EXTERN operator uint64_t() const;
    QPID_COMMON_EXTERN operator int8_t() const;
    QPID_COMMON_EXTERN operator int16_t() const;
    QPID_COMMON_EXTERN operator int32_t() const;
    QPID_COMMON_EXTERN operator int64_t() const;
    QPID_COMMON_EXTERN operator float() const;
    QPID_COMMON_EXTERN operator double() const;
    QPID_COMMON_EXTERN operator std::string() const;
    QPID_COMMON_EXTERN operator Uuid() const;

    QPID_COMMON_EXTERN const Map& asMap() const;
    QPID_COMMON_EXTERN Map& asMap();
    QPID_COMMON_EXTERN const List& asList() const;
    QPID_COMMON_EXTERN List& asList();
    /**
     * Unlike asString(), getString() will not do any conversions and
     * will throw InvalidConversion if the type is not STRING.
     */
    QPID_COMMON_EXTERN const std::string& getString() const;
    QPID_COMMON_EXTERN std::string& getString();

    QPID_COMMON_EXTERN void setEncoding(const std::string&);
    QPID_COMMON_EXTERN const std::string& getEncoding() const;

    QPID_COMMON_EXTERN bool isEqualTo(const Variant& a) const;

    QPID_COMMON_EXTERN void reset();    
  private:
    VariantImpl* impl;
};

QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& out, const Variant& value);
QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& out, const Variant::Map& map);
QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& out, const Variant::List& list);
QPID_COMMON_EXTERN bool operator==(const Variant& a, const Variant& b);
}} // namespace qpid::types

#endif  /*!QPID_TYPES_VARIANT_H*/
