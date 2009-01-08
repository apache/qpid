/*
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
 */
#include "qpid/framing/Blob.h"

#include "unit_test.h"

QPID_AUTO_TEST_SUITE(BlobTestSuite)

using namespace std;
using namespace qpid::framing;

struct Base {
    int id;
    int magic;

    Base(int n) : id(n), magic(42) {}
    Base(const Base& c) : id(c.id), magic(42) {}
    ~Base() { BOOST_CHECK_EQUAL(42, magic); } // Detect random data. 
};

template <class T> struct Count : public Base {
    static int instances;
    bool destroyed;
    
    Count(int n) : Base(n), destroyed(false) { ++instances; }
    Count(const Count& c) : Base(c), destroyed(false) { ++instances; }
    ~Count() {
        BOOST_CHECK(!destroyed); // Detect double-destructor
        destroyed=true;
        BOOST_CHECK(--instances >= 0);
    }
};

template <class T> int Count<T>::instances = 0;

struct Foo : public Count<Foo> { Foo(int n) : Count<Foo>(n) {}; };
struct Bar : public Count<Bar> { Bar(int n) : Count<Bar>(n) {}; };

typedef Blob<sizeof(Foo), Base> TestBlob ;

QPID_AUTO_TEST_CASE(testBlobCtor) {
    {
        TestBlob empty;
        BOOST_CHECK(empty.empty());
        BOOST_CHECK(empty.get() == 0);

        TestBlob empty2(empty);
        BOOST_CHECK(empty2.empty());

        TestBlob foo(in_place<Foo>(1));
        BOOST_CHECK(!foo.empty());
        BOOST_CHECK_EQUAL(1, foo.get()->id);
        BOOST_CHECK_EQUAL(1, Foo::instances);

        TestBlob foo2(foo);
        BOOST_CHECK(!foo2.empty());
        BOOST_CHECK_EQUAL(1, foo2.get()->id);
        BOOST_CHECK_EQUAL(2, Foo::instances);
    }
    
    BOOST_CHECK_EQUAL(0, Foo::instances);
    BOOST_CHECK_EQUAL(0, Bar::instances);
}


QPID_AUTO_TEST_CASE(testAssign) {
    {
        TestBlob b;
        b = Foo(2);
        BOOST_CHECK_EQUAL(2, b.get()->id);
        BOOST_CHECK_EQUAL(1, Foo::instances);

        TestBlob b2(b);
        BOOST_CHECK_EQUAL(2, b.get()->id);
        BOOST_CHECK_EQUAL(2, Foo::instances);
    
        b2 = Bar(3);
        BOOST_CHECK_EQUAL(3, b2.get()->id);
        BOOST_CHECK_EQUAL(1, Foo::instances);
        BOOST_CHECK_EQUAL(1, Bar::instances);

        b2 = in_place<Foo>(4); 
        BOOST_CHECK_EQUAL(4, b2.get()->id);
        BOOST_CHECK_EQUAL(2, Foo::instances);
        BOOST_CHECK_EQUAL(0, Bar::instances);

        b2.clear();
        BOOST_CHECK(b2.empty());
        BOOST_CHECK_EQUAL(1, Foo::instances);
    }
    BOOST_CHECK_EQUAL(0, Foo::instances);
    BOOST_CHECK_EQUAL(0, Bar::instances);
}


QPID_AUTO_TEST_CASE(testClear) {
    TestBlob b(in_place<Foo>(5));
    TestBlob c(b);
    BOOST_CHECK(!c.empty());
    BOOST_CHECK(!b.empty());
    BOOST_CHECK_EQUAL(2, Foo::instances);

    c.clear();
    BOOST_CHECK(c.empty());
    BOOST_CHECK_EQUAL(1, Foo::instances);

    b.clear(); 
    BOOST_CHECK(b.empty());
    BOOST_CHECK_EQUAL(0, Foo::instances);
}

QPID_AUTO_TEST_SUITE_END()
