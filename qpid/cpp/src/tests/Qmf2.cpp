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
#include <iostream>
#include "qpid/types/Variant.h"
#include "qmf/QueryImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/exceptions.h"
#include "qpid/messaging/Connection.h"
#include "qmf/PosixEventNotifierImpl.h"
#include "qmf/AgentSession.h"
#include "qmf/AgentSessionImpl.h"
#include "qmf/ConsoleSession.h"
#include "qmf/ConsoleSessionImpl.h"
#include "unit_test.h"

using namespace std;
using namespace qpid::types;
using namespace qpid::messaging;
using namespace qmf;

bool isReadable(int fd)
{
    fd_set rfds;
    struct timeval tv;
    int nfds, result;

    FD_ZERO(&rfds);
    FD_SET(fd, &rfds);
    nfds = fd + 1;
    tv.tv_sec = 0;
    tv.tv_usec = 0;

    result = select(nfds, &rfds, NULL, NULL, &tv);

    return result > 0;
}

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(Qmf2Suite)

QPID_AUTO_TEST_CASE(testQuery)
{
    Query query(QUERY_OBJECT, "class_name", "package_name", "[and, [eq, name, [quote, smith]], [lt, age, [quote, 27]]]");
    Query newQuery(new QueryImpl(QueryImplAccess::get(query).asMap()));

    BOOST_CHECK_EQUAL(newQuery.getTarget(), QUERY_OBJECT);
    BOOST_CHECK_EQUAL(newQuery.getSchemaId().getName(), "class_name");
    BOOST_CHECK_EQUAL(newQuery.getSchemaId().getPackageName(), "package_name");

    Variant::List pred(newQuery.getPredicate());
    BOOST_CHECK_EQUAL(pred.size(), size_t(3));

    Variant::List::iterator iter(pred.begin());
    BOOST_CHECK_EQUAL(iter->asString(), "and");
    iter++;
    BOOST_CHECK_EQUAL(iter->getType(), VAR_LIST);
    iter++;
    BOOST_CHECK_EQUAL(iter->getType(), VAR_LIST);
    iter = iter->asList().begin();
    BOOST_CHECK_EQUAL(iter->asString(), "lt");
    iter++;
    BOOST_CHECK_EQUAL(iter->asString(), "age");
    iter++;
    BOOST_CHECK_EQUAL(iter->getType(), VAR_LIST);
    iter = iter->asList().begin();
    BOOST_CHECK_EQUAL(iter->asString(), "quote");
    iter++;
    BOOST_CHECK_EQUAL(iter->asUint32(), uint32_t(27));

    Query query2(QUERY_OBJECT_ID);
    Query newQuery2(new QueryImpl(QueryImplAccess::get(query2).asMap()));
    BOOST_CHECK_EQUAL(newQuery2.getTarget(), QUERY_OBJECT_ID);

    Query query3(QUERY_SCHEMA);
    Query newQuery3(new QueryImpl(QueryImplAccess::get(query3).asMap()));
    BOOST_CHECK_EQUAL(newQuery3.getTarget(), QUERY_SCHEMA);

    Query query4(QUERY_SCHEMA_ID);
    Query newQuery4(new QueryImpl(QueryImplAccess::get(query4).asMap()));
    BOOST_CHECK_EQUAL(newQuery4.getTarget(), QUERY_SCHEMA_ID);

    DataAddr addr("name", "agent_name", 34);
    Query query5(addr);
    Query newQuery5(new QueryImpl(QueryImplAccess::get(query5).asMap()));
    BOOST_CHECK_EQUAL(newQuery5.getTarget(), QUERY_OBJECT);
    BOOST_CHECK_EQUAL(newQuery5.getDataAddr().getName(), "name");
    BOOST_CHECK_EQUAL(newQuery5.getDataAddr().getAgentName(), "agent_name");
    BOOST_CHECK_EQUAL(newQuery5.getDataAddr().getAgentEpoch(), uint32_t(34));
}

