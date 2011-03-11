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


// TODO aconway 2006-09-20: More efficient matching algorithm.
// Areas for improvement:
// - excessive string copying: should be 0 copy, match from original buffer.
// - match/lookup: use descision tree or other more efficient structure.

namespace 
{
const std::string STAR("*");
const std::string HASH("#");
}

// iterator for federation ReOrigin bind operation
class TopicExchange::ReOriginIter : public TopicExchange::BindingNode::TreeIterator {
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
class TopicExchange::BindingsFinderIter : public TopicExchange::BindingNode::TreeIterator {
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
class TopicExchange::QueueFinderIter : public TopicExchange::BindingNode::TreeIterator {
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


// Iterate over a string of '.'-separated tokens.
struct TopicExchange::TokenIterator {
    typedef pair<const char*,const char*> Token;
    
    TokenIterator(const char* b, const char* e) : end(e), token(make_pair(b, find(b,e,'.'))) {}

    TokenIterator(const string& key) : end(&key[0]+key.size()), token(make_pair(&key[0], find(&key[0],end,'.'))) {}

    bool finished() const { return !token.first; }

    void next() {
        if (token.second == end)
            token.first = token.second = 0;
        else {
            token.first=token.second+1;
            token.second=(find(token.first, end, '.'));
        }
    }

    void pop(string &top) {
        ptrdiff_t l = len();
        if (l) {
            top.assign(token.first, l);
        } else top.clear();
        next();
    }

    bool match1(char c) const {
        return token.second==token.first+1 && *token.first == c;
    }

    bool match(const Token& token2) const {
        ptrdiff_t l=len();
        return l == token2.second-token2.first &&
            strncmp(token.first, token2.first, l) == 0;
    }

    bool match(const string& str) const {
        ptrdiff_t l=len();
        return l == ptrdiff_t(str.size()) &&
          str.compare(0, l, token.first, l) == 0;
    }

    ptrdiff_t len() const { return token.second - token.first; }


    const char* end;
    Token token;
};


class TopicExchange::Normalizer : public TopicExchange::TokenIterator {
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

TopicExchange::TopicExchange(const std::string& _name, bool _durable,
                             const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, _args, _parent, b),
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
        BindingKey *bk = bindingTree.addBindingKey(routingPattern);
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

            Binding::shared_ptr binding (new Binding (routingPattern, queue, this, FieldTable(), fedOrigin));
            binding->startManagement();
            bk->bindingVector.push_back(binding);
            nBindings++;
            propagate = bk->fedBinding.addOrigin(queue->getName(), fedOrigin);
            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
            }
            QPID_LOG(debug, "Bound key [" << routingPattern << "] to queue " << queue->getName()
                     << " (origin=" << fedOrigin << ")");
        }
    } else if (fedOp == fedOpUnbind) {
        bool reallyUnbind = false;
        {
            RWlock::ScopedWlock l(lock);
            BindingKey* bk = bindingTree.getBindingKey(routingPattern);
            if (bk) {
                propagate = bk->fedBinding.delOrigin(queue->getName(), fedOrigin);
                reallyUnbind = bk->fedBinding.countFedBindings(queue->getName()) == 0;
            }
        }
        if (reallyUnbind)
            unbind(queue, routingPattern, 0);
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

    routeIVE();
    if (propagate)
        propagateFedOp(routingKey, fedTags, fedOp, fedOrigin);
    return true;
}

bool TopicExchange::unbind(Queue::shared_ptr queue, const string& constRoutingKey, const FieldTable* /*args*/){
	ClearCache cc(&cacheLock,&bindingCache); // clear the cache on function exit.
    RWlock::ScopedWlock l(lock);
    string routingKey = normalize(constRoutingKey);
    BindingKey* bk = bindingTree.getBindingKey(routingKey);
    if (!bk) return false;
    Binding::vector& qv(bk->bindingVector);
    bool propagate = false;

    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    if(q == qv.end()) return false;
    qv.erase(q);
    assert(nBindings > 0);
    nBindings--;
    propagate = bk->fedBinding.delOrigin();
    if(qv.empty()) {
        bindingTree.removeBindingKey(routingKey);
    }
    if (mgmtExchange != 0) {
        mgmtExchange->dec_bindingCount();
    }
    QPID_LOG(debug, "Unbound [" << routingKey << "] from queue " << queue->getName());

    if (propagate)
        propagateFedOp(routingKey, string(), fedOpUnbind, string());
    return true;
}

bool TopicExchange::isBound(Queue::shared_ptr queue, const string& pattern)
{
    // Note well: lock held by caller....
    BindingKey *bk = bindingTree.getBindingKey(pattern);  // Exact match against binding pattern
    if (!bk) return false;
    Binding::vector& qv(bk->bindingVector);
    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    return q != qv.end();
}

void TopicExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/)
{
    // Note: PERFORMANCE CRITICAL!!!
    BindingList b;
	std::map<std::string, BindingList>::iterator it;
	{  // only lock the cache for read
       RWlock::ScopedRlock cl(cacheLock);
	   it = bindingCache.find(routingKey);
	}
    PreRoute pr(msg, this);
    if (it == bindingCache.end())  // no cache hit
    {
        RWlock::ScopedRlock l(lock);
    	b = BindingList(new std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >);
        BindingsFinderIter bindingsFinder(b);
        bindingTree.iterateMatch(routingKey, bindingsFinder);
	    RWlock::ScopedWlock cwl(cacheLock);
		bindingCache[routingKey] = b; // update cache
    }else {
        b = it->second;
     }
    doRoute(msg, b);
}

