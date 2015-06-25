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
#ifndef _QPID_BROKER_TOPIC_KEY_NODE_
#define _QPID_BROKER_TOPIC_KEY_NODE_

#include "qpid/broker/BrokerImportExport.h"
#include <boost/shared_ptr.hpp>
#include <map>
#include <string>
#include <string.h>


namespace qpid {
namespace broker {

static const std::string STAR("*");
static const std::string HASH("#");


// Iterate over a string of '.'-separated tokens.
struct TokenIterator {
    typedef std::pair<const char*,const char*> Token;
    
    TokenIterator(const char* b, const char* e) : end(e), token(std::make_pair(b, std::find(b,e,'.'))) {}

    TokenIterator(const std::string& key) : end(&key[0]+key.size()), token(std::make_pair(&key[0], std::find(&key[0],end,'.'))) {}

    bool finished() const { return !token.first; }

    void next() {
        if (token.second == end)
            token.first = token.second = 0;
        else {
            token.first=token.second+1;
            token.second=(std::find(token.first, end, '.'));
        }
    }

    void pop(std::string &top) {
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

    bool match(const std::string& str) const {
        ptrdiff_t l=len();
        return l == ptrdiff_t(str.size()) &&
          str.compare(0, l, token.first, l) == 0;
    }

    ptrdiff_t len() const { return token.second - token.first; }


    const char* end;
    Token token;
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
template <class T>
class QPID_BROKER_CLASS_EXTERN TopicKeyNode {

 public:

    typedef boost::shared_ptr<TopicKeyNode> shared_ptr;

    // for database transversal (visit a node).
    class TreeIterator {
    public:
        TreeIterator() {};
        virtual ~TreeIterator() {};
        virtual bool visit(TopicKeyNode& node) = 0;
    };

    TopicKeyNode() : isStar(false), isHash(false) {}
    TopicKeyNode(const std::string& _t) : token(_t), isStar(_t == STAR), isHash(_t == HASH) {}
    QPID_BROKER_EXTERN virtual ~TopicKeyNode() {
        childTokens.clear();
    }

    // add normalizedRoute to tree, return associated T
    QPID_BROKER_EXTERN T* add(const std::string& normalizedRoute) {
        TokenIterator bKey(normalizedRoute);
        return add(bKey, normalizedRoute);
    }

    // return T associated with normalizedRoute
    QPID_BROKER_EXTERN T* get(const std::string& normalizedRoute) {
        TokenIterator bKey(normalizedRoute);
        return get(bKey);
    }

    // remove T associated with normalizedRoute
    QPID_BROKER_EXTERN void remove(const std::string& normalizedRoute) {
        TokenIterator bKey2(normalizedRoute);
        remove(bKey2, normalizedRoute);
    }

    // applies iter against each node in tree until iter returns false
    QPID_BROKER_EXTERN bool iterateAll(TreeIterator& iter) {
        if (!iter.visit(*this)) return false;
        if (starChild && !starChild->iterateAll(iter)) return false;
        if (hashChild && !hashChild->iterateAll(iter)) return false;
        for (typename ChildMap::iterator ptr = childTokens.begin();
             ptr != childTokens.end(); ptr++) {
            if (!ptr->second->iterateAll(iter)) return false;
        }
        return true;
    }

    // applies iter against only matching nodes until iter returns false
    QPID_BROKER_EXTERN bool iterateMatch(const std::string& routingKey, TreeIterator& iter) {
        TokenIterator rKey(routingKey);
        return iterateMatch( rKey, iter );
    }

    std::string routePattern;  // normalized binding that matches this node
    T bindings;  // for matches against this node

 private:

    std::string token;         // portion of pattern represented by this node
    bool isStar;
    bool isHash;

    // children
    typedef std::map<std::string, typename TopicKeyNode::shared_ptr> ChildMap;
    ChildMap childTokens;
    typename TopicKeyNode::shared_ptr starChild;  // "*" subtree
    typename TopicKeyNode::shared_ptr hashChild;  // "#" subtree

    unsigned int getChildCount() { return childTokens.size() +
            (starChild ? 1 : 0) + (hashChild ? 1 : 0); }

    T* add(TokenIterator& bKey, const std::string& fullPattern){
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
                    starChild.reset(new TopicKeyNode<T>(STAR));
                }
                bKey.next();
                return starChild->add(bKey, fullPattern);

            } else if (bKey.match(HASH)) {
                if (!hashChild) {
                    hashChild.reset(new TopicKeyNode<T>(HASH));
                }
                bKey.next();
                return hashChild->add(bKey, fullPattern);

            } else {
                typename ChildMap::iterator ptr;
                std::string next_token;
                bKey.pop(next_token);
                ptr = childTokens.find(next_token);
                if (ptr != childTokens.end()) {
                    return ptr->second->add(bKey, fullPattern);
                } else {
                    typename TopicKeyNode::shared_ptr child(new TopicKeyNode<T>(next_token));
                    childTokens[next_token] = child;
                    return child->add(bKey, fullPattern);
                }
            }
        }
    }


