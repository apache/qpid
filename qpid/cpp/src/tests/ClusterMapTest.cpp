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


#include "unit_test.h"
#include "test_tools.h"
#include "qpid/cluster/ClusterMap.h"
#include "qpid/framing/ClusterMapBody.h"
#include "qpid/framing/Buffer.h"
#include "qpid/Url.h"
#include <boost/assign.hpp>

QPID_AUTO_TEST_SUITE(CluterMapTest)

using namespace std;
using namespace qpid;
using namespace cluster;
using namespace framing;

MemberId id(int i) { return MemberId(i,i); }

Url url(const char* host) { return Url(TcpAddress(host)); }

QPID_AUTO_TEST_CASE(testNotice) {
    ClusterMap m;
    BOOST_CHECK(!m.urlNotice(id(0), url("0-ready"))); // id(0) member, no dump.
    BOOST_CHECK(m.isMember(id(0)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);
    BOOST_CHECK_EQUAL(m.memberCount(), 1);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(1), url("1-dump"))); // Newbie, needs dump
    BOOST_CHECK(m.isMember(id(0)));
    BOOST_CHECK(m.isDumpee(id(1)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 1);
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 0);
    BOOST_CHECK_EQUAL(m.memberCount(), 1);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 1);

    BOOST_CHECK(!m.urlNotice(id(1), url("1-ready"))); // id(1) is ready.
    BOOST_CHECK(m.isMember(id(0)));
    BOOST_CHECK(m.isMember(id(1)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 0);
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(2), url("2-dump"))); // id(2) needs dump
    BOOST_CHECK(m.isDumpee(id(2)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 1);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 1);

    BOOST_CHECK_EQUAL(id(1), m.urlNotice(id(3), url("3-dump"))); // 0 busy, dump to id(1).
    BOOST_CHECK(m.isDumpee(id(3)));    
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 1);
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 1);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 2);

    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(4), url("4-dump"))); // Equally busy, 0 is first on list.
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 2);
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 1);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 3);    

    // My dumpees both complete
    BOOST_CHECK(!m.urlNotice(id(2), url("2-ready"))); 
    BOOST_CHECK(!m.urlNotice(id(4), url("4-ready"))); 
    BOOST_CHECK(m.isMember(id(2)));
    BOOST_CHECK(m.isMember(id(4)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);    
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 1);    
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 1);

    // Final dumpee completes.
    BOOST_CHECK(!m.urlNotice(id(3), url("3-ready")));
    BOOST_CHECK(m.isMember(id(3)));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);    
    BOOST_CHECK_EQUAL(m.dumps(id(1)), 0);    
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

}

QPID_AUTO_TEST_CASE(testLeave) {
    ClusterMap m;
    BOOST_CHECK(!m.urlNotice(id(0), url("0-ready")));
    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(1), url("1-dump")));
    BOOST_CHECK(!m.urlNotice(id(1), url("1-ready")));
    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(2), url("2-dump")));
    BOOST_CHECK(!m.urlNotice(id(2), url("2-ready")));
    BOOST_CHECK_EQUAL(m.memberCount(), 3);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);
    
    m.leave(id(1));
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);
    BOOST_CHECK(m.isMember(id(0)));
    BOOST_CHECK(m.isMember(id(2)));

    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(4), url("4-dump")));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 1);
    BOOST_CHECK(m.isDumpee(id(4)));
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 1);

    m.dumpFailed(id(4));        // Dumper detected a failure.
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);
    BOOST_CHECK(!m.isDumpee(id(4)));
    BOOST_CHECK(!m.isMember(id(4)));
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

    m.leave(id(4));             // Dumpee leaves, no-op since we already know it failed.
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

    BOOST_CHECK_EQUAL(id(0), m.urlNotice(id(5), url("5-dump")));
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 1);
    BOOST_CHECK(m.isDumpee(id(5)));
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 1);

    m.leave(id(5));             // Dumpee detects failure and leaves cluster.
    BOOST_CHECK_EQUAL(m.dumps(id(0)), 0);
    BOOST_CHECK(!m.isDumpee(id(5)));
    BOOST_CHECK(!m.isMember(id(5)));
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);

    m.dumpFailed(id(5));        // Dumper reports failure - no op, we already know.
    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 0);
}

QPID_AUTO_TEST_CASE(testToControl) {
    ClusterMap m;
    m.urlNotice(id(0), url("0"));
    m.urlNotice(id(1), url("1dump"));
    m.urlNotice(id(1), url("1"));
    m.urlNotice(id(2), url("2dump"));
    m.urlNotice(id(3), url("3dump"));
    m.urlNotice(id(4), url("4dump"));

    BOOST_CHECK_EQUAL(m.memberCount(), 2);
    BOOST_CHECK_EQUAL(m.dumpeeCount(), 3);

    ClusterMapBody b = m.toControl();

    BOOST_CHECK_EQUAL(b.getMembers().count(), 2);
    BOOST_CHECK_EQUAL(b.getMembers().getString(id(0).str()), url("0").str());
    BOOST_CHECK_EQUAL(b.getMembers().getString(id(1).str()), url("1").str());

    BOOST_CHECK_EQUAL(b.getDumpees().count(), 3);
    BOOST_CHECK_EQUAL(b.getDumpees().getString(id(2).str()), url("2dump").str());
    BOOST_CHECK_EQUAL(b.getDumpees().getString(id(3).str()), url("3dump").str());
    BOOST_CHECK_EQUAL(b.getDumpees().getString(id(4).str()), url("4dump").str());

    BOOST_CHECK_EQUAL(b.getDumps().count(), 3);
    BOOST_CHECK_EQUAL(b.getDumps().getString(id(2).str()), id(0).str());
    BOOST_CHECK_EQUAL(b.getDumps().getString(id(3).str()), id(1).str());
    BOOST_CHECK_EQUAL(b.getDumps().getString(id(4).str()), id(0).str());

    std::string s(b.size(), '\0');
    Buffer buf(&s[0], s.size());
    b.encode(buf);

    ClusterMap m2;
    m2.fromControl(b);
    ClusterMapBody b2 = m2.toControl();
    std::string s2(b2.size(), '\0');
    Buffer buf2(&s2[0], s2.size());
    b2.encode(buf2);

    // Verify a round-trip encoding produces identical results.
    BOOST_CHECK_EQUAL(s,s2);
}

QPID_AUTO_TEST_SUITE_END()
