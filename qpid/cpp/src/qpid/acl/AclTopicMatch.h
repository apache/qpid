#ifndef QPID_ACL_TOPIC_MATCH_H
#define QPID_ACL_TOPIC_MATCH_H

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/broker/TopicKeyNode.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/log/Statement.h"
#include "boost/shared_ptr.hpp"
#include <vector>
#include <sstream>

namespace qpid {
namespace broker {

// Class for executing topic exchange routing key matching rules in
// Acl code. Allows or denies users publishing to an exchange.
class TopicExchange::TopicExchangeTester {

class boundNode;

public:
    typedef std::vector<bool>       BindingVec;
    typedef TopicKeyNode<boundNode> TestBindingNode;

private:
    // Target class to be bound into topic key tree
    class boundNode {
    public:
        BindingVec bindingVector;
    };

    // Acl binding trees contain only one node each.
    // When the iterator sees it then the node matches the caller's spec.
    class TestFinder : public TestBindingNode::TreeIterator {
    public:
        TestFinder(BindingVec& m) : bv(m), found(false) {};
        ~TestFinder() {};
        bool visit(TestBindingNode& /*node*/) {
            assert(!found);
            found = true;
            return true;
        }
        BindingVec& bv;
        bool found;
    };

public:
    TopicExchangeTester() {};
    ~TopicExchangeTester() {};
    bool addBindingKey(const std::string& bKey) {
        std::string routingPattern = normalize(bKey);
        boundNode *mbn = bindingTree.add(routingPattern);
        if (mbn) {
            // push a dummy binding to mark this node as "non-leaf"
            mbn->bindingVector.push_back(true);
            return true;
        }
        return false;
    }

    bool findMatches(const std::string& rKey, BindingVec& matches) {
        TestFinder testFinder(matches);
        bindingTree.iterateMatch( rKey, testFinder );
        return testFinder.found;
    }

private:
    TestBindingNode bindingTree;
};
}}  // namespace qpid::broker

#endif // QPID_ACL_TOPIC_MATCH_H
