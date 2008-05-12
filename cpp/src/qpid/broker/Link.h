#ifndef _broker_Link_h
#define _broker_Link_h

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
#include "MessageStore.h"
#include "PersistableConfig.h"
#include "Bridge.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/ProtocolAccess.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Link.h"
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
    namespace broker {

        using std::string;
        class LinkRegistry;
        class Broker;
        class Connection;

        class Link : public PersistableConfig, public management::Manageable {
        private:
            sys::Mutex          lock;
            LinkRegistry*       links;
            const string        host;
            const uint16_t      port;
            const bool          useSsl;
            const bool          durable;
            mutable uint64_t    persistenceId;
            management::Link::shared_ptr mgmtObject;
            Broker* broker;
            int     state;
            sys::ProtocolAccess access;
            uint32_t visitCount;
            uint32_t currentInterval;
            bool     closing;

            typedef boost::ptr_vector<Bridge> Bridges;
            Bridges created;   // Bridges pending creation
            Bridges active;    // Bridges active
            Bridges cancelled; // Bridges pending deletion
            uint channelCounter;
            boost::shared_ptr<Connection> connection;

            static const int STATE_WAITING     = 1;
            static const int STATE_CONNECTING  = 2;
            static const int STATE_OPERATIONAL = 3;

            static const uint32_t MAX_INTERVAL = 16;

            void setState (int newState);
            void startConnection();          // Start the IO Connection
            void established();              // Called when connection is created
            void closed(int, std::string);   // Called when connection goes away
            void destroy();                  // Called when mgmt deletes this link
            void cancel(Bridge*);            // Called by self-cancelling bridge
            void ioThreadProcessing();       // Called on connection's IO thread by request
            void setConnection(boost::shared_ptr<Connection>); // Set pointer to the AMQP Connection

        public:
            typedef boost::shared_ptr<Link> shared_ptr;

            Link(LinkRegistry*           links,
                 string&                 host,
                 uint16_t                port,
                 bool                    useSsl,
                 bool                    durable,
                 Broker*                 broker,
                 management::Manageable* parent = 0);
            virtual ~Link();

            bool isDurable() { return durable; }
            void maintenanceVisit ();

            // PersistableConfig:
            void     setPersistenceId(uint64_t id) const;
            uint64_t getPersistenceId() const { return persistenceId; }
            uint32_t encodedSize() const;
            void     encode(framing::Buffer& buffer) const; 
            const string& getName() const;

            static Link::shared_ptr decode(LinkRegistry& links, framing::Buffer& buffer);

            // Manageable entry points
            management::ManagementObject::shared_ptr GetManagementObject (void) const;
            management::Manageable::status_t ManagementMethod (uint32_t, management::Args&);
        };
    }
}


#endif  /*!_broker_Link.cpp_h*/
