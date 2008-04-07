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
#include "qpid/amqp_0_10/Packer.h"
#include "qpid/amqp_0_10/built_in_types.h"
#include "qpid/amqp_0_10/Codec.h"
#include "qpid/amqp_0_10/specification.h"
#include "qpid/amqp_0_10/ControlHolder.h"
#include "qpid/amqp_0_10/Frame.h"
#include "qpid/amqp_0_10/Map.h"

#include <boost/test/test_case_template.hpp>
#include <boost/type_traits/is_arithmetic.hpp>
#include <boost/utility/enable_if.hpp>
#include <boost/optional.hpp>
#include <boost/mpl/vector.hpp>
#include <boost/mpl/back_inserter.hpp>
#include <boost/mpl/copy.hpp>
#include <boost/mpl/empty_sequence.hpp>
#include <iterator>
#include <string>
#include <sstream>
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
using qpid::framing::in_place;

template <class A, class B> struct concat2 { typedef typename mpl::copy<B, typename mpl::back_inserter<A> >::type type; };
template <class A, class B, class C> struct concat3 { typedef typename concat2<A, typename concat2<B, C>::type>::type type; };
template <class A, class B, class C, class D> struct concat4 { typedef typename concat2<A, typename concat3<B, C, D>::type>::type type; };

typedef mpl::vector<Boolean, Char, Int32, Int64, Int8, Uint16, CharUtf32, Uint32, Uint64, Bin8, Uint8>::type IntegralTypes;
typedef mpl::vector<Bin1024, Bin128, Bin16, Bin256, Bin32, Bin40, Bin512, Bin64, Bin72>::type BinTypes;
typedef mpl::vector<Double, Float>::type FloatTypes;
typedef mpl::vector<SequenceNo, Uuid, Datetime, Dec32, Dec64> FixedSizeClassTypes;
typedef mpl::vector<Map, Vbin8, Str8Latin, Str8, Str8Utf16, Vbin16, Str16Latin, Str16, Str16Utf16, Vbin32> VariableSizeTypes;

typedef concat4<IntegralTypes, BinTypes, FloatTypes, FixedSizeClassTypes>::type FixedSizeTypes;
typedef concat2<FixedSizeTypes, VariableSizeTypes>::type AllTypes;

// TODO aconway 2008-02-20: should test 64 bit integrals for order also.
BOOST_AUTO_TEST_CASE(testNetworkByteOrder) {
    string data;

    uint32_t l = 0x11223344;
    Codec::encode(std::back_inserter(data))(l);
    uint32_t enc=reinterpret_cast<const uint32_t&>(*data.data());
    uint32_t l2 = ntohl(enc);
    BOOST_CHECK_EQUAL(l, l2);

    data.clear();
    uint16_t s = 0x1122;
    Codec::encode(std::back_inserter(data))(s);
    uint32_t s2 = ntohs(*reinterpret_cast<const uint32_t*>(data.data()));
    BOOST_CHECK_EQUAL(s, s2);
}

BOOST_AUTO_TEST_CASE(testSetLimit) {
    typedef Codec::Encoder<back_insert_iterator<string> > Encoder;
    string data;
    Encoder encode(back_inserter(data), 3);
    encode('1')('2')('3');
    try {
        encode('4');
        BOOST_FAIL("Expected exception");
    } catch (...) {}            // FIXME aconway 2008-04-03: catch proper exception
    BOOST_CHECK_EQUAL(data, "123");
}

BOOST_AUTO_TEST_CASE(testScopedLimit) {
    typedef Codec::Encoder<back_insert_iterator<string> > Encoder;
    string data;
    Encoder encode(back_inserter(data), 10);
    encode(Str8("123"));        // 4 bytes
    {
        Encoder::ScopedLimit l(encode, 3);
        encode('a')('b')('c');
        try {
            encode('d');
            BOOST_FAIL("Expected exception");
        } catch(...) {}         // FIXME aconway 2008-04-03: catch proper exception
    }
    BOOST_CHECK_EQUAL(data, "\003123abc");
    encode('x')('y')('z');
    try {
        encode('!');
        BOOST_FAIL("Expected exception");
    } catch(...) {}         // FIXME aconway 2008-04-03: catch proper exception
    BOOST_CHECK_EQUAL(data.size(), 10u);
}

