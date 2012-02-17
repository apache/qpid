#ifndef _broker_LinkRegistry_h
#define _broker_LinkRegistry_h

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
#include "qpid/broker/Bridge.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/Address.h"
#include "qpid/sys/Mutex.h"
#include "qpid/management/Manageable.h"
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <boost/function.hpp>

namespace qpid {
namespace broker {

    class Link;
    class Broker;
    class Connection;
    class LinkRegistry {
        typedef std::map<std::string, boost::shared_ptr<Link> > LinkMap;
        typedef std::map<std::string, Bridge::shared_ptr> BridgeMap;

        LinkMap   links;
        BridgeMap bridges;

        qpid::sys::Mutex lock;
        Broker* broker;
        management::Manageable* parent;
        MessageStore* store;
        bool passive;
        std::string realm;

        boost::shared_ptr<Link> findLink(const std::string& key);
        static std::string createKey(const Address& address);
        static std::string createKey(const std::string& host, uint16_t port);

    public:
        LinkRegistry (); // Only used in store tests
        LinkRegistry (Broker* _broker);
        ~LinkRegistry();

        std::pair<boost::shared_ptr<Link>, bool>
            declare(const std::string& host,
                    uint16_t     port,
                    const std::string& transport,
                    bool         durable,
                    const std::string& authMechanism,
                    const std::string& username,
                    const std::string& password);

        std::pair<Bridge::shared_ptr, bool>
            declare(const std::string& host,
                    uint16_t     port,
                    bool         durable,
                    const std::string& src,
                    const std::string& dest,
                    const std::string& key,
                    bool         isQueue,
                    bool         isLocal,
                    const std::string& id,
                    const std::string& excludes,
                    bool         dynamic,
                    uint16_t     sync,
                    Bridge::InitializeCallback=0
            );

        void destroy(const std::string& host, const uint16_t port);

        void destroy(const std::string& host,
                     const uint16_t     port,
                     const std::string& src,
                     const std::string& dest,
                     const std::string& key);

        /**
         * Register the manageable parent for declared queues
         */
        void setParent (management::Manageable* _parent) { parent = _parent; }

        /**
         * Set the store to use.  May only be called once.
         */
        void setStore (MessageStore*);

        /**
         * Return the message store used.
         */
        MessageStore* getStore() const;

        void notifyConnection (const std::string& key, Connection* c);
        void notifyOpened     (const std::string& key);
        void notifyClosed     (const std::string& key);
        void notifyConnectionForced    (const std::string& key, const std::string& text);
        std::string getAuthMechanism   (const std::string& key);
        std::string getAuthCredentials (const std::string& key);
        std::string getAuthIdentity    (const std::string& key);
        std::string getUsername        (const std::string& key);
        std::string getPassword        (const std::string& key);
        std::string getHost            (const std::string& key);
        uint16_t    getPort            (const std::string& key);

        /**
         * Called by links failing over to new address
         */
        void changeAddress(const Address& oldAddress, const Address& newAddress);

        /**
         * Called to alter passive state. In passive state the links
         * and bridges managed by a link registry will be recorded and
         * updated but links won't actually establish connections and
         * bridges won't therefore pull or push any messages.
         */
        void setPassive(bool);
        bool isPassive() { return passive; }

        /** Iterate over each link in the registry. Used for cluster updates. */
        void eachLink(boost::function<void(boost::shared_ptr<Link>)> f);
        /** Iterate over each bridge in the registry. Used for cluster updates. */
        void eachBridge(boost::function<void(boost::shared_ptr< Bridge>)> f);
    };
}
}


#endif  /*!_broker_LinkRegistry_h*/
