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

#include "qpid/framing/Uuid.h"
#include "qpid/framing/Buffer.h"
#include "qpid/types/Uuid.h"

#include "unit_test.h"

#include <set>

#include <boost/array.hpp>

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(UuidTestSuite)

using namespace std;
using namespace qpid::framing;

struct UniqueSet : public std::set<Uuid> {
    void operator()(const Uuid& uuid) {
        BOOST_REQUIRE(find(uuid) == end());
        insert(uuid);
    }
};

QPID_AUTO_TEST_CASE(testUuidCtor) {
    // Uniqueness
    boost::array<Uuid,1000> uuids;
    for_each(uuids.begin(), uuids.end(), mem_fun_ref(&Uuid::generate));
    UniqueSet unique;
    for_each(uuids.begin(), uuids.end(), unique);
}

boost::array<uint8_t, 16>  sample =  {{0x1b, 0x4e, 0x28, 0xba, 0x2f, 0xa1, 0x11, 0x02, 0x88, 0x3f, 0xb9, 0xa7, 0x61, 0xbd, 0xe3, 0xfb}};
const string sampleStr("1b4e28ba-2fa1-1102-883f-b9a761bde3fb");
const string zeroStr("00000000-0000-0000-0000-000000000000");
const string badUuid1("1b4e28ba-2fa1-11d2-883f-b9761bde3fb");
const string badUuid2("1b4e28ba-2fa1-11d23883f-b9761dbde3fb");

QPID_AUTO_TEST_CASE(testUuidIstream) {
    Uuid uuid;
    istringstream in(sampleStr);
    in >> uuid;
    BOOST_CHECK(!in.fail());
    BOOST_CHECK(::memcmp(uuid.data(), sample.data(), uuid.size())==0);

    istringstream is(zeroStr);
    Uuid zero;
    is >> zero;
    BOOST_CHECK(!is.fail());
    BOOST_CHECK_EQUAL(zero, Uuid());
}

QPID_AUTO_TEST_CASE(testUuidOstream) {
    Uuid uuid(sample.c_array());
    ostringstream out;
    out << uuid;
    BOOST_CHECK(out.good());
    BOOST_CHECK_EQUAL(out.str(), sampleStr);

    ostringstream os;
    os << Uuid();
    BOOST_CHECK(out.good());
    BOOST_CHECK_EQUAL(os.str(), zeroStr);
}

QPID_AUTO_TEST_CASE(testBadUuidIstream) {
  Uuid a;
  istringstream is(badUuid1);
  is >> a;
  BOOST_CHECK(!is.good());
  istringstream is2(badUuid2);
  is2 >> a;
  BOOST_CHECK(!is2.good());
}

QPID_AUTO_TEST_CASE(testUuidIOstream) {
    Uuid a(true), b(true);
    ostringstream os;
    os << a << endl << b;
    Uuid aa, bb;
    istringstream is(os.str());
    is >> aa >> ws >> bb;
    BOOST_CHECK(os.good());
    BOOST_CHECK_EQUAL(a, aa);
    BOOST_CHECK_EQUAL(b, bb);
}

QPID_AUTO_TEST_CASE(testUuidEncodeDecode) {
    std::vector<char> buff(Uuid::size());
    Buffer wbuf(&buff[0], Uuid::size());
    Uuid uuid(sample.c_array());
    uuid.encode(wbuf);

    Buffer rbuf(&buff[0], Uuid::size());
    Uuid decoded;
    decoded.decode(rbuf);
    BOOST_CHECK_EQUAL(string(sample.begin(), sample.end()),
                      string(decoded.data(), decoded.data()+decoded.size()));
}

QPID_AUTO_TEST_CASE(testTypesUuid)
{
    //tests for the Uuid class in the types namespace (introduced
    //to avoid pulling in dependencies from framing)
    types::Uuid a;
    types::Uuid b(true);
    types::Uuid c(true);
    types::Uuid d(b);
    types::Uuid e;
    e = c;

    BOOST_CHECK(!a);
    BOOST_CHECK(b);

    BOOST_CHECK(a != b);
    BOOST_CHECK(b != c);

    BOOST_CHECK_EQUAL(b, d);
    BOOST_CHECK_EQUAL(c, e);

    ostringstream out;
    out << b;    
    istringstream in(out.str());
    in >> a;
    BOOST_CHECK(!in.fail());
    BOOST_CHECK_EQUAL(a, b);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
