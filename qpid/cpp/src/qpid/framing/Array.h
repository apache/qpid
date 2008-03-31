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
#include "FieldValue.h"

#ifndef _Array_
#define _Array_

namespace qpid {
namespace framing {

class Buffer;

class Array
{
  public:
    typedef boost::shared_ptr<FieldValue> ValuePtr;
    typedef std::vector<ValuePtr> ValueVector;

    uint32_t size() const;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);

    int count() const;
    bool operator==(const Array& other) const;

    Array();
    Array(uint8_t type);
    //creates a longstr array
    Array(const std::vector<std::string>& in);

    void add(ValuePtr value);

    template <class T>
    void collect(std::vector<T>& out)
    {
        for (ValueVector::const_iterator i = values.begin(); i != values.end(); ++i) {
            out.push_back((*i)->get<T>());
        }
    }
    
  private:
    uint8_t typeOctet;
    ValueVector values;

    ValueVector::const_iterator begin() const { return values.begin(); }
    ValueVector::const_iterator end() const { return values.end(); }

    friend std::ostream& operator<<(std::ostream& out, const Array& body);
};

}
}


#endif
