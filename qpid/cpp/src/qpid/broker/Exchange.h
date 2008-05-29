#ifndef _broker_Exchange_h
#define _broker_Exchange_h

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

#include <boost/shared_ptr.hpp>
#include "Deliverable.h"
#include "Queue.h"
#include "MessageStore.h"
#include "PersistableExchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Exchange.h"
#include "qpid/management/Binding.h"

namespace qpid {
    namespace broker {
        using std::string;
        class ExchangeRegistry;

        class Exchange : public PersistableExchange, public management::Manageable {
        private:
            const string name;
            const bool durable;
            qpid::framing::FieldTable args;
            boost::shared_ptr<Exchange> alternate;
            uint32_t alternateUsers;
            mutable uint64_t persistenceId;

        protected:
            struct Binding : public management::Manageable {
                typedef boost::shared_ptr<Binding>       shared_ptr;
                typedef std::vector<Binding::shared_ptr> vector;

                Queue::shared_ptr         queue;
                const std::string         key;
                const framing::FieldTable args;
                management::Binding::shared_ptr mgmtBinding;

                Binding(const std::string& key, Queue::shared_ptr queue, Exchange* parent = 0,
                        framing::FieldTable args = framing::FieldTable ());
                ~Binding ();
                management::ManagementObject::shared_ptr GetManagementObject () const;
                management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args);
            };

            management::Exchange::shared_ptr mgmtExchange;

        public:
            typedef boost::shared_ptr<Exchange> shared_ptr;

            explicit Exchange(const string& name, management::Manageable* parent = 0);
            Exchange(const string& _name, bool _durable, const qpid::framing::FieldTable& _args,
                     management::Manageable* parent = 0);
            virtual ~Exchange();

            const string& getName() const { return name; }
            bool isDurable() { return durable; }
            qpid::framing::FieldTable& getArgs() { return args; }

            Exchange::shared_ptr getAlternate() { return alternate; }
            void setAlternate(Exchange::shared_ptr _alternate) { alternate = _alternate; }
            void incAlternateUsers() { alternateUsers++; }
            void decAlternateUsers() { alternateUsers--; }
            bool inUseAsAlternate() { return alternateUsers > 0; }

            virtual string getType() const = 0;
            virtual bool bind(Queue::shared_ptr queue, const string& routingKey, const qpid::framing::FieldTable* args) = 0;
            virtual bool unbind(Queue::shared_ptr queue, const string& routingKey, const qpid::framing::FieldTable* args) = 0;
            virtual bool isBound(Queue::shared_ptr queue, const string* const routingKey, const qpid::framing::FieldTable* const args) = 0;
            virtual void route(Deliverable& msg, const string& routingKey, const qpid::framing::FieldTable* args) = 0;

            //PersistableExchange:
            void setPersistenceId(uint64_t id) const;
            uint64_t getPersistenceId() const { return persistenceId; }
            uint32_t encodedSize() const;
            void encode(framing::Buffer& buffer) const; 

            static Exchange::shared_ptr decode(ExchangeRegistry& exchanges, framing::Buffer& buffer);

            // Manageable entry points
            management::ManagementObject::shared_ptr GetManagementObject (void) const;
            management::Manageable::status_t
                ManagementMethod (uint32_t, management::Args&) { return management::Manageable::STATUS_UNKNOWN_METHOD; }
        };
    }
}


#endif  /*!_broker_Exchange.cpp_h*/
