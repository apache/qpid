#ifndef QPIPD_TEST_UNIT_TEST_H_
#define QPIPD_TEST_UNIT_TEST_H_

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


// Workaround so we can build against boost 1.32, 1.33 and boost 1.34.
// Remove when we no longer need to support 1.32 or 1.33.

#include <boost/version.hpp>

#if (BOOST_VERSION < 103400) // v.1.33 and earlier
# include <boost/test/auto_unit_test.hpp>
#else // v.1.34 and later
# include <boost/test/unit_test.hpp>
#endif

// Keep the test function for compilation but do not not register it.
// TODO aconway 2008-04-23: better workaround for expected failures.
// The following causes the test testUpdateTxState not to run at all.
# define QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(test_name,n)             \
    namespace { struct test_name { void test_method(); };  }            \
    void test_name::test_method()
// The following runs the test testUpdateTxState, but it fails.
/*#define QPID_AUTO_TEST_CASE_EXPECTED_FAILURES(test_name,n)             \
    namespace { struct test_name { void test_method(); };  }            \
    BOOST_AUTO_TEST_CASE(name)*/

#if (BOOST_VERSION < 103300) // v.1.32 and earlier

# define QPID_AUTO_TEST_SUITE(name)
# define QPID_AUTO_TEST_CASE(name)  BOOST_AUTO_UNIT_TEST(name)
# define QPID_AUTO_TEST_SUITE_END()

#elif (BOOST_VERSION < 103400) // v.1.33

// Note the trailing ';'
# define QPID_AUTO_TEST_SUITE(name) BOOST_AUTO_TEST_SUITE(name);
# define QPID_AUTO_TEST_CASE(name)  BOOST_AUTO_TEST_CASE(name)
# define QPID_AUTO_TEST_SUITE_END() BOOST_AUTO_TEST_SUITE_END();

#else // v.1.34 and later

# define QPID_AUTO_TEST_SUITE(name) BOOST_AUTO_TEST_SUITE(name)
# define QPID_AUTO_TEST_CASE(name)  BOOST_AUTO_TEST_CASE(name)
# define QPID_AUTO_TEST_SUITE_END() BOOST_AUTO_TEST_SUITE_END()

#endif

#endif  /*!QPIPD_TEST_UNIT_TEST_H_*/
