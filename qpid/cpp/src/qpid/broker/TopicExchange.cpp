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
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/FedOps.h"
#include "qpid/log/Statement.h"
#include <algorithm>


namespace qpid {
namespace broker {

using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;

// iterator for federation ReOrigin bind operation
class TopicExchange::ReOriginIter : public BindingNode::TreeIterator {
public:
    ReOriginIter() {};
    ~ReOriginIter() {};
    bool visit(BindingNode& node) {
        if (node.bindings.fedBinding.hasLocal()) {
            keys2prop.push_back(node.routePattern);
        }
        return true;
    }
    std::vector<std::string> keys2prop;
};


// match iterator used by route(): builds BindingList of all unique queues
// that match the routing key.
class TopicExchange::BindingsFinderIter : public BindingNode::TreeIterator {
public:
    BindingsFinderIter(BindingList &bl) : b(bl) {};
    ~BindingsFinderIter() {};

    BindingList& b;
    std::set<std::string> qSet;

    bool visit(BindingNode& node) {

        Binding::vector& qv(node.bindings.bindingVector);
        for (Binding::vector::iterator j = qv.begin(); j != qv.end(); j++) {
            // do not duplicate queues on the binding list
            if (qSet.insert(j->get()->queue->getName()).second) {
                b->push_back(*j);
            }
        }
        return true;
    }
};



// Iterator to visit all bindings until a given queue is found
class TopicExchange::QueueFinderIter : public BindingNode::TreeIterator {
public:
    QueueFinderIter(Queue::shared_ptr queue) : queue(queue), found(false) {};
    ~QueueFinderIter() {};
    bool visit(BindingNode& node) {

        Binding::vector& qv(node.bindings.bindingVector);
        Binding::vector::iterator q;
        for (q = qv.begin(); q != qv.end(); q++) {
            if ((*q)->queue == queue) {
                found = true;
                return false;   // search done
            }
        }
        return true; // continue search
    }

    Queue::shared_ptr queue;
    bool found;
};


class TopicExchange::Normalizer : public TokenIterator {
  public:
    Normalizer(string& p)
        : TokenIterator(&p[0], &p[0]+p.size()), pattern(p)
    { normalize(); }

  private:
    // Apply 2 transformations:  #.*  -> *.# and #.# -> #
    void normalize() {
        while (!finished()) {
            if (match1('#')) {
                const char* hash1=token.first;
                next();
                if (!finished()) {
                    if (match1('#')) { // Erase #.# -> #
                        pattern.erase(hash1-pattern.data(), 2);
                        token.first -= 2;
                        token.second -= 2;
                        end -= 2;
                    }
                    else if (match1('*'))  { // Swap #.* -> *.#
                        swap(*const_cast<char*>(hash1),
                             *const_cast<char*>(token.first));
                    }
                }
            }
            else
                next();
        }
    }

    string& pattern;
};



// Convert sequences of * and # to a sequence of * followed by a single #
string TopicExchange::normalize(const string& pattern) {
    string normal(pattern);
    Normalizer n(normal);
    return normal;
}


TopicExchange::TopicExchange(const string& _name, Manageable* _parent, Broker* b)
    : Exchange(_name, _parent, b),
      nBindings(0)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

TopicExchange::TopicExchange(const std::string& _name, bool _durable, bool autodelete,
                             const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, autodelete, _args, _parent, b),
    nBindings(0)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

bool TopicExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args)
{
    ClearCache cc(&cacheLock,&bindingCache); // clear the cache on function exit.
    string fedOp(args ? args->getAsString(qpidFedOp) : fedOpBind);
    string fedTags(args ? args->getAsString(qpidFedTags) : "");
    string fedOrigin(args ? args->getAsString(qpidFedOrigin) : "");
    bool propagate = false;
    string routingPattern = normalize(routingKey);

    if (args == 0 || fedOp.empty() || fedOp == fedOpBind) {
        RWlock::ScopedWlock l(lock);
        BindingKey *bk = bindingTree.add(routingPattern);
        if (bk) {
            Binding::vector& qv(bk->bindingVector);
            Binding::vector::iterator q;
            for (q = qv.begin(); q != qv.end(); q++) {
                if ((*q)->queue == queue) {
                    // already bound, but may be from a different fedOrigin
                    bk->fedBinding.addOrigin(queue->getName(), fedOrigin);
                    return false;
                }
            }

            Binding::shared_ptr binding (new Binding (routingPattern, queue, this, args ? *args : FieldTable(), fedOrigin));
            binding->startManagement();
            bk->bindingVector.push_back(binding);
            nBindings++;
            propagate = bk->fedBinding.addOrigin(queue->getName(), fedOrigin);
            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
            }
            QPID_LOG(debug, "Binding key [" << routingPattern << "] to queue " << queue->getName()
                     << " on exchange " << getName() << " (origin=" << fedOrigin << ")");
        }
    } else if (fedOp == fedOpUnbind) {
        RWlock::ScopedWlock l(lock);
        BindingKey* bk = getQueueBinding(queue, routingPattern);
        if (bk) {
            QPID_LOG(debug, "FedOpUnbind [" << routingPattern << "] from exchange " << getName()
                     << " on queue=" << queue->getName() << " origin=" << fedOrigin);
            propagate = bk->fedBinding.delOrigin(queue->getName(), fedOrigin);
            // if this was the last binding for the queue, delete the binding
            if (bk->fedBinding.countFedBindings(queue->getName()) == 0) {
                deleteBinding(queue, routingPattern, bk);
            }
        }
    } else if (fedOp == fedOpReorigin) {
        /** gather up all the keys that need rebinding in a local vector
         * while holding the lock.  Then propagate once the lock is
         * released
         */
        ReOriginIter reOriginIter;
        {
            RWlock::ScopedRlock l(lock);
            bindingTree.iterateAll( reOriginIter );
        }   /* lock dropped */

        for (std::vector<std::string>::const_iterator key = reOriginIter.keys2prop.begin();
             key != reOriginIter.keys2prop.end(); key++) {
            propagateFedOp( *key, string(), fedOpBind, string());
        }
    }

