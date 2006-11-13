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
#include <qpid/posix/EpollEventChannel.h>
#include <qpid_test_plugin.h>
#include <iostream>

using namespace qpid::sys;

class EpollEventChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(EpollEventChannelTest);
    CPPUNIT_TEST(testRead);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testRead() 
    {
        static const std::string msg("hello");
        static const ssize_t size = msg.size()+1;
        
        int p[2];
        CHECK0(pipe(p));

        char readBuf[size];
        ReadEvent re(p[0], readBuf, size);

        CPPUNIT_ASSERT_EQUAL(size, write(p[1], msg.c_str(), size));
        CPPUNIT_ASSERT_EQUAL(size, read(p[0], readBuf, size));
        CPPUNIT_ASSERT_EQUAL(msg, std::string(readBuf));

        EpollEventChannel ec;
        ec.post(re);
        
        
        ReadEvent* e = static_cast<ReadEvent*>(ec.getEvent());
        CPPUNIT_ASSERT_EQUAL(&re, e);

        CPPUNIT_ASSERT_EQUAL(msg, std::string(readBuf))
        
        CPPUNIT_FAIL("Unfinished - not using ec");
    }

    void testPartialRead()
    {
        CPPUNIT_FAIL("Partial reads: shoul EQ collect full message?");
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(EpollEventChannelTest);

