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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/PersistableExchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Mutex.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Exchange.h"
#include "qmf/org/apache/qpid/broker/Binding.h"
#include "qmf/org/apache/qpid/broker/Broker.h"
#include <map>
#include <boost/function.hpp>

namespace qpid {
namespace broker {

class Broker;
class ExchangeRegistry;

class QPID_BROKER_CLASS_EXTERN Exchange : public PersistableExchange, public management::Manageable {
public:
    struct Binding : public management::Manageable {
        typedef boost::shared_ptr<Binding>       shared_ptr;
        typedef std::vector<Binding::shared_ptr> vector;

        Exchange*                 parent;
        boost::shared_ptr<Queue>         queue;
        const std::string         key;
        const framing::FieldTable args;
        std::string               origin;
        qmf::org::apache::qpid::broker::Binding::shared_ptr mgmtBinding;

        Binding(const std::string& key, boost::shared_ptr<Queue> queue, Exchange* parent = 0,
                framing::FieldTable args = framing::FieldTable(), const std::string& origin = std::string());
        ~Binding();
        void startManagement();
        management::ManagementObject::shared_ptr GetManagementObject() const;
    };

private:
    const std::string name;
    const bool durable;
    const bool autodelete;
    std::string alternateName;
    boost::shared_ptr<Exchange> alternate;
    mutable qpid::sys::Mutex usersLock;
    uint32_t alternateUsers;
    uint32_t otherUsers;
    std::map<std::string, boost::function0<void> > deletionListeners;
    mutable uint64_t persistenceId;

protected:
    mutable qpid::framing::FieldTable args;
    bool sequence;
    mutable qpid::sys::Mutex sequenceLock;
    int64_t sequenceNo;
    bool ive;
    Message lastMsg;

    class PreRoute{
    public:
        PreRoute(Deliverable& msg, Exchange* _p);
        ~PreRoute();
    private:
        Exchange* parent;
    };

    typedef boost::shared_ptr<const std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> > > ConstBindingList;
    typedef boost::shared_ptr<      std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> > > BindingList;
    void doRoute(Deliverable& msg, ConstBindingList b);
    void routeIVE();
    void checkAutodelete();
    virtual bool hasBindings() = 0;

    struct MatchQueue {
        const boost::shared_ptr<Queue> queue;
        MatchQueue(boost::shared_ptr<Queue> q);
        bool operator()(Exchange::Binding::shared_ptr b);
    };

    /** A FedBinding keeps track of information that Federation needs
        to know when to propagate changes.

        Dynamic federation needs to know which exchanges have at least
        one local binding. The bindings on these exchanges need to be
        propagated.

        Federated binds and unbinds need to know which federation
        origins are associated with the bindings for each queue. When
        origins are added or deleted, the corresponding bindings need
        to be propagated.

        fedBindings[queueName] contains the origins associated with
        the given queue.
    */

    class FedBinding {
        uint32_t localBindings;

        typedef std::set<std::string> originSet;
        std::map<std::string, originSet> fedBindings;

    public:
        FedBinding() : localBindings(0) {}
        bool hasLocal() const { return localBindings != 0; }

        /** Returns true if propagation is needed. */
        bool addOrigin(const std::string& queueName, const std::string& origin) {
            if (origin.empty()) {
                localBindings++;
                return localBindings == 1;
            }
            fedBindings[queueName].insert(origin);
            return true;
        }

        /** Returns true if propagation is needed. */
        bool delOrigin(const std::string& queueName, const std::string& origin){
            if (origin.empty()) {   // no remote == local binding
                if (localBindings > 0)
                    localBindings--;
                return localBindings == 0;
            }
            size_t match = fedBindings[queueName].erase(origin);
            if (fedBindings[queueName].empty())
                fedBindings.erase(queueName);
            return match != 0;
        }

        uint32_t count() {
            return localBindings + fedBindings.size();
        }

        uint32_t countFedBindings(const std::string& queueName) {
            // don't use '[]' - it may increase size of fedBindings!
            std::map<std::string, originSet>::iterator i;
            if ((i = fedBindings.find(queueName)) != fedBindings.end())
                return  i->second.size();
            return 0;
        }
    };

    qmf::org::apache::qpid::broker::Exchange::shared_ptr mgmtExchange;
    qmf::org::apache::qpid::broker::Broker::shared_ptr brokerMgmtObject;

public:
    typedef boost::shared_ptr<Exchange> shared_ptr;

