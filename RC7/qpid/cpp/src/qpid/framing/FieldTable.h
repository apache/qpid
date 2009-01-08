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
#include <boost/shared_ptr.hpp>
#include <map>
#include "amqp_types.h"

#ifndef _FieldTable_
#define _FieldTable_

namespace qpid {
    /**
     * The framing namespace contains classes that are used to create,
     * send and receive the basic packets from which AMQP is built.
     */
namespace framing {

class Array;
class FieldValue;
class Buffer;

/**
 * A set of name-value pairs. (See the AMQP spec for more details on
 * AMQP field tables).
 *
 * \ingroup clientapi
 */
class FieldTable
{
  public:
    typedef boost::shared_ptr<FieldValue> ValuePtr;
    typedef std::map<std::string, ValuePtr> ValueMap;
    typedef ValueMap::iterator iterator;

    FieldTable() {};
    FieldTable(const FieldTable& ft);
    ~FieldTable();
    FieldTable& operator=(const FieldTable& ft);
    uint32_t encodedSize() const;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);

    int count() const;
    void set(const std::string& name, const ValuePtr& value);
    ValuePtr get(const std::string& name) const;
    bool isSet(const std::string& name) const { return get(name).get() != 0; }

    void setString(const std::string& name, const std::string& value);
    void setInt(const std::string& name, const int value);
    void setInt64(const std::string& name, const int64_t value);
    void setTimestamp(const std::string& name, const uint64_t value);
    void setUInt64(const std::string& name, const uint64_t value);
    void setTable(const std::string& name, const FieldTable& value);
    void setArray(const std::string& name, const Array& value);
    void setFloat(const std::string& name, const float value);
    void setDouble(const std::string& name, const double value);
    //void setDecimal(string& name, xxx& value);

    int getAsInt(const std::string& name) const;
    uint64_t getAsUInt64(const std::string& name) const;
    int64_t getAsInt64(const std::string& name) const;
    std::string getAsString(const std::string& name) const;

    bool getTable(const std::string& name, FieldTable& value) const;
    bool getArray(const std::string& name, Array& value) const;
    bool getFloat(const std::string& name, float& value) const;
    bool getDouble(const std::string& name, double& value) const;
    //bool getTimestamp(const std::string& name, uint64_t& value) const;
    //bool getDecimal(string& name, xxx& value);
    void erase(const std::string& name);
    

    bool operator==(const FieldTable& other) const;

    // Map-like interface.
    // TODO: may need to duplicate into versions that return mutable iterator
    ValueMap::const_iterator begin() const { return values.begin(); }
    ValueMap::const_iterator end() const { return values.end(); }
    ValueMap::const_iterator find(const std::string& s) const { return values.find(s); }

    std::pair <ValueMap::iterator, bool> insert(const ValueMap::value_type&);
    void clear() { values.clear(); }

    // ### Hack Alert

    ValueMap::iterator getValues() { return values.begin(); }
    
  private:
    ValueMap values;

    friend std::ostream& operator<<(std::ostream& out, const FieldTable& body);
};

//class FieldNotFoundException{};
//class UnknownFieldName : public FieldNotFoundException{};
//class IncorrectFieldType : public FieldNotFoundException{};
}
}


#endif
