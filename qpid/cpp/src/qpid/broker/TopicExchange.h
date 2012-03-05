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
#ifndef _TopicExchange_
#define _TopicExchange_

#include <map>
#include <vector>
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/broker/Queue.h"


namespace qpid {
namespace broker {

class TopicExchange : public virtual Exchange {

    struct TokenIterator;
    class Normalizer;

    struct BindingKey {        // binding for this node
        Binding::vector bindingVector;
        FedBinding fedBinding;
    };

    // Binding database:
    // The dotted form of a binding key is broken up and stored in a directed tree graph.
    // Common binding prefix are merged.  This allows the route match alogrithm to quickly
    // isolate those sub-trees that match a given routingKey.
    // For example, given the routes:
    //     a.b.c.<...>
    //     a.b.d.<...>
    //     a.x.y.<...>
    // The resulting tree would be:
    //    a-->b-->c-->...
    //    |   +-->d-->...
    //    +-->x-->y-->...
    //
    class QPID_BROKER_CLASS_EXTERN BindingNode {
    public:

        typedef boost::shared_ptr<BindingNode> shared_ptr;

        // for database transversal (visit a node).
        class TreeIterator {
        public:
            TreeIterator() {};
            virtual ~TreeIterator() {};
            virtual bool visit(BindingNode& node) = 0;
        };

        BindingNode() {};
        BindingNode(const std::string& token) : token(token) {};
        QPID_BROKER_EXTERN virtual ~BindingNode();

        // add normalizedRoute to tree, return associated BindingKey
        QPID_BROKER_EXTERN BindingKey* addBindingKey(const std::string& normalizedRoute);

        // return BindingKey associated with normalizedRoute
        QPID_BROKER_EXTERN BindingKey* getBindingKey(const std::string& normalizedRoute);

        // remove BindingKey associated with normalizedRoute
        QPID_BROKER_EXTERN void removeBindingKey(const std::string& normalizedRoute);

        // applies iter against each node in tree until iter returns false
        QPID_BROKER_EXTERN bool iterateAll(TreeIterator& iter);

        // applies iter against only matching nodes until iter returns false
        QPID_BROKER_EXTERN bool iterateMatch(const std::string& routingKey, TreeIterator& iter);

        std::string routePattern;  // normalized binding that matches this node
        BindingKey bindings;  // for matches against this node

  protected:

        std::string token;         // portion of pattern represented by this node

        // children
        typedef std::map<const std::string, BindingNode::shared_ptr> ChildMap;
        ChildMap childTokens;
        BindingNode::shared_ptr starChild;  // "*" subtree
        BindingNode::shared_ptr hashChild;  // "#" subtree

        unsigned int getChildCount() { return childTokens.size() +
              (starChild ? 1 : 0) + (hashChild ? 1 : 0); }
        BindingKey* addBindingKey(TokenIterator& bKey,
                                  const std::string& fullPattern);
        bool removeBindingKey(TokenIterator& bKey,
                              const std::string& fullPattern);
        BindingKey* getBindingKey(TokenIterator& bKey);
        QPID_BROKER_EXTERN virtual bool iterateMatch(TokenIterator& rKey, TreeIterator& iter);
        bool iterateMatchChildren(const TokenIterator& key, TreeIterator& iter);
    };

    // Special case: ("*" token) Node in the tree for a match exactly one wildcard
    class StarNode : public BindingNode {
    public:
        StarNode();
        ~StarNode() {};

    protected:
        virtual bool iterateMatch(TokenIterator& key, TreeIterator& iter);
    };

    // Special case: ("#" token) Node in the tree for a match zero or more
    class HashNode : public BindingNode {
    public:
        HashNode();
        ~HashNode() {};

    protected:
        virtual bool iterateMatch(TokenIterator& key, TreeIterator& iter);
    };

    BindingNode bindingTree;
    unsigned long nBindings;
    qpid::sys::RWlock lock;     // protects bindingTree and nBindings
    qpid::sys::RWlock cacheLock;     // protects cache
    std::map<std::string, BindingList> bindingCache; // cache of matched routes.
    class ClearCache {
    private:
        qpid::sys::RWlock* cacheLock;
        std::map<std::string, BindingList>* bindingCache;
	bool cleared; 
    public:
        ClearCache(qpid::sys::RWlock* l, std::map<std::string, BindingList>* bc): cacheLock(l),
             bindingCache(bc),cleared(false) {};
        void clearCache() {
             qpid::sys::RWlock::ScopedWlock l(*cacheLock);
             if (!cleared) {
                 bindingCache->clear();
                 cleared =true;
             }
        };
        ~ClearCache(){ 
	     clearCache();
        };
    };
    BindingKey *getQueueBinding(Queue::shared_ptr queue, const std::string& pattern);
    bool deleteBinding(Queue::shared_ptr queue,
                       const std::string& routingKey,
                       BindingKey *bk);

    class ReOriginIter;
    class BindingsFinderIter;
    class QueueFinderIter;

  public:
    static const std::string typeName;

    static QPID_BROKER_EXTERN std::string normalize(const std::string& pattern);

    QPID_BROKER_EXTERN TopicExchange(const std::string& name,
                                     management::Manageable* parent = 0, Broker* broker = 0);
    QPID_BROKER_EXTERN TopicExchange(const std::string& _name,
                                     bool _durable,
                                     const qpid::framing::FieldTable& _args,
                                     management::Manageable* parent = 0, Broker* broker = 0);

    virtual std::string getType() const { return typeName; }

    QPID_BROKER_EXTERN virtual bool bind(Queue::shared_ptr queue,
                                         const std::string& routingKey,
                                         const qpid::framing::FieldTable* args);

    virtual bool unbind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    QPID_BROKER_EXTERN virtual void route(Deliverable& msg);

    QPID_BROKER_EXTERN virtual bool isBound(Queue::shared_ptr queue,
                                            const std::string* const routingKey,
                                            const qpid::framing::FieldTable* const args);

    QPID_BROKER_EXTERN virtual ~TopicExchange();
    virtual bool supportsDynamicBinding() { return true; }

    class TopicExchangeTester;
    friend class TopicExchangeTester;
};



}
}

#endif
