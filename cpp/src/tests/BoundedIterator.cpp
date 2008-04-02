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
#include "unit_test.h"
#include "qpid/BoundedIterator.h"

using namespace std;
using namespace qpid;

QPID_AUTO_TEST_SUITE(BoundedIteratorTestSuite)

BOOST_AUTO_TEST_CASE(testIncrement) {
    const char* b="ab";
    const char* e=b+strlen(b);
    BoundedIterator<const char*> i(b,e);
    BOOST_CHECK_EQUAL('a', *i);
    ++i;
    BOOST_CHECK_EQUAL('b', *i);
    ++i;
    BOOST_CHECK(static_cast<const char*>(i) == e);
    try {
        ++i;
        BOOST_FAIL("Expected exception");
    }
    catch (const OutOfBoundsException&) {}
}

BOOST_AUTO_TEST_CASE(testAdvance) {
    const char* b="abc";
    const char* e=b+strlen(b);
    BoundedIterator<const char*> i(b,e);
    i += 2;
    BOOST_CHECK_EQUAL('c', *i);
    try {
        i += 2;
        BOOST_FAIL("Expected exception");
    }
    catch (const OutOfBoundsException&) {}
}

BOOST_AUTO_TEST_CASE(testDeref) {
    const char* b="ab";
    const char* e=b+strlen(b);
    BoundedIterator<const char*> i(b,e);
    i += 2;
    BOOST_CHECK(static_cast<const char*>(i) == e);
    try {
        (void)*i;
        BOOST_FAIL("Expected exception");
    }
    catch (const OutOfBoundsException&) {}
}




QPID_AUTO_TEST_SUITE_END()
