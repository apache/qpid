#ifndef _broker_ExchangeRegistry_h
#define _broker_ExchangeRegistry_h

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

#include <map>
#include <boost/function.hpp>
#include "Exchange.h"
#include "MessageStore.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/management/Manageable.h"

namespace qpid {
namespace broker {
    struct UnknownExchangeTypeException{};

    class ExchangeRegistry{
     public:
        typedef boost::function4<Exchange::shared_ptr, const std::string&, 
                                 bool, const qpid::framing::FieldTable&, qpid::management::Manageable*> FactoryFunction;

        ExchangeRegistry () : parent(0) {}
        std::pair<Exchange::shared_ptr, bool> declare(const std::string& name, const std::string& type)
            throw(UnknownExchangeTypeException);
        std::pair<Exchange::shared_ptr, bool> declare(const std::string& name, const std::string& type, 
                                                      bool durable, const qpid::framing::FieldTable& args = framing::FieldTable())
            throw(UnknownExchangeTypeException);
        void destroy(const std::string& name);
        Exchange::shared_ptr get(const std::string& name);
        Exchange::shared_ptr getDefault();

        /**
         * Register the manageable parent for declared exchanges
         */
        void setParent (management::Manageable* _parent) { parent = _parent; }

        void registerType(const std::string& type, FactoryFunction);
      private:
        typedef std::map<std::string, Exchange::shared_ptr> ExchangeMap;
        typedef std::map<std::string, FactoryFunction > FunctionMap;

        ExchangeMap exchanges;
        FunctionMap factory;
        qpid::sys::RWlock lock;
        management::Manageable* parent;

    };
}
}


#endif  /*!_broker_ExchangeRegistry_h*/
