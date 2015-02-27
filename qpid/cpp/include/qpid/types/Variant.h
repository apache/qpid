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
#include "qpid/types/Exception.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/types/ImportExport.h"

namespace qpid {
namespace types {

/**
 * Thrown when an illegal conversion of a variant is attempted.
 */
struct QPID_TYPES_CLASS_EXTERN InvalidConversion : public Exception 
{
    QPID_TYPES_EXTERN InvalidConversion(const std::string& msg);
    QPID_TYPES_EXTERN ~InvalidConversion() throw();
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

QPID_TYPES_EXTERN std::string getTypeName(VariantType type);

QPID_TYPES_EXTERN bool isIntegerType(VariantType type);

class VariantImpl;

/**
 * Represents a value of variable type.
 */
class QPID_TYPES_CLASS_EXTERN Variant
{
  public:
    typedef std::map<std::string, Variant> Map;
    typedef std::list<Variant> List;

    QPID_TYPES_EXTERN Variant();
    QPID_TYPES_EXTERN Variant(bool);
    QPID_TYPES_EXTERN Variant(uint8_t);
    QPID_TYPES_EXTERN Variant(uint16_t);
    QPID_TYPES_EXTERN Variant(uint32_t);
    QPID_TYPES_EXTERN Variant(uint64_t);
    QPID_TYPES_EXTERN Variant(int8_t);
    QPID_TYPES_EXTERN Variant(int16_t);
    QPID_TYPES_EXTERN Variant(int32_t);
    QPID_TYPES_EXTERN Variant(int64_t);
    QPID_TYPES_EXTERN Variant(float);
    QPID_TYPES_EXTERN Variant(double);
    QPID_TYPES_EXTERN Variant(const std::string&);
    QPID_TYPES_EXTERN Variant(const std::string& value, const std::string& encoding);
    QPID_TYPES_EXTERN Variant(const char*);
    QPID_TYPES_EXTERN Variant(const char* value, const char* encoding);
    QPID_TYPES_EXTERN Variant(const Map&);
    QPID_TYPES_EXTERN Variant(const List&);
    QPID_TYPES_EXTERN Variant(const Variant&);
    QPID_TYPES_EXTERN Variant(const Uuid&);

    QPID_TYPES_EXTERN ~Variant();

    QPID_TYPES_EXTERN VariantType getType() const;
    QPID_TYPES_EXTERN bool isVoid() const;
    
    QPID_TYPES_EXTERN Variant& operator=(bool);
    QPID_TYPES_EXTERN Variant& operator=(uint8_t);
    QPID_TYPES_EXTERN Variant& operator=(uint16_t);
    QPID_TYPES_EXTERN Variant& operator=(uint32_t);
    QPID_TYPES_EXTERN Variant& operator=(uint64_t);
    QPID_TYPES_EXTERN Variant& operator=(int8_t);
    QPID_TYPES_EXTERN Variant& operator=(int16_t);
    QPID_TYPES_EXTERN Variant& operator=(int32_t);
    QPID_TYPES_EXTERN Variant& operator=(int64_t);
    QPID_TYPES_EXTERN Variant& operator=(float);
    QPID_TYPES_EXTERN Variant& operator=(double);
    QPID_TYPES_EXTERN Variant& operator=(const std::string&);
    QPID_TYPES_EXTERN Variant& operator=(const char*);
    QPID_TYPES_EXTERN Variant& operator=(const Map&);
    QPID_TYPES_EXTERN Variant& operator=(const List&);
    QPID_TYPES_EXTERN Variant& operator=(const Variant&);
    QPID_TYPES_EXTERN Variant& operator=(const Uuid&);

    /**
     * Parses the argument and assigns itself the appropriate
     * value. Recognises integers, doubles and booleans.
     */
    QPID_TYPES_EXTERN Variant& parse(const std::string&);