bool TopicExchange::isBound(Queue::shared_ptr queue, const string* const routingKey, const FieldTable* const)
{
    RWlock::ScopedRlock l(lock);
    if (routingKey && queue) {
        string key(normalize(*routingKey));
        return isBound(queue, key);
    } else if (!routingKey && !queue) {
        return nBindings > 0;
    } else if (routingKey) {
        if (bindingTree.getBindingKey(*routingKey)) {
            return true;
        }
    } else {
        QueueFinderIter queueFinder(queue);
        bindingTree.iterateAll( queueFinder );
        return queueFinder.found;
    }
    return false;
}

TopicExchange::~TopicExchange() {}

const std::string TopicExchange::typeName("topic");

//
// class BindingNode
//

TopicExchange::BindingNode::~BindingNode()
{
    childTokens.clear();
}


// Add a binding pattern to the tree.  Return a pointer to the binding key
// of the node that matches the binding pattern.
TopicExchange::BindingKey*
TopicExchange::BindingNode::addBindingKey(const std::string& normalizedRoute)
{
    TokenIterator bKey(normalizedRoute);
    return addBindingKey(bKey, normalizedRoute);
}


// Return a pointer to the binding key of the leaf node that matches the binding pattern.
TopicExchange::BindingKey*
TopicExchange::BindingNode::getBindingKey(const std::string& normalizedRoute)
{
    TokenIterator bKey(normalizedRoute);
    return getBindingKey(bKey);
}


// Delete the binding associated with the given route.
void TopicExchange::BindingNode::removeBindingKey(const std::string& normalizedRoute)
{
    TokenIterator bKey2(normalizedRoute);
    removeBindingKey(bKey2, normalizedRoute);
}

// visit each node in the tree.  Note: all nodes are visited,
// even non-leaf nodes (i.e. nodes without any bindings)
bool TopicExchange::BindingNode::iterateAll(TopicExchange::BindingNode::TreeIterator& iter)
{
    if (!iter.visit(*this)) return false;

    if (starChild && !starChild->iterateAll(iter)) return false;

    if (hashChild && !hashChild->iterateAll(iter)) return false;

    for (ChildMap::iterator ptr = childTokens.begin();
         ptr != childTokens.end(); ptr++) {

        if (!ptr->second->iterateAll(iter)) return false;
    }

    return true;
}

// applies iter against only matching nodes until iter returns false
// Note Well: the iter may match against the same node more than once
// if # wildcards are present!
bool TopicExchange::BindingNode::iterateMatch(const std::string& routingKey, TreeIterator& iter)
{
    TopicExchange::TokenIterator rKey(routingKey);
    return iterateMatch( rKey, iter );
}


// recurse over binding using token iterator.
// Note well: bkey is modified!
TopicExchange::BindingKey*
TopicExchange::BindingNode::addBindingKey(TokenIterator &bKey,
                                          const string& fullPattern)
{
    if (bKey.finished()) {
        // this node's binding
        if (routePattern.empty()) {
            routePattern = fullPattern;
        } else assert(routePattern == fullPattern);

        return &bindings;

    } else {
        // pop the topmost token & recurse...

        if (bKey.match(STAR)) {
            if (!starChild) {
                starChild.reset(new StarNode());
            }
            bKey.next();
            return starChild->addBindingKey(bKey, fullPattern);

        } else if (bKey.match(HASH)) {
            if (!hashChild) {
                hashChild.reset(new HashNode());
            }
            bKey.next();
            return hashChild->addBindingKey(bKey, fullPattern);

        } else {
            ChildMap::iterator ptr;
            std::string next_token;
            bKey.pop(next_token);
            ptr = childTokens.find(next_token);
            if (ptr != childTokens.end()) {
                return ptr->second->addBindingKey(bKey, fullPattern);
            } else {
                BindingNode::shared_ptr child(new BindingNode(next_token));
                childTokens[next_token] = child;
                return child->addBindingKey(bKey, fullPattern);
            }
        }
    }
}


