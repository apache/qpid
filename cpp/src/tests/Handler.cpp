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

#define BOOST_AUTO_TEST_MAIN    // Must come before #include<boost/test/*>
#include <boost/test/auto_unit_test.hpp>

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FrameHandler.h"
#include "test_tools.h"
#include <boost/assign.hpp>

using namespace std;
using namespace qpid;
using namespace qpid::framing;
using namespace boost::assign;

typedef int DummyFrame;
typedef int DummyContext;
vector<int> handledBy;

/** Both a link and a terminal handler. */
template <int N>
struct TestHandler : public Handler<DummyFrame> {
    TestHandler(DummyContext c) : context(c) {}
    
    void handle(DummyFrame frame) {
        handledBy.push_back(N+context);
        nextHandler(frame);
    }

    typedef HandlerFactoryImpl<TestHandler<N>, DummyContext> Factory;
    DummyContext context;
};



BOOST_AUTO_TEST_CASE(handlerChains) {
    TestHandler<1>::Factory a;
    TestHandler<2>::Factory b;
    TestHandler<3>::Factory c;
    HandlerFactoryChain<DummyFrame, DummyContext> factory;
    factory.push_back(&a);
    factory.push_back(&b);
    factory.push_back(&c);
    factory.create(10)->handle(1);

    vector<int> expect;
    expect = list_of(11)(12)(13);
    BOOST_CHECK_EQUAL(expect, handledBy);
}