    cc.clearCache(); // clear the cache before we IVE route.
    routeIVE();
    if (propagate)
        propagateFedOp(routingKey, fedTags, fedOp, fedOrigin);
    return true;
}

bool TopicExchange::unbind(Queue::shared_ptr queue, const string& constRoutingKey, const FieldTable* args)
{
    string fedOrigin(args ? args->getAsString(qpidFedOrigin) : "");
    QPID_LOG(debug, "Unbinding key [" << constRoutingKey << "] from queue " << queue->getName()
             << " on exchange " << getName() << " origin=" << fedOrigin << ")" );

    ClearCache cc(&cacheLock,&bindingCache); // clear the cache on function exit.
    RWlock::ScopedWlock l(lock);
    string routingKey = normalize(constRoutingKey);
    BindingKey* bk = getQueueBinding(queue, routingKey);
    if (!bk) return false;
    bool propagate = bk->fedBinding.delOrigin(queue->getName(), fedOrigin);
    deleteBinding(queue, routingKey, bk);
    if (propagate)
        propagateFedOp(routingKey, string(), fedOpUnbind, string());
    if (nBindings == 0) checkAutodelete();
    return true;
}


bool TopicExchange::deleteBinding(Queue::shared_ptr queue,
                                  const std::string& routingKey,
                                  BindingKey *bk)
{
    // Note well: write lock held by caller
    Binding::vector& qv(bk->bindingVector);
    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    if(q == qv.end()) return false;
    qv.erase(q);
    assert(nBindings > 0);
    nBindings--;

    if(qv.empty()) {
        bindingTree.remove(routingKey);
    }
    if (mgmtExchange != 0) {
        mgmtExchange->dec_bindingCount();
    }
    QPID_LOG(debug, "Unbound key [" << routingKey << "] from queue " << queue->getName()
             << " on exchange " << getName());
    return true;
}

/** returns a pointer to the BindingKey if the given queue is bound to this
 * exchange using the routing pattern. 0 if queue binding does not exist.
 */
TopicExchange::BindingKey *TopicExchange::getQueueBinding(Queue::shared_ptr queue, const string& pattern)
{
    // Note well: lock held by caller....
    BindingKey *bk = bindingTree.get(pattern);  // Exact match against binding pattern
    if (!bk) return 0;
    Binding::vector& qv(bk->bindingVector);
    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    return (q != qv.end()) ? bk : 0;
}

void TopicExchange::route(Deliverable& msg)
{
    const string& routingKey = msg.getMessage().getRoutingKey();
    // Note: PERFORMANCE CRITICAL!!!
    BindingList b;
    std::map<std::string, BindingList>::iterator it;
    {  // only lock the cache for read
       RWlock::ScopedRlock cl(cacheLock);
       it = bindingCache.find(routingKey);
       if (it != bindingCache.end()) {
           b = it->second;
       }
    }
    PreRoute pr(msg, this);
    if (!b.get())  // no cache hit
    {
        RWlock::ScopedRlock l(lock);
    	b = BindingList(new std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >);
        BindingsFinderIter bindingsFinder(b);
        bindingTree.iterateMatch(routingKey, bindingsFinder);
        RWlock::ScopedWlock cwl(cacheLock);
        bindingCache[routingKey] = b; // update cache
    }
    doRoute(msg, b);
}

bool TopicExchange::isBound(Queue::shared_ptr queue, const string* const routingKey, const FieldTable* const)
{
    RWlock::ScopedRlock l(lock);
    if (routingKey && queue) {
        string key(normalize(*routingKey));
        return getQueueBinding(queue, key) != 0;
    } else if (!routingKey && !queue) {
        return nBindings > 0;
    } else if (routingKey) {
        if (bindingTree.get(*routingKey)) {
            return true;
        }
    } else {
        QueueFinderIter queueFinder(queue);
        bindingTree.iterateAll( queueFinder );
        return queueFinder.found;
    }
    return false;
}

TopicExchange::~TopicExchange() {
    if (mgmtExchange != 0)
        mgmtExchange->debugStats("destroying");
}

const std::string TopicExchange::typeName("topic");

bool TopicExchange::hasBindings()
{
    RWlock::ScopedRlock l(lock);
    return nBindings > 0;
}

}} // namespace qpid::broker