QPID_AUTO_TEST_CASE(testQueryPredicateErrors)
{
    Query query;
    Variant::Map map;

    BOOST_CHECK_THROW(Query(QUERY_OBJECT, "INVALID"), QmfException);
    query = Query(QUERY_OBJECT, "[unknown, one, two]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[eq, first]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[exists]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[eq, first, [quote, 1, 2, 3]]]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[eq, first, [unexpected, 3]]]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[eq, first, {}]]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[eq, first, second, third]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);

    query = Query(QUERY_OBJECT, "[and, first, second, third]");
    BOOST_CHECK_THROW(query.matchesPredicate(map), QmfException);
}

QPID_AUTO_TEST_CASE(testQueryPredicate)
{
    Query query;
    Variant::Map map;

    map["forty"] = 40;
    map["fifty"] = 50;
    map["minus_ten"] = -10;
    map["pos_float"] = 100.05;
    map["neg_float"] = -1000.33;
    map["name"] = "jones";
    map["bool_t"] = true;
    map["bool_f"] = false;

    BOOST_CHECK_THROW(Query(QUERY_OBJECT, "INVALID"), QmfException);

    query = Query(QUERY_OBJECT);
    BOOST_CHECK_EQUAL(query.matchesPredicate(Variant::Map()), true);

    query = Query(QUERY_OBJECT, "[eq, forty, [quote, 40]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[eq, forty, [quote, 41]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[le, forty, fifty]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[and, [eq, forty, [quote, 40]], [eq, name, [quote, jones]]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[and, [eq, forty, [quote, 40]], [eq, name, [quote, smith]]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[or, [eq, forty, [quote, 40]], [eq, name, [quote, smith]]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[or, [eq, forty, [quote, 41]], [eq, name, [quote, smith]]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[not, [le, forty, [quote, 40]]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[le, forty, [quote, 40]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[ge, forty, [quote, 40]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[lt, forty, [quote, 45]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[lt, [quote, 45], forty]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[gt, forty, [quote, 45]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[gt, [quote, 45], forty]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[eq, bool_t, [quote, True]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[eq, bool_t, [quote, False]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[eq, bool_f, [quote, True]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[eq, bool_f, [quote, False]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[eq, minus_ten, [quote, -10]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[lt, minus_ten, [quote, -20]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[lt, [quote, -20], minus_ten]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[exists, name]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);

    query = Query(QUERY_OBJECT, "[exists, nonexfield]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), false);

    query = Query(QUERY_OBJECT, "[eq, pos_float, [quote, 100.05]]");
    BOOST_CHECK_EQUAL(query.matchesPredicate(map), true);
}

QPID_AUTO_TEST_CASE(testSchema)
{
    Schema in(SCHEMA_TYPE_DATA, "package", "class");
    in.addProperty(SchemaProperty("prop1", SCHEMA_DATA_BOOL,   "{desc:'Property One'}"));
    in.addProperty(SchemaProperty("prop2", SCHEMA_DATA_INT,    "{desc:'Property Two',unit:'Furlong'}"));
    in.addProperty(SchemaProperty("prop3", SCHEMA_DATA_STRING, "{desc:'Property Three'}"));

    SchemaMethod method1("method1", "{desc:'Method One'}");
    method1.addArgument(SchemaProperty("arg1", SCHEMA_DATA_BOOL,  "{desc:'Argument One',dir:IN}"));
    method1.addArgument(SchemaProperty("arg2", SCHEMA_DATA_INT,   "{desc:'Argument Two',dir:OUT}"));
    method1.addArgument(SchemaProperty("arg3", SCHEMA_DATA_FLOAT, "{desc:'Argument Three',dir:INOUT}"));
    in.addMethod(method1);

    SchemaMethod method2("method2", "{desc:'Method Two'}");
    method2.addArgument(SchemaProperty("arg21", SCHEMA_DATA_BOOL,  "{desc:'Argument One',dir:IN}"));
    method2.addArgument(SchemaProperty("arg22", SCHEMA_DATA_INT,   "{desc:'Argument Two',dir:OUT}"));
    method2.addArgument(SchemaProperty("arg23", SCHEMA_DATA_FLOAT, "{desc:'Argument Three',dir:INOUT}"));
    in.addMethod(method2);

    BOOST_CHECK(!in.isFinalized());
    in.finalize();
    BOOST_CHECK(in.isFinalized());

    Variant::Map map(SchemaImplAccess::get(in).asMap());
    Schema out(new SchemaImpl(map));

    BOOST_CHECK(out.isFinalized());
    BOOST_CHECK_EQUAL(out.getSchemaId().getType(), SCHEMA_TYPE_DATA);
    BOOST_CHECK_EQUAL(out.getSchemaId().getPackageName(), "package");
    BOOST_CHECK_EQUAL(out.getSchemaId().getName(), "class");
    BOOST_CHECK_EQUAL(out.getSchemaId().getHash(), in.getSchemaId().getHash());

    BOOST_CHECK_EQUAL(out.getPropertyCount(), uint32_t(3));
    SchemaProperty prop;

    prop = out.getProperty(0);
    BOOST_CHECK_EQUAL(prop.getName(), "prop1");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_BOOL);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Property One");

    prop = out.getProperty(1);
    BOOST_CHECK_EQUAL(prop.getName(), "prop2");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_INT);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Property Two");
    BOOST_CHECK_EQUAL(prop.getUnit(), "Furlong");
    BOOST_CHECK(!prop.isIndex());

    prop = out.getProperty(2);
    BOOST_CHECK_EQUAL(prop.getName(), "prop3");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_STRING);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Property Three");

    BOOST_CHECK_THROW(out.getProperty(3), QmfException);

    BOOST_CHECK_EQUAL(out.getMethodCount(), uint32_t(2));
    SchemaMethod method;

    method = out.getMethod(0);
    BOOST_CHECK_EQUAL(method.getName(), "method1");
    BOOST_CHECK_EQUAL(method.getDesc(), "Method One");
    BOOST_CHECK_EQUAL(method.getArgumentCount(), uint32_t(3));

    prop = method.getArgument(0);
    BOOST_CHECK_EQUAL(prop.getName(), "arg1");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_BOOL);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument One");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_IN);

    prop = method.getArgument(1);
    BOOST_CHECK_EQUAL(prop.getName(), "arg2");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_INT);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument Two");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_OUT);

    prop = method.getArgument(2);
    BOOST_CHECK_EQUAL(prop.getName(), "arg3");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_FLOAT);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument Three");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_IN_OUT);

    BOOST_CHECK_THROW(method.getArgument(3), QmfException);

    method = out.getMethod(1);
    BOOST_CHECK_EQUAL(method.getName(), "method2");
    BOOST_CHECK_EQUAL(method.getDesc(), "Method Two");
    BOOST_CHECK_EQUAL(method.getArgumentCount(), uint32_t(3));

    prop = method.getArgument(0);
    BOOST_CHECK_EQUAL(prop.getName(), "arg21");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_BOOL);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument One");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_IN);

    prop = method.getArgument(1);
    BOOST_CHECK_EQUAL(prop.getName(), "arg22");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_INT);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument Two");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_OUT);

    prop = method.getArgument(2);
    BOOST_CHECK_EQUAL(prop.getName(), "arg23");
    BOOST_CHECK_EQUAL(prop.getType(), SCHEMA_DATA_FLOAT);
    BOOST_CHECK_EQUAL(prop.getDesc(), "Argument Three");
    BOOST_CHECK_EQUAL(prop.getDirection(), DIR_IN_OUT);

    BOOST_CHECK_THROW(method.getArgument(3), QmfException);
}

