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
#ifndef _QueueBindings_
#define _QueueBindings_

#include "qpid/framing/FieldTable.h"
#include <boost/ptr_container/ptr_list.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class ExchangeRegistry;
class Queue;
class QueueBindings
{
    struct Binding{
        const std::string exchange;
        const std::string key;
        const qpid::framing::FieldTable args;
        Binding(const std::string& exchange, const std::string& key, const qpid::framing::FieldTable& args);
    };
    
    typedef boost::ptr_list<Binding> Bindings;
    Bindings bindings;
    
public:
    void add(const std::string& exchange, const std::string& key, const qpid::framing::FieldTable& args);
    void unbind(ExchangeRegistry& exchanges, boost::shared_ptr<Queue> queue);
};


}
}


#endif