// Assign test values to the various types.
void testValue(bool& b) { b = true; }
void testValue(Bit&) { }
template <class T> typename boost::enable_if<boost::is_arithmetic<T> >::type testValue(T& n) { n=42; }
void testValue(CharUtf32& c) { c = 43; }
void testValue(long long& l) { l = 0x012345; }
void testValue(Datetime& dt) { dt = qpid::sys::now(); }
void testValue(Uuid& uuid) { uuid=Uuid(true); }
template <class E, class M> void testValue(Decimal<E,M>& d) { d.exponent=2; d.mantissa=0x1122; }
void testValue(SequenceNo& s) { s = 42; }
template <size_t N> void testValue(Bin<N>& a) { a.assign(42); }
template <class T, class S, int Unique> void testValue(SerializableString<T, S, Unique>& s) {
    char msg[]="foobar";
    s.assign(msg, msg+sizeof(msg));
}
void testValue(Str16& s) { s = "the quick brown fox jumped over the lazy dog"; }
void testValue(Str8& s) { s = "foobar"; }
void testValue(Map& m) { m["s"] = Str8("foobar"); m["b"] = true; m["c"] = uint16_t(42); }

//typedef mpl::vector<Str8, Str16>::type TestTypes;
BOOST_AUTO_TEST_CASE_TEMPLATE(testEncodeDecode, T, AllTypes)
{
    string data;
    T t;
    testValue(t);
    Codec::encode(std::back_inserter(data))(t);

    BOOST_CHECK_EQUAL(Codec::size(t), data.size());

    T t2;
    Codec::decode(data.begin())(t2);
    BOOST_CHECK_EQUAL(t,t2);
}

struct TestMe {
    bool encoded, decoded;
    char value;
    TestMe(char v) : encoded(), decoded(), value(v) {}
    template <class S> void encode(S& s) const {
        const_cast<TestMe*>(this)->encoded=true; s(value);
    }
    template <class S> void decode(S& s) { decoded=true; s(value); }
    template <class S> void serialize(S& s) { s.split(*this); }
};

BOOST_AUTO_TEST_CASE(testSplit) {
    string data;
    TestMe t1('x');
    Codec::encode(std::back_inserter(data))(t1);
    BOOST_CHECK(t1.encoded);
    BOOST_CHECK(!t1.decoded);
    BOOST_CHECK_EQUAL(data, "x");

    TestMe t2('y');
    Codec::decode(data.begin())(t2);
    BOOST_CHECK(!t2.encoded);
    BOOST_CHECK(t2.decoded);
    BOOST_CHECK_EQUAL(t2.value, 'x');
}

BOOST_AUTO_TEST_CASE(testControlEncodeDecode) {
    string data;
    Control::Holder h(in_place<connection::Tune>(1,2,3,4));
    Codec::encode(std::back_inserter(data))(h);
    
    BOOST_CHECK_EQUAL(data.size(), Codec::size(h));

    Codec::Decoder<string::iterator> decode(data.begin());
    Control::Holder h2;
    decode(h2);

    BOOST_REQUIRE(h2.get());
    BOOST_CHECK_EQUAL(h2.get()->getClassCode(), connection::CODE);
    BOOST_CHECK_EQUAL(h2.get()->getCode(), uint8_t(connection::Tune::CODE));
    connection::Tune& tune=static_cast<connection::Tune&>(*h2.get());
    BOOST_CHECK_EQUAL(tune.channelMax, 1u);
    BOOST_CHECK_EQUAL(tune.maxFrameSize, 2u);
    BOOST_CHECK_EQUAL(tune.heartbeatMin, 3u);
    BOOST_CHECK_EQUAL(tune.heartbeatMax, 4u);
}