    QPID_BROKER_EXTERN explicit Exchange(const std::string& name, management::Manageable* parent = 0,
                                         Broker* broker = 0);
    QPID_BROKER_EXTERN Exchange(const std::string& _name, bool _durable, bool autodelete, const qpid::framing::FieldTable& _args,
                                management::Manageable* parent = 0, Broker* broker = 0);
    QPID_BROKER_INLINE_EXTERN virtual ~Exchange();

    const std::string& getName() const { return name; }
    bool isDurable() { return durable; }
    QPID_BROKER_EXTERN bool isAutoDelete() const;
    QPID_BROKER_EXTERN const qpid::framing::FieldTable& getArgs() const { return args; }
    QPID_BROKER_EXTERN void setArgs(const framing::FieldTable&);

    QPID_BROKER_EXTERN Exchange::shared_ptr getAlternate() { return alternate; }
    QPID_BROKER_EXTERN void setAlternate(Exchange::shared_ptr _alternate);

    QPID_BROKER_EXTERN void incAlternateUsers();
    QPID_BROKER_EXTERN void decAlternateUsers();
    QPID_BROKER_EXTERN bool inUseAsAlternate();

    QPID_BROKER_EXTERN void incOtherUsers();
    QPID_BROKER_EXTERN void decOtherUsers(bool isControllingLink);
    QPID_BROKER_EXTERN bool inUse() const;

    virtual std::string getType() const = 0;

    /**
     *  bind() is used for two distinct purposes:
     *
     *  1. To create a binding, in the conventional sense
     *
     *  2. As a vehicle for any FedOp, currently including federated
     *  binding, federated unbinding, federated reorigin.
     *
     */

    virtual bool bind(boost::shared_ptr<Queue> queue, const std::string& routingKey, const qpid::framing::FieldTable* args) = 0;
    virtual bool unbind(boost::shared_ptr<Queue> queue, const std::string& routingKey, const qpid::framing::FieldTable* args) = 0;
    virtual bool isBound(boost::shared_ptr<Queue> queue, const std::string* const routingKey, const qpid::framing::FieldTable* const args) = 0;
    //QPID_BROKER_EXTERN virtual void setProperties(Message&);
    virtual void route(Deliverable& msg) = 0;

    //PersistableExchange:
    QPID_BROKER_EXTERN void setPersistenceId(uint64_t id) const;
    uint64_t getPersistenceId() const { return persistenceId; }
    QPID_BROKER_EXTERN uint32_t encodedSize() const;
    QPID_BROKER_EXTERN virtual void encode(framing::Buffer& buffer) const;

    static QPID_BROKER_EXTERN Exchange::shared_ptr decode(ExchangeRegistry& exchanges, framing::Buffer& buffer);

    // Manageable entry points
    QPID_BROKER_EXTERN management::ManagementObject::shared_ptr GetManagementObject(void) const;

    // Federation hooks
    class DynamicBridge {
    public:
        virtual ~DynamicBridge() {}
        virtual void propagateBinding(const std::string& key, const std::string& tagList, const std::string& op, const std::string& origin, qpid::framing::FieldTable* extra_args=0) = 0;
        virtual void sendReorigin() = 0;
        virtual bool containsLocalTag(const std::string& tagList) const = 0;
        virtual const std::string& getLocalTag() const = 0;
    };

    void registerDynamicBridge(DynamicBridge* db);
    void removeDynamicBridge(DynamicBridge* db);
    virtual bool supportsDynamicBinding() { return false; }
    Broker* getBroker() const { return broker; }
    /**
     * Notify exchange that recovery has completed.
     */
    void recoveryComplete(ExchangeRegistry& exchanges);

    bool routeWithAlternate(Deliverable& message);

    QPID_BROKER_EXTERN void destroy();
    QPID_BROKER_EXTERN bool isDestroyed() const;

    QPID_BROKER_EXTERN void setDeletionListener(const std::string& key, boost::function0<void> listener);
    QPID_BROKER_EXTERN void unsetDeletionListener(const std::string& key);

protected:
    qpid::sys::Mutex bridgeLock;
    std::vector<DynamicBridge*> bridgeVector;
    Broker* broker;
    bool destroyed;

    QPID_BROKER_EXTERN virtual void handleHelloRequest();
    void propagateFedOp(const std::string& routingKey, const std::string& tags,
                        const std::string& op,         const std::string& origin,
                        qpid::framing::FieldTable* extra_args=0);
};

}}

#endif  /*!_broker_Exchange.cpp_h*/
