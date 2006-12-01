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
#include <amqp_types.h>

#ifndef _FieldTable_
#define _FieldTable_

namespace qpid {
namespace framing {

class Value;
class Buffer;

class FieldTable
{
  public:
    typedef boost::shared_ptr<Value> ValuePtr;
    typedef std::map<std::string, ValuePtr> ValueMap;

    ~FieldTable();
    u_int32_t size() const;
    int count() const;
    void setString(const std::string& name, const std::string& value);
    void setInt(const std::string& name, int value);
    void setTimestamp(const std::string& name, u_int64_t value);
    void setTable(const std::string& name, const FieldTable& value);
    //void setDecimal(string& name, xxx& value);
    std::string getString(const std::string& name) const;
    int getInt(const std::string& name) const;
    u_int64_t getTimestamp(const std::string& name) const;
    void getTable(const std::string& name, FieldTable& value) const;
    //void getDecimal(string& name, xxx& value);
    void erase(const std::string& name);
    
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);

    bool operator==(const FieldTable& other) const;

    // TODO aconway 2006-09-26: Yeuch! Rework FieldTable to  have
    // a map-like interface.
    const ValueMap& getMap() const { return values; }
    ValueMap& getMap() { return values; }
    
  private:
  friend std::ostream& operator<<(std::ostream& out, const FieldTable& body);
    ValueMap values;
    template<class T> T getValue(const std::string& name) const;
};

class FieldNotFoundException{};
class UnknownFieldName : public FieldNotFoundException{};
class IncorrectFieldType : public FieldNotFoundException{};
}
}


#endif