BOOST_AUTO_TEST_CASE(testFrameEncodeDecode) {
    static const int overhead=12;
    string data;
    Frame r, c;
    char d1[]="abcdefg";
    r.refer(d1, d1+sizeof(d1));
    r.setFlags(Frame::FIRST_FRAME);
    r.setType(CONTROL);
    r.setChannel(32);
    r.setTrack(1);
    char d2[]="01234567";
    c.copy(d2, d2+sizeof(d2));

    BOOST_CHECK_EQUAL(overhead+sizeof(d1), Codec::size(r));
    BOOST_CHECK_EQUAL(overhead+sizeof(d2), Codec::size(c));
    Codec::encode(std::back_inserter(data))(r)(c);
    BOOST_CHECK_EQUAL(data.size(), Codec::size(r)+Codec::size(c));

    FrameHeader fh;
    std::string::iterator i = Codec::decode(data.begin())(fh).pos();
    size_t s = fh.size();
    BOOST_CHECK_EQUAL(s, sizeof(d1));
    BOOST_CHECK_EQUAL(std::string(i, i+s), std::string(d1, d1+s));

                      
    Frame f1, f2;
    Codec::decode(data.begin())(f1)(f2);
    BOOST_CHECK_EQUAL(f1.size(), sizeof(d1));
    BOOST_CHECK_EQUAL(std::string(f1.begin(), f1.size()),
                      std::string(d1, sizeof(d1)));
    BOOST_CHECK_EQUAL(f1.size(), r.size());
    BOOST_CHECK_EQUAL(f1.getFlags(), Frame::FIRST_FRAME);
    BOOST_CHECK_EQUAL(f1.getType(), CONTROL);
    BOOST_CHECK_EQUAL(f1.getChannel(), 32);
    BOOST_CHECK_EQUAL(f1.getTrack(), 1);

    BOOST_CHECK_EQUAL(f2.size(), c.size());
    BOOST_CHECK_EQUAL(std::string(f2.begin(), f2.end()),
                      std::string(d2, d2+sizeof(d2)));
    
}

struct DummyPacked {
    static const uint8_t PACK=1;
    boost::optional<char> i, j;
    char k;
    DummyPacked(char a=0, char b=0, char c=0) : i(a), j(b), k(c) {}
    template <class S> void serialize(S& s) { s(i)(j)(k); }

    string str() const {
        ostringstream os;
        os << i << j << k;
        return os.str();
    }
};

Packer<DummyPacked> serializable(DummyPacked& d) { return Packer<DummyPacked>(d); }

BOOST_AUTO_TEST_CASE(testPackBits) {
    DummyPacked d('a','b','c');
    BOOST_CHECK_EQUAL(packBits(d), 7u);
    d.j = boost::none;
    BOOST_CHECK_EQUAL(packBits(d), 5u);    
}


BOOST_AUTO_TEST_CASE(testPacked) {
    string data;

    Codec::encode(back_inserter(data))('a')(boost::optional<char>('b'))(boost::optional<char>())('c');
    BOOST_CHECK_EQUAL(data, "abc");
    data.clear();
    
    DummyPacked dummy('a','b','c');

    Codec::encode(back_inserter(data))(dummy);
    BOOST_CHECK_EQUAL(data.size(), 4u);
    BOOST_CHECK_EQUAL(data, string("\007abc"));
    data.clear();

    dummy.i = boost::none;
    Codec::encode(back_inserter(data))(dummy);
    BOOST_CHECK_EQUAL(data, string("\6bc"));
    data.clear();

    const char* missing = "\5xy";
    Codec::decode(missing)(dummy);
    BOOST_CHECK(dummy.i);
    BOOST_CHECK_EQUAL(*dummy.i, 'x');
    BOOST_CHECK(!dummy.j);
    BOOST_CHECK_EQUAL(dummy.k, 'y');
}

QPID_AUTO_TEST_SUITE_END()
