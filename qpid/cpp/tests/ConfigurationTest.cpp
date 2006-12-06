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
#include <Configuration.h>
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
    CPPUNIT_TEST(testStore);
    CPPUNIT_TEST(testStagingThreshold);
    CPPUNIT_TEST(testVarious);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testIsHelp() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--help"};
        conf.parse("ignore", 2, argv);
        CPPUNIT_ASSERT(conf.isHelp());
    }

    void testPortLongForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--port", "6789"};
        conf.parse("ignore", 3, argv);
        CPPUNIT_ASSERT_EQUAL(6789, conf.getPort());
    }

    void testPortShortForm() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "-p", "6789"};
        conf.parse("ignore", 3, argv);
        CPPUNIT_ASSERT_EQUAL(6789, conf.getPort());
    }

    void testStore() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--store", "my-store-module.so"};
        conf.parse("ignore", 3, argv);
        std::string expected("my-store-module.so");
        CPPUNIT_ASSERT_EQUAL(expected, conf.getStore());
    }

    void testStagingThreshold() 
    {
        Configuration conf;
        char* argv[] = {"ignore", "--staging-threshold", "123456789"};
        conf.parse("ignore", 3, argv);
        long expected = 123456789;
        CPPUNIT_ASSERT_EQUAL(expected, conf.getStagingThreshold());
    }

    void testVarious() 
    {        
        Configuration conf;
        char* argv[] = {"ignore", "-t", "--worker-threads", "10"};
        conf.parse("ignore", 4, argv);
        CPPUNIT_ASSERT_EQUAL(5672, conf.getPort());//default
        CPPUNIT_ASSERT_EQUAL(10, conf.getWorkerThreads());
        CPPUNIT_ASSERT(conf.isTrace());
        CPPUNIT_ASSERT(!conf.isHelp());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ConfigurationTest);

