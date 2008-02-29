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
#include "qpid/amqp_0_10/built_in_types.h"
#include "qpid/amqp_0_10/Codec.h"
#include <boost/test/test_case_template.hpp>
#include <boost/type_traits/is_arithmetic.hpp>
#include <boost/utility/enable_if.hpp>
#include <boost/mpl/vector.hpp>
#include <boost/mpl/back_inserter.hpp>
#include <boost/mpl/copy.hpp>
#include <boost/mpl/empty_sequence.hpp>
#include <iterator>
#include <string>
#include <iostream>
#include <netinet/in.h>

// Missing operators needed for tests.
namespace boost {
template <class T, size_t N>
std::ostream& operator<<(std::ostream& out, const array<T,N>& a) {
    std::ostream_iterator<T> o(out, " ");
    std::copy(a.begin(), a.end(), o);
    return out;
}
} // boost

namespace qpid {
namespace sys {

std::ostream& operator<<(std::ostream& out, const AbsTime& t) {
    return out << t.timeValue();
}
}

} // qpid


QPID_AUTO_TEST_SUITE(SerializeTestSuite)



using namespace std;
namespace mpl=boost::mpl;
using namespace qpid::amqp_0_10;

template <class A, class B> struct concat2 { typedef typename mpl::copy<B, typename mpl::back_inserter<A> >::type type; };
template <class A, class B, class C> struct concat3 { typedef typename concat2<A, typename concat2<B, C>::type>::type type; };
template <class A, class B, class C, class D> struct concat4 { typedef typename concat2<A, typename concat3<B, C, D>::type>::type type; };

typedef mpl::vector<Bit, Boolean, Char, Int32, Int64, Int8, Uint16, CharUtf32, Uint32, Uint64, Bin8, Uint8>::type IntegralTypes;
typedef mpl::vector<Bin1024, Bin128, Bin16, Bin256, Bin32, Bin40, Bin512, Bin64, Bin72>::type BinTypes;
typedef mpl::vector<Double, Float>::type FloatTypes;
typedef mpl::vector<SequenceNo, Uuid, Datetime, Dec32, Dec64> FixedSizeClassTypes;
typedef mpl::vector<Vbin8, Str8Latin, Str8, Str8Utf16, Vbin16, Str16Latin, Str16, Str16Utf16, Vbin32> VariableSizeTypes;


typedef concat4<IntegralTypes, BinTypes, FloatTypes, FixedSizeClassTypes>::type FixedSizeTypes;
typedef concat2<FixedSizeTypes, VariableSizeTypes>::type AllTypes;

// TODO aconway 2008-02-20: should test 64 bit integrals for order also.
BOOST_AUTO_TEST_CASE(testNetworkByteOrder) {
    string data;

    uint32_t l = 0x11223344;
    Codec::encode(std::back_inserter(data), l);
    uint32_t enc=reinterpret_cast<const uint32_t&>(*data.data());
    uint32_t l2 = ntohl(enc);
    BOOST_CHECK_EQUAL(l, l2);

    data.clear();
    uint16_t s = 0x1122;
    Codec::encode(std::back_inserter(data), s);
    uint32_t s2 = ntohs(*reinterpret_cast<const uint32_t*>(data.data()));
    BOOST_CHECK_EQUAL(s, s2);
}

// Assign test values to the various types.
void testValue(bool& b) { b = true; }
template <class T> typename boost::enable_if<boost::is_arithmetic<T> >::type testValue(T& n) { n=42; }
void testValue(long long& l) { l = 0x012345; }
void testValue(Datetime& dt) { dt = qpid::sys::now(); }
void testValue(Uuid& uuid) { uuid=Uuid(true); }
template <class E, class M> void testValue(Decimal<E,M>& d) { d.exponent=2; d.mantissa=0x1122; }
void testValue(SequenceNo& s) { s = 42; }
template <size_t N> void testValue(Bin<N>& a) { a.assign(42); }
template <class T, class S> void testValue(SerializableString<T, S>& s) {
    char msg[]="foobar";
    s.assign(msg, msg+sizeof(msg));
}
void testValue(Str16& s) { s = "the quick brown fox jumped over the lazy dog"; }
void testValue(Str8& s) { s = "foobar"; }

//typedef mpl::vector<Str8, Str16>::type TestTypes;
BOOST_AUTO_TEST_CASE_TEMPLATE(testEncodeDecode, T, AllTypes)
{
    string data;
    T t;
    testValue(t);
    Codec::encode(std::back_inserter(data), t);

    BOOST_CHECK_EQUAL(Codec::size(t), data.size());

    T t2;
    Codec::decode(data.begin(), t2);
    BOOST_CHECK_EQUAL(t,t2);
}


QPID_AUTO_TEST_SUITE_END()