    QPID_TYPES_EXTERN bool asBool() const;
    QPID_TYPES_EXTERN uint8_t asUint8() const;
    QPID_TYPES_EXTERN uint16_t asUint16() const;
    QPID_TYPES_EXTERN uint32_t asUint32() const;
    QPID_TYPES_EXTERN uint64_t asUint64() const;
    QPID_TYPES_EXTERN int8_t asInt8() const;
    QPID_TYPES_EXTERN int16_t asInt16() const;
    QPID_TYPES_EXTERN int32_t asInt32() const;
    QPID_TYPES_EXTERN int64_t asInt64() const;
    QPID_TYPES_EXTERN float asFloat() const;
    QPID_TYPES_EXTERN double asDouble() const;
    QPID_TYPES_EXTERN std::string asString() const;
    QPID_TYPES_EXTERN Uuid asUuid() const;

    QPID_TYPES_EXTERN operator bool() const;
    QPID_TYPES_EXTERN operator uint8_t() const;
    QPID_TYPES_EXTERN operator uint16_t() const;
    QPID_TYPES_EXTERN operator uint32_t() const;
    QPID_TYPES_EXTERN operator uint64_t() const;
    QPID_TYPES_EXTERN operator int8_t() const;
    QPID_TYPES_EXTERN operator int16_t() const;
    QPID_TYPES_EXTERN operator int32_t() const;
    QPID_TYPES_EXTERN operator int64_t() const;
    QPID_TYPES_EXTERN operator float() const;
    QPID_TYPES_EXTERN operator double() const;
    QPID_TYPES_EXTERN operator std::string() const;
    QPID_TYPES_EXTERN operator Uuid() const;

    QPID_TYPES_EXTERN const Map& asMap() const;
    QPID_TYPES_EXTERN Map& asMap();
    QPID_TYPES_EXTERN const List& asList() const;
    QPID_TYPES_EXTERN List& asList();

    /**
     * Unlike asString(), getString() will not do any conversions.
     * @exception InvalidConversion if the type is not STRING.
     */
    QPID_TYPES_EXTERN const std::string& getString() const;
    QPID_TYPES_EXTERN std::string& getString();

    QPID_TYPES_EXTERN void setEncoding(const std::string&);
    QPID_TYPES_EXTERN const std::string& getEncoding() const;

    QPID_TYPES_EXTERN bool isEqualTo(const Variant& a) const;

    /** Reset value to VOID, does not reset the descriptors. */
    QPID_TYPES_EXTERN void reset();

    /** True if there is at least one descriptor associated with this variant. */
    QPID_TYPES_EXTERN bool isDescribed() const;

    /** Get the first descriptor associated with this variant.
     *
     * Normally there is at most one descriptor, when there are multiple
     * descriptors use getDescriptors()
     *
     *@return The first descriptor or VOID if there is no descriptor.
     *@see isDescribed, getDescriptors
     */
    QPID_TYPES_EXTERN Variant getDescriptor() const;

    /** Set a single descriptor for this Variant. The descriptor must be a string or integer. */
    QPID_TYPES_EXTERN void setDescriptor(const Variant& descriptor);

    /** Return a modifiable list of descriptors for this Variant.
     * Used in case where there are multiple descriptors, for a single descriptor use
     * getDescriptor and setDescriptor.
     */
    QPID_TYPES_EXTERN List& getDescriptors();

    /** Return the list of descriptors for this Variant.
     * Used in case where there are multiple descriptors, for a single descriptor use
     * getDescriptor and setDescriptor.
     */
    QPID_TYPES_EXTERN const List& getDescriptors() const;

    /** Create a described value */
    QPID_TYPES_EXTERN static Variant described(const Variant& descriptor, const Variant& value);

    /** Create a described list, a common special case */
    QPID_TYPES_EXTERN static Variant described(const Variant& descriptor, const List& value);

  private:
    mutable VariantImpl* impl;
};

#ifndef SWIG
QPID_TYPES_EXTERN std::ostream& operator<<(std::ostream& out, const Variant& value);
QPID_TYPES_EXTERN std::ostream& operator<<(std::ostream& out, const Variant::Map& map);
QPID_TYPES_EXTERN std::ostream& operator<<(std::ostream& out, const Variant::List& list);
QPID_TYPES_EXTERN bool operator==(const Variant& a, const Variant& b);
QPID_TYPES_EXTERN bool operator!=(const Variant& a, const Variant& b);
#endif
}} // namespace qpid::types

#endif  /*!QPID_TYPES_VARIANT_H*/
