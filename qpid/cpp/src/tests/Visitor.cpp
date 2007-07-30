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

#include "qpid/framing/Visitor.h"

#define BOOST_AUTO_TEST_MAIN    // Must come before #include<boost/test/*>
#include <boost/test/auto_unit_test.hpp>
#include <boost/tuple/tuple.hpp>

using namespace std;
using namespace qpid::framing;

struct DummyA;
struct DummyB;
struct DummyC;

typedef HandlerVisitor<boost::mpl::vector<DummyA&, DummyB&, DummyC&> > DummyVisitor;

struct DummyFrame : public AbstractVisitable<DummyVisitor> {};

struct DummyA : public ConcreteVisitable<DummyA&, DummyFrame> {};
struct DummyB : public ConcreteVisitable<DummyB&, DummyFrame> {};
struct DummyC : public ConcreteVisitable<DummyC&, DummyFrame> {};

struct TestDummyVisitor : public DummyVisitor {
    boost::tuple<DummyA*, DummyB*, DummyC*> dummies;
    void handle(DummyA& a) { dummies.get<0>() = &a; }
    void handle(DummyB& b) { dummies.get<1>() = &b; }
    void handle(DummyC& c) { dummies.get<2>() = &c; }
};

BOOST_AUTO_TEST_CASE(Visitor_accept) {
    TestDummyVisitor v;
    DummyA a;
    DummyB b;
    DummyC c;
    a.accept(v);
    BOOST_CHECK_EQUAL(&a, v.dummies.get<0>());
    b.accept(v);
    BOOST_CHECK_EQUAL(&b, v.dummies.get<1>());
    c.accept(v);
    BOOST_CHECK_EQUAL(&c, v.dummies.get<2>());
}