QPID_AUTO_TEST_CASE(testAgentSessionEventListener)
{
    Connection connection("localhost");
    AgentSession session(connection, "");
    posix::EventNotifier notifier(session);

    AgentSessionImpl& sessionImpl = AgentSessionImplAccess::get(session);
            
    BOOST_CHECK(sessionImpl.getEventNotifier() != 0);
}

QPID_AUTO_TEST_CASE(testConsoleSessionEventListener)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    posix::EventNotifier notifier(session);

    ConsoleSessionImpl& sessionImpl = ConsoleSessionImplAccess::get(session);

    BOOST_CHECK(sessionImpl.getEventNotifier() != 0);
}

QPID_AUTO_TEST_CASE(testGetHandle)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    posix::EventNotifier notifier(session);

    BOOST_CHECK(notifier.getHandle() > 0);
}

QPID_AUTO_TEST_CASE(testSetReadableToFalse)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    posix::EventNotifier notifier(session);
    PosixEventNotifierImplAccess::get(notifier).setReadable(false);

    bool readable(isReadable(notifier.getHandle()));
    BOOST_CHECK(!readable);
}

QPID_AUTO_TEST_CASE(testSetReadable)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    posix::EventNotifier notifier(session);
    PosixEventNotifierImplAccess::get(notifier).setReadable(true);

    bool readable(isReadable(notifier.getHandle()));
    BOOST_CHECK(readable);
}

QPID_AUTO_TEST_CASE(testSetReadableMultiple)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    posix::EventNotifier notifier(session);
    for (int i = 0; i < 15; i++)
        PosixEventNotifierImplAccess::get(notifier).setReadable(true);
    PosixEventNotifierImplAccess::get(notifier).setReadable(false);

    bool readable(isReadable(notifier.getHandle()));
    BOOST_CHECK(!readable);
}

QPID_AUTO_TEST_CASE(testDeleteNotifier)
{
    Connection connection("localhost");
    ConsoleSession session(connection, "");
    ConsoleSessionImpl& sessionImpl = ConsoleSessionImplAccess::get(session);
    {
        posix::EventNotifier notifier(session);
        BOOST_CHECK(sessionImpl.getEventNotifier() != 0);
    }
    BOOST_CHECK(sessionImpl.getEventNotifier() == 0);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