// Remove a binding pattern from the tree.  Return true if the current
// node becomes a leaf without any bindings (therefore can be deleted).
// Note Well: modifies parameter bKey's value!
bool
TopicExchange::BindingNode::removeBindingKey(TokenIterator &bKey,
                                             const string& fullPattern)
{
    bool remove;

    if (!bKey.finished()) {

        if (bKey.match(STAR)) {
            bKey.next();
            if (starChild) {
                remove = starChild->removeBindingKey(bKey, fullPattern);
                if (remove) {
                    starChild.reset();
                }
            }
        } else if (bKey.match(HASH)) {
            bKey.next();
            if (hashChild) {
                remove = hashChild->removeBindingKey(bKey, fullPattern);
                if (remove) {
                    hashChild.reset();
                }
            }
        } else {
            ChildMap::iterator ptr;
            std::string next_token;
            bKey.pop(next_token);
            ptr = childTokens.find(next_token);
            if (ptr != childTokens.end()) {
                remove = ptr->second->removeBindingKey(bKey, fullPattern);
                if (remove) {
                    childTokens.erase(ptr);
                }
            }
        }
    }

    // no bindings and no children == parent can delete this node.
    return getChildCount() == 0 && bindings.bindingVector.empty();
}


// find the binding key that matches the given binding pattern.
// Note Well: modifies key parameter!
TopicExchange::BindingKey*
TopicExchange::BindingNode::getBindingKey(TokenIterator &key)
{
    if (key.finished()) {
        return &bindings;
    }

    string next_token;

    key.pop(next_token);

    if (next_token == STAR) {
        if (starChild)
            return starChild->getBindingKey(key);
    } else if (next_token == HASH) {
        if (hashChild)
            return hashChild->getBindingKey(key);
    } else {
        ChildMap::iterator ptr;
        ptr = childTokens.find(next_token);
        if (ptr != childTokens.end()) {
            return ptr->second->getBindingKey(key);
        }
    }

    return 0;
}



// iterate over all nodes that match the given key.  Note well: the set of nodes
// that are visited includes matching non-leaf nodes.
// Note well: parameter key is modified!
bool TopicExchange::BindingNode::iterateMatch(TokenIterator& key, TreeIterator& iter)
{
    // invariant: key has matched all previous tokens up to this node.
    if (key.finished()) {
        // exact match this node:  visit if bound
        if (!bindings.bindingVector.empty())
            if (!iter.visit(*this)) return false;
    }

    // check remaining key against children, even if empty.
    return iterateMatchChildren(key, iter);
}


TopicExchange::StarNode::StarNode()
    : BindingNode(STAR) {}


// See iterateMatch() above.
// Special case: this node must verify a token is available (match exactly one).
bool TopicExchange::StarNode::iterateMatch(TokenIterator& key, TreeIterator& iter)
{
    // must match one token:
    if (key.finished())
        return true;    // match failed, but continue iteration on siblings

    // pop the topmost token
    key.next();

    if (key.finished()) {
        // exact match this node:  visit if bound
        if (!bindings.bindingVector.empty())
            if (!iter.visit(*this)) return false;
    }

    return iterateMatchChildren(key, iter);
}


TopicExchange::HashNode::HashNode()
    : BindingNode(HASH) {}


// See iterateMatch() above.
// Special case: can match zero or more tokens at the head of the key.
bool TopicExchange::HashNode::iterateMatch(TokenIterator& key, TreeIterator& iter)
{
    // consume each token and look for a match on the
    // remaining key.
    while (!key.finished()) {
        if (!iterateMatchChildren(key, iter)) return false;
        key.next();
    }

    if (!bindings.bindingVector.empty())
        return iter.visit(*this);

    return true;
}


// helper: iterate over current node's matching children
bool
TopicExchange::BindingNode::iterateMatchChildren(const TopicExchange::TokenIterator& key,
                                                 TopicExchange::BindingNode::TreeIterator& iter)
{
    // always try glob - it can match empty keys
    if (hashChild) {
        TokenIterator tmp(key);
        if (!hashChild->iterateMatch(tmp, iter))
            return false;
    }

    if (!key.finished()) {

        if (starChild) {
            TokenIterator tmp(key);
            if (!starChild->iterateMatch(tmp, iter))
                return false;
        }

        if (!childTokens.empty()) {
            TokenIterator newKey(key);
            std::string next_token;
            newKey.pop(next_token);

            ChildMap::iterator ptr = childTokens.find(next_token);
            if (ptr != childTokens.end()) {
                return ptr->second->iterateMatch(newKey, iter);
            }
        }
    }

    return true;
}

}} // namespace qpid::broker
