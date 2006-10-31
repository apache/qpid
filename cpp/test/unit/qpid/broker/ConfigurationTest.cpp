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
#include "qpid/broker/Configuration.h"
#include <qpid_test_plugin.h>
#include <iostream>

using namespace std;
using namespace qpid::broker;

class ConfigurationTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ConfigurationTest);
    CPPUNIT_TEST(testIsHelp);
    CPPUNIT_TEST(testPortLongForm);
    CPPUNIT_TEST(testPortShortForm);
    CPPUNIT_TEST(testAcceptorLongForm);
    CPPUNIT_TEST(testAcceptorShortForm);
    CPPUNIT_TEST(testVarious);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testIsHelp() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--help"};
        conf.parse(2, argv);
        CPPUNIT_ASSERT(conf.isHelp());
    }

    void testPortLongForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--port", "6789"};
        conf.parse(3, argv);
        CPPUNIT_ASSERT_EQUAL(6789, conf.getPort());
    }

    void testPortShortForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "-p", "6789"};
        conf.parse(3, argv);
        CPPUNIT_ASSERT_EQUAL(6789, conf.getPort());
    }

    void testAcceptorLongForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--acceptor", "blocking"};
        conf.parse(3, argv);
        CPPUNIT_ASSERT_EQUAL(string("blocking"), conf.getAcceptor());
    }

    void testAcceptorShortForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "-a", "blocking"};
        conf.parse(3, argv);
        CPPUNIT_ASSERT_EQUAL(string("blocking"), conf.getAcceptor());
    }

    void testVarious() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "-t", "--worker-threads", "10", "-a", "blocking"};
        conf.parse(6, argv);
        CPPUNIT_ASSERT_EQUAL(5672, conf.getPort());//default
        CPPUNIT_ASSERT_EQUAL(string("blocking"), conf.getAcceptor());
        CPPUNIT_ASSERT_EQUAL(10, conf.getWorkerThreads());
        CPPUNIT_ASSERT(conf.isTrace());
        CPPUNIT_ASSERT(!conf.isHelp());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ConfigurationTest);