    bool remove(TokenIterator& bKey, const std::string& fullPattern) {
        bool remove;
        if (!bKey.finished()) {
            if (bKey.match(STAR)) {
                bKey.next();
                if (starChild) {
                    remove = starChild->remove(bKey, fullPattern);
                    if (remove) {
                        starChild.reset();
                    }
                }
            } else if (bKey.match(HASH)) {
                bKey.next();
                if (hashChild) {
                    remove = hashChild->remove(bKey, fullPattern);
                    if (remove) {
                        hashChild.reset();
                    }
                }
            } else {
                typename ChildMap::iterator ptr;
                std::string next_token;
                bKey.pop(next_token);
                ptr = childTokens.find(next_token);
                if (ptr != childTokens.end()) {
                    remove = ptr->second->remove(bKey, fullPattern);
                    if (remove) {
                        childTokens.erase(ptr);
                    }
                }
            }
        }

        // no bindings and no children == parent can delete this node.
        return getChildCount() == 0 && bindings.bindingVector.empty();
    }


    T* get(TokenIterator& bKey) {
        if (bKey.finished()) {
            return &bindings;
        }

        std::string next_token;
        bKey.pop(next_token);

        if (next_token == STAR) {
            if (starChild)
                return starChild->get(bKey);
        } else if (next_token == HASH) {
            if (hashChild)
                return hashChild->get(bKey);
        } else {
            typename ChildMap::iterator ptr;
            ptr = childTokens.find(next_token);
            if (ptr != childTokens.end()) {
                return ptr->second->get(bKey);
            }
        }

        return 0;
    }


    bool iterateMatch(TokenIterator& rKey, TreeIterator& iter) {
        if (isStar) return iterateMatchStar(rKey, iter);
        if (isHash) return iterateMatchHash(rKey, iter);
        return iterateMatchString(rKey, iter);
    }


    bool iterateMatchString(TokenIterator& rKey, TreeIterator& iter){
        // invariant: key has matched all previous tokens up to this node.
        if (rKey.finished()) {
            // exact match this node:  visit if bound
            if (!bindings.bindingVector.empty())
                if (!iter.visit(*this)) return false;
        }

        // check remaining key against children, even if empty.
        return iterateMatchChildren(rKey, iter);
    }


    bool iterateMatchStar(TokenIterator& rKey, TreeIterator& iter) {
        // must match one token:
        if (rKey.finished())
            return true;    // match failed, but continue iteration on siblings

        // pop the topmost token
        rKey.next();

        if (rKey.finished()) {
            // exact match this node:  visit if bound
            if (!bindings.bindingVector.empty())
                if (!iter.visit(*this)) return false;
        }

        return iterateMatchChildren(rKey, iter);
    }


    bool iterateMatchHash(TokenIterator& rKey, TreeIterator& iter) {
        // consume each token and look for a match on the
        // remaining key.
        while (!rKey.finished()) {
            if (!iterateMatchChildren(rKey, iter)) return false;
            rKey.next();
        }

        if (!bindings.bindingVector.empty())
            return iter.visit(*this);

        return true;
    }


    bool iterateMatchChildren(const TokenIterator& key, TreeIterator& iter) {
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

                typename ChildMap::iterator ptr = childTokens.find(next_token);
                if (ptr != childTokens.end()) {
                    return ptr->second->iterateMatch(newKey, iter);
                }
            }
        }

        return true;
    }
};

}
}

#endif
