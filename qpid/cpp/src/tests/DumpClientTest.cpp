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


#include "unit_test.h"
#include "test_tools.h"
#include "BrokerFixture.h"
#include "qpid/cluster/DumpClient.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/Url.h"
#include <boost/assign.hpp>

QPID_AUTO_TEST_SUITE(DumpClientTest)

using namespace std;
using namespace qpid;
using namespace framing;
using namespace client;
using namespace cluster;
using namespace sys;

// Verify we can copy shared state - wiring + messages - from one
// broker to another via the DumpClient.
// 
QPID_AUTO_TEST_CASE(testDumpClientSharedState) {
    BrokerFixture donor, receiver;
    {
        Client c(donor.getPort());
        FieldTable args;
        args.setString("x", "y");
        c.session.queueDeclare("qa", arg::arguments=args);
        c.session.queueDeclare("qb", arg::alternateExchange="amq.direct");

        c.session.exchangeDeclare(arg::exchange="exd", arg::type="direct", arg::arguments=args);
        c.session.exchangeBind(arg::exchange="exd", arg::queue="qa", arg::bindingKey="foo");
        c.session.messageTransfer(arg::destination="exd", arg::content=Message("one", "foo"));

        c.session.exchangeDeclare("ext", arg::type="topic");
        c.session.exchangeBind(arg::exchange="ext", arg::queue="qb", arg::bindingKey="bar");
        c.subs.subscribe(c.lq, "qa", FlowControl::messageCredit(0));
        c.session.messageTransfer(arg::destination="ext", arg::content=Message("one", "bar"));
        c.session.messageTransfer(arg::destination="ext", arg::content=Message("two", "bar"));

        c.session.close();
        c.connection.close();
    }
    Url url(Url::getIpAddressesUrl(receiver.getPort()));
    qpid::cluster::DumpClient dump(url, *donor.broker, 0, 0);
    dump.dump();
    {
        Client r(receiver.getPort());
        // Verify exchanges
        ExchangeQueryResult ex=r.session.exchangeQuery("exd");
        BOOST_CHECK_EQUAL(ex.getType(), "direct");
        BOOST_CHECK_EQUAL(ex.getDurable(), false);
        BOOST_CHECK_EQUAL(ex.getNotFound(), false);
        BOOST_CHECK_EQUAL(ex.getArguments().getString("x"), "y");

        ex = r.session.exchangeQuery("ext");
        BOOST_CHECK_EQUAL(ex.getType(), "topic");
        BOOST_CHECK_EQUAL(ex.getNotFound(), false);

        // Verify queues
        QueueQueryResult qq = r.session.queueQuery("qa");
        BOOST_CHECK_EQUAL(qq.getQueue(), "qa");
        BOOST_CHECK_EQUAL(qq.getAlternateExchange(), "");
        BOOST_CHECK_EQUAL(qq.getArguments().getString("x"), "y");
        BOOST_CHECK_EQUAL(qq.getMessageCount(), (unsigned)1);

        qq = r.session.queueQuery("qb");
        BOOST_CHECK_EQUAL(qq.getQueue(), "qb");
        BOOST_CHECK_EQUAL(qq.getAlternateExchange(), "amq.direct");
        BOOST_CHECK_EQUAL(qq.getMessageCount(), (unsigned)2);

        // Verify messages
        Message m;
        BOOST_CHECK(r.subs.get(m, "qa", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "one");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "exd");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "foo");

        BOOST_CHECK(r.subs.get(m, "qb", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "one");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "ext");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "bar");

        BOOST_CHECK(r.subs.get(m, "qb", TIME_SEC));
        BOOST_CHECK_EQUAL(m.getData(), "two");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getExchange(), "ext");
        BOOST_CHECK_EQUAL(m.getDeliveryProperties().getRoutingKey(), "bar");

        // Verify bindings
        r.session.messageTransfer(arg::destination="exd", arg::content=Message("xxx", "foo"));
        BOOST_CHECK(r.subs.get(m, "qa"));
        BOOST_CHECK_EQUAL(m.getData(), "xxx");
        
        r.session.messageTransfer(arg::destination="ext", arg::content=Message("yyy", "bar"));
        BOOST_CHECK(r.subs.get(m, "qb"));
        BOOST_CHECK_EQUAL(m.getData(), "yyy");

        r.session.close();
        r.connection.close();
    }
}

QPID_AUTO_TEST_SUITE_END()
