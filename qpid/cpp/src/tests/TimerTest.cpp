
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
#include "qpid/sys/Timer.h"
#include "qpid/sys/Monitor.h"
#include "qpid/Options.h"
#include "unit_test.h"
#include <math.h>
#include <iostream>
#include <memory>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>

using namespace qpid::sys;
using boost::intrusive_ptr;
using boost::dynamic_pointer_cast;

namespace qpid {
namespace tests {

class Counter
{
    Mutex lock;
    uint counter;
  public:
    Counter() : counter(0) {}
    uint next()
    {
        Mutex::ScopedLock l(lock);
        return ++counter;
    }
};

class TestTask : public TimerTask
{
    const AbsTime start;
    const Duration expected;
    AbsTime end;
    bool fired;
    uint position;
    Monitor monitor;
    Counter& counter;

  public:
    TestTask(Duration timeout, Counter& _counter)
        : TimerTask(timeout, "Test"), start(now()), expected(timeout), end(start), fired(false), counter(_counter) {}

    void fire()
    {
        Monitor::ScopedLock l(monitor);
        fired = true;
        position = counter.next();
        end = now();
        monitor.notify();
    }

    void check(uint expected_position, uint64_t tolerance = 500 * TIME_MSEC)
    {
        Monitor::ScopedLock l(monitor);
        BOOST_CHECK(fired);
        BOOST_CHECK_EQUAL(expected_position, position);
        Duration actual(start, end);
#ifdef _MSC_VER
        uint64_t difference = _abs64(expected - actual);
#elif defined(_WIN32)
        uint64_t difference = labs(expected - actual);
#elif defined(__SUNPRO_CC) || defined (__IBMCPP__)
        uint64_t difference = llabs(expected - actual);
#else
        uint64_t difference = abs(expected - actual);
#endif
        std::string msg(boost::lexical_cast<std::string>(boost::format("tolerance = %1%, difference = %2%") % tolerance % difference));
        BOOST_CHECK_MESSAGE(difference < tolerance, msg);
    }

    void wait(Duration d)
    {
        Monitor::ScopedLock l(monitor);
        monitor.wait(AbsTime(now(), d));
    }
};

class DummyRunner : public Runnable
{
  public:
    void run() {}
};

QPID_AUTO_TEST_SUITE(TimerTestSuite)

QPID_AUTO_TEST_CASE(testGeneral)
{
    Counter counter;
    Timer timer;
    intrusive_ptr<TestTask> task1(new TestTask(Duration(3 * TIME_SEC), counter));
    intrusive_ptr<TestTask> task2(new TestTask(Duration(1 * TIME_SEC), counter));
    intrusive_ptr<TestTask> task3(new TestTask(Duration(4 * TIME_SEC), counter));
    intrusive_ptr<TestTask> task4(new TestTask(Duration(2 * TIME_SEC), counter));

    timer.add(task1);
    timer.add(task2);
    timer.add(task3);
    timer.add(task4);

    dynamic_pointer_cast<TestTask>(task3)->wait(Duration(6 * TIME_SEC));

    dynamic_pointer_cast<TestTask>(task1)->check(3);
    dynamic_pointer_cast<TestTask>(task2)->check(1);
    dynamic_pointer_cast<TestTask>(task3)->check(4);
    dynamic_pointer_cast<TestTask>(task4)->check(2);
}

std::string toString(Duration d) { return boost::lexical_cast<std::string>(d); }
Duration fromString(const std::string& str) { return boost::lexical_cast<Duration>(str); }

QPID_AUTO_TEST_CASE(testOstreamInOut) {
    std::string empty;
    BOOST_CHECK_EQUAL(toString(Duration(int64_t(TIME_SEC))), "1s");
    BOOST_CHECK_EQUAL(toString(Duration(int64_t(TIME_SEC*123.4))), "123.4s");
    BOOST_CHECK_EQUAL(toString(Duration(int64_t(TIME_MSEC*123.4))), "123.4ms");
    BOOST_CHECK_EQUAL(toString(Duration(int64_t(TIME_USEC*123.4))), "123.4us");
    BOOST_CHECK_EQUAL(toString(Duration(int64_t(TIME_NSEC*123))), "123ns");

    BOOST_CHECK_EQUAL(fromString("123.4"), Duration(int64_t(TIME_SEC*123.4)));
    BOOST_CHECK_EQUAL(fromString("123.4s"), Duration(int64_t(TIME_SEC*123.4)));
    BOOST_CHECK_EQUAL(fromString("123ms"), Duration(int64_t(TIME_MSEC*123)));
    BOOST_CHECK_EQUAL(fromString("123us"), Duration(int64_t(TIME_USEC*123)));
    BOOST_CHECK_EQUAL(fromString("123ns"), Duration(int64_t(TIME_NSEC*123)));

    Duration d = 0;
    std::istringstream i;
    std::string s;

    i.str("123x");
    i >> d;
    BOOST_CHECK(i.fail());
    BOOST_CHECK_EQUAL(d, 0);
    BOOST_CHECK_EQUAL(i.str(), "123x");

    i.str("xxx");
    i >> d;
    BOOST_CHECK(i.fail());
    BOOST_CHECK_EQUAL(d, 0);
    BOOST_CHECK_EQUAL(i.str(), "xxx");
}

QPID_AUTO_TEST_CASE(testOptionParse) {
    Options opts;
    Duration interval;
    opts.addOptions()("interval", optValue(interval, "I"), "blah");
    const char *args[] = { "fakeexe", "--interval", "123.4" };
    opts.parse(sizeof(args)/sizeof(args[0]), args);
    BOOST_CHECK_EQUAL(interval, Duration(int64_t(TIME_SEC*123.4)));
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
