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
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"

#define BOOST_AUTO_TEST_MAIN
#include <boost/test/auto_unit_test.hpp>

using namespace qpid::framing;

BOOST_AUTO_TEST_CASE(testMe)
{
    FieldTable ft;
    ft.setString("A", "BCDE");
    BOOST_CHECK(StringValue("BCDE") == *ft.get("A"));

    char buff[100];
    Buffer wbuffer(buff, 100);
    wbuffer.put(ft);

    Buffer rbuffer(buff, 100);
    FieldTable ft2;
    rbuffer.get(ft2);
    BOOST_CHECK(StringValue("BCDE") == *ft2.get("A"));

}

BOOST_AUTO_TEST_CASE(testAssignment)
{
    FieldTable a;
    FieldTable b;

    a.setString("A", "BBBB");
    a.setInt("B", 1234);
    b = a;
    a.setString("A", "CCCC");
    
    BOOST_CHECK(StringValue("CCCC") == *a.get("A"));
    BOOST_CHECK(StringValue("BBBB") == *b.get("A"));
    BOOST_CHECK_EQUAL(1234, a.getInt("B"));
    BOOST_CHECK_EQUAL(1234, b.getInt("B"));
    BOOST_CHECK(IntegerValue(1234) == *a.get("B"));
    BOOST_CHECK(IntegerValue(1234) == *b.get("B"));

    FieldTable d;
    {
        FieldTable c;
        c = a;
        
        char* buff = static_cast<char*>(::alloca(c.size()));
        Buffer wbuffer(buff, c.size());
        wbuffer.put(c);

        Buffer rbuffer(buff, c.size());
        rbuffer.get(d);
        BOOST_CHECK_EQUAL(c, d);
        BOOST_CHECK(StringValue("CCCC") == *c.get("A"));
        BOOST_CHECK(IntegerValue(1234) == *c.get("B"));
    }
    BOOST_CHECK(StringValue("CCCC") == *d.get("A"));
    BOOST_CHECK(IntegerValue(1234) == *d.get("B"));
}



