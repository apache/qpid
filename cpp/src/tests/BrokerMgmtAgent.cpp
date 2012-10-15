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
#include "MessagingFixture.h"
#include "qpid/management/Buffer.h"
#include "qpid/messaging/Message.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"

#include "qmf/org/apache/qpid/broker/mgmt/test/TestObject.h"

#include <iomanip>


using           qpid::management::Mutex;
using           qpid::management::Manageable;
using           qpid::management::Buffer;
using namespace qpid::messaging;
using namespace qpid::types;



namespace qpid {
namespace tests {

namespace _qmf = qmf::org::apache::qpid::broker::mgmt::test;
namespace {

typedef boost::shared_ptr<_qmf::TestObject> TestObjectPtr;
typedef std::vector<TestObjectPtr> TestObjectVector;

// Instantiates a broker and its internal management agent.  Provides
// factories for constructing Receivers for object indication messages.
//
class AgentFixture
{
    MessagingFixture *mFix;

  public:
    AgentFixture( unsigned int pubInterval=10,
                  bool qmfV2=false,
                  qpid::broker::Broker::Options opts = qpid::broker::Broker::Options())
    {
        opts.enableMgmt=true;
        opts.qmf2Support=qmfV2;
        opts.mgmtPubInterval=pubInterval;
        mFix = new MessagingFixture(opts, true);

        _qmf::TestObject::registerSelf(getBrokerAgent());
    };
    ~AgentFixture()
    {
        delete mFix;
    };
    ::qpid::management::ManagementAgent *getBrokerAgent() { return mFix->broker->getManagementAgent(); }
    Receiver createV1DataIndRcvr( const std::string package, const std::string klass )
    {
        return mFix->session.createReceiver(std::string("kqueue; {create: always, delete: always, "
                                                        "node: {type: queue, "
                                                        "x-bindings: [{exchange: qpid.management, "
                                                        "key: 'console.obj.1.0.")
                                            + package + std::string(".") + klass
                                            + std::string("'}]}}"));
    };
    Receiver createV2DataIndRcvr( const std::string package, const std::string klass )
    {
        std::string p(package);
        std::replace(p.begin(), p.end(), '.', '_');
        std::string k(klass);
        std::replace(k.begin(), k.end(), '.', '_');

        return mFix->session.createReceiver(std::string("kqueue; {create: always, delete: always, "
                                                        "node: {type: queue, "
                                                        "x-bindings: [{exchange: qmf.default.topic, "
                                                        "key: 'agent.ind.data.")
                                            + p + std::string(".") + k
                                            + std::string("'}]}}"));
    };
};


// A "management object" that supports the TestObject
//
class TestManageable : public qpid::management::Manageable
{
    management::ManagementObject::shared_ptr mgmtObj;
    const std::string key;
  public:
    TestManageable(management::ManagementAgent *agent, std::string _key)
        : key(_key)
    {
        _qmf::TestObject::shared_ptr tmp(new _qmf::TestObject(agent, this));

        // seed it with some default values...
        tmp->set_string1(key);
        tmp->set_bool1(true);
        qpid::types::Variant::Map vMap;
        vMap["one"] = qpid::types::Variant(1);
        vMap["two"] = qpid::types::Variant("two");
        vMap["three"] = qpid::types::Variant("whatever");
        tmp->set_map1(vMap);

        mgmtObj = tmp;
    };
    ~TestManageable() { mgmtObj.reset(); }
    management::ManagementObject::shared_ptr GetManagementObject() const { return mgmtObj; };
    static void validateTestObjectProperties(_qmf::TestObject& to)
    {
        // verify the default values are as expected.  We don't check 'string1',
        // as it is the object key, and is unique for each object (no default value).
        BOOST_CHECK(to.get_bool1() == true);
        BOOST_CHECK(to.get_map1().size() == 3);
        qpid::types::Variant::Map mappy = to.get_map1();
        BOOST_CHECK(1 == (unsigned int)mappy["one"]);
        BOOST_CHECK(mappy["two"].asString() == std::string("two"));
        BOOST_CHECK(mappy["three"].asString() == std::string("whatever"));
    };
};


// decode a V1 Content Indication message
//
void decodeV1ObjectUpdates(const Message& inMsg, TestObjectVector& objs, const size_t objLen)
{
    const size_t MAX_BUFFER_SIZE=65536;
    char tmp[MAX_BUFFER_SIZE];

    objs.clear();

    BOOST_CHECK(inMsg.getContent().size() <= MAX_BUFFER_SIZE);

    ::memcpy(tmp, inMsg.getContent().data(), inMsg.getContent().size());
    Buffer buf(tmp, inMsg.getContent().size());

    while (buf.available() > 8) {     // 8 == qmf v1 header size
        BOOST_CHECK_EQUAL(buf.getOctet(), 'A');
        BOOST_CHECK_EQUAL(buf.getOctet(), 'M');
        BOOST_CHECK_EQUAL(buf.getOctet(), '2');
        BOOST_CHECK_EQUAL(buf.getOctet(), 'c'); // opcode == content indication
        // @@todo: kag: how do we skip 'i' entries???
        buf.getLong();  // ignore sequence

        std::string str1;                   // decode content body as string
        buf.getRawData(str1, objLen);

        TestObjectPtr fake(new _qmf::TestObject(0,0));
        fake->readProperties( str1 );
        objs.push_back(fake);
    }
}


// decode a V2 Content Indication message
//
void decodeV2ObjectUpdates(const qpid::messaging::Message& inMsg, TestObjectVector& objs)
{
    objs.clear();

    BOOST_CHECK_EQUAL(inMsg.getContentType(), std::string("amqp/list"));

    const ::qpid::types::Variant::Map& m = inMsg.getProperties();
    Variant::Map::const_iterator iter = m.find(std::string("qmf.opcode"));
    BOOST_CHECK(iter != m.end());
    BOOST_CHECK_EQUAL(iter->second.asString(), std::string("_data_indication"));

    Variant::List vList;
    ::qpid::amqp_0_10::ListCodec::decode(inMsg.getContent(), vList);

    for (Variant::List::iterator lIter = vList.begin(); lIter != vList.end(); lIter++) {
        TestObjectPtr fake(new _qmf::TestObject(0,0));
        fake->readTimestamps(lIter->asMap());
        fake->mapDecodeValues((lIter->asMap())["_values"].asMap());
        objs.push_back(fake);
    }
}
}

QPID_AUTO_TEST_SUITE(BrokerMgmtAgent)

// verify that an object that is added to the broker's management database is
// published correctly.  Furthermore, verify that it is published once after
// it has been deleted.
//
QPID_AUTO_TEST_CASE(v1ObjPublish)
{
    AgentFixture* fix = new AgentFixture(3);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    // create a manageable test object
    TestManageable *tm = new TestManageable(agent, std::string("obj1"));
    uint32_t objLen = tm->GetManagementObject()->writePropertiesSize();

    Receiver r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");

    agent->addObject(tm->GetManagementObject(), 1);

    // wait for the object to be published
    Message m1;
    BOOST_CHECK(r1.fetch(m1, Duration::SECOND * 6));

    TestObjectVector objs;
    decodeV1ObjectUpdates(m1, objs, objLen);
    BOOST_CHECK(objs.size() > 0);

    for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

        TestManageable::validateTestObjectProperties(**oIter);

        qpid::types::Variant::Map mappy;
        (*oIter)->writeTimestamps(mappy);
        BOOST_CHECK(0 == mappy["_delete_ts"].asUint64());   // not deleted
    }

    // destroy the object

    tm->GetManagementObject()->resourceDestroy();

    // wait for the deleted object to be published

    bool isDeleted = false;
    while (!isDeleted && r1.fetch(m1, Duration::SECOND * 6)) {

        decodeV1ObjectUpdates(m1, objs, objLen);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                isDeleted = true;
        }
    }

    BOOST_CHECK(isDeleted);

    r1.close();
    delete fix;
    delete tm;
}

// Repeat the previous test, but with V2-based object support
//
QPID_AUTO_TEST_CASE(v2ObjPublish)
{
    AgentFixture* fix = new AgentFixture(3, true);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    TestManageable *tm = new TestManageable(agent, std::string("obj2"));

    Receiver r1 = fix->createV2DataIndRcvr(tm->GetManagementObject()->getPackageName(), "#");

    agent->addObject(tm->GetManagementObject(), "testobj-1");

    // wait for the object to be published
    Message m1;
    BOOST_CHECK(r1.fetch(m1, Duration::SECOND * 6));

    TestObjectVector objs;
    decodeV2ObjectUpdates(m1, objs);
    BOOST_CHECK(objs.size() > 0);

    for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

        TestManageable::validateTestObjectProperties(**oIter);

        qpid::types::Variant::Map mappy;
        (*oIter)->writeTimestamps(mappy);
        BOOST_CHECK(0 == mappy["_delete_ts"].asUint64());
    }

    // destroy the object

    tm->GetManagementObject()->resourceDestroy();

    // wait for the deleted object to be published

    bool isDeleted = false;
    while (!isDeleted && r1.fetch(m1, Duration::SECOND * 6)) {

        decodeV2ObjectUpdates(m1, objs);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                isDeleted = true;
        }
    }

    BOOST_CHECK(isDeleted);

    r1.close();
    delete fix;
    delete tm;
}


// verify that a deleted object is exported correctly using the
// exportDeletedObjects() method.  V1 testcase.
//
QPID_AUTO_TEST_CASE(v1ExportDelObj)
{
    AgentFixture* fix = new AgentFixture(3);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    // create a manageable test object
    TestManageable *tm = new TestManageable(agent, std::string("myObj"));
    uint32_t objLen = tm->GetManagementObject()->writePropertiesSize();

    Receiver r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");

    agent->addObject(tm->GetManagementObject(), 1);

    // wait for the object to be published
    Message m1;
    BOOST_CHECK(r1.fetch(m1, Duration::SECOND * 6));

    TestObjectVector objs;
    decodeV1ObjectUpdates(m1, objs, objLen);
    BOOST_CHECK(objs.size() > 0);

    // destroy the object, then immediately export (before the next poll cycle)

    ::qpid::management::ManagementAgent::DeletedObjectList delObjs;
    tm->GetManagementObject()->resourceDestroy();
    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 1);

    // wait for the deleted object to be published

    bool isDeleted = false;
    while (!isDeleted && r1.fetch(m1, Duration::SECOND * 6)) {

        decodeV1ObjectUpdates(m1, objs, objLen);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                isDeleted = true;
        }
    }

    BOOST_CHECK(isDeleted);

    // verify there are no deleted objects to export now.

    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 0);

    r1.close();
    delete fix;
    delete tm;
}


// verify that a deleted object is imported correctly using the
// importDeletedObjects() method.  V1 testcase.
//
QPID_AUTO_TEST_CASE(v1ImportDelObj)
{
    AgentFixture* fix = new AgentFixture(3);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    // create a manageable test object
    TestManageable *tm = new TestManageable(agent, std::string("anObj"));
    uint32_t objLen = tm->GetManagementObject()->writePropertiesSize();

    Receiver r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");

    agent->addObject(tm->GetManagementObject(), 1);

    // wait for the object to be published
    Message m1;
    BOOST_CHECK(r1.fetch(m1, Duration::SECOND * 6));

    TestObjectVector objs;
    decodeV1ObjectUpdates(m1, objs, objLen);
    BOOST_CHECK(objs.size() > 0);

    // destroy the object, then immediately export (before the next poll cycle)

    ::qpid::management::ManagementAgent::DeletedObjectList delObjs;
    tm->GetManagementObject()->resourceDestroy();
    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 1);

    // destroy the broker, and reinistantiate a new one without populating it
    // with a TestObject.

    r1.close();
    delete fix;
    delete tm;    // should no longer be necessary

    fix = new AgentFixture(3);
    r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");
    agent = fix->getBrokerAgent();
    agent->importDeletedObjects( delObjs );

    // wait for the deleted object to be published

    bool isDeleted = false;
    while (!isDeleted && r1.fetch(m1, Duration::SECOND * 6)) {

        decodeV1ObjectUpdates(m1, objs, objLen);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                isDeleted = true;
        }
    }

    BOOST_CHECK(isDeleted);

    // verify there are no deleted objects to export now.

    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 0);

    r1.close();
    delete fix;
}


// verify that an object that is added and deleted prior to the
// first poll cycle is accounted for by the export
//
QPID_AUTO_TEST_CASE(v1ExportFastDelObj)
{
    AgentFixture* fix = new AgentFixture(3);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    // create a manageable test object
    TestManageable *tm = new TestManageable(agent, std::string("objectifyMe"));

    // add, then immediately delete and export the object...

    ::qpid::management::ManagementAgent::DeletedObjectList delObjs;
    agent->addObject(tm->GetManagementObject(), 999);
    tm->GetManagementObject()->resourceDestroy();
    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 1);

    delete fix;
    delete tm;
}


// Verify that we can export and import multiple deleted objects correctly.
//
QPID_AUTO_TEST_CASE(v1ImportMultiDelObj)
{
    AgentFixture* fix = new AgentFixture(3);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    Receiver r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");

    // populate the agent with multiple test objects
    const size_t objCount = 50;
    std::vector<TestManageable *> tmv;
    uint32_t objLen;

    for (size_t i = 0; i < objCount; i++) {
        std::stringstream key;
        key << "testobj-" << std::setfill('x') << std::setw(4) << i;
        // (no, seriously, I didn't just do that.)
        // Note well: we have to keep the key string length EXACTLY THE SAME
        // FOR ALL OBJECTS, so objLen will be the same.  Otherwise the
        // decodeV1ObjectUpdates() will fail (v1 lacks explict encoded length).
        TestManageable *tm = new TestManageable(agent, key.str());
        objLen = tm->GetManagementObject()->writePropertiesSize();
        agent->addObject(tm->GetManagementObject(), i + 1);
        tmv.push_back(tm);
    }

    // wait for the objects to be published
    Message m1;
    uint32_t    msgCount = 0;
    while(r1.fetch(m1, Duration::SECOND * 6)) {
        TestObjectVector objs;
        decodeV1ObjectUpdates(m1, objs, objLen);
        msgCount += objs.size();
    }

    BOOST_CHECK_EQUAL(msgCount, objCount);

    // destroy some of the objects, then immediately export (before the next poll cycle)

    uint32_t delCount = 0;
    for (size_t i = 0; i < objCount; i += 2) {
        tmv[i]->GetManagementObject()->resourceDestroy();
        delCount++;
    }

    ::qpid::management::ManagementAgent::DeletedObjectList delObjs;
    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK_EQUAL(delObjs.size(), delCount);

    // destroy the broker, and reinistantiate a new one without populating it
    // with TestObjects.

    r1.close();
    delete fix;
    while (tmv.size()) {
        delete tmv.back();
        tmv.pop_back();
    }

    fix = new AgentFixture(3);
    r1 = fix->createV1DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");
    agent = fix->getBrokerAgent();
    agent->importDeletedObjects( delObjs );

    // wait for the deleted object to be published, verify the count

    uint32_t countDels = 0;
    while (r1.fetch(m1, Duration::SECOND * 6)) {
        TestObjectVector objs;
        decodeV1ObjectUpdates(m1, objs, objLen);
        BOOST_CHECK(objs.size() > 0);

                
        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                countDels++;
        }
    }

    // make sure we get the correct # of deleted objects
    BOOST_CHECK_EQUAL(countDels, delCount);

    // verify there are no deleted objects to export now.

    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 0);

    r1.close();
    delete fix;
}

// Verify that we can export and import multiple deleted objects correctly.
// QMF V2 variant
QPID_AUTO_TEST_CASE(v2ImportMultiDelObj)
{
    AgentFixture* fix = new AgentFixture(3, true);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    Receiver r1 = fix->createV2DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");

    // populate the agent with multiple test objects
    const size_t objCount = 50;
    std::vector<TestManageable *> tmv;

    for (size_t i = 0; i < objCount; i++) {
        std::stringstream key;
        key << "testobj-" << i;
        TestManageable *tm = new TestManageable(agent, key.str());
        if (tm->GetManagementObject()->writePropertiesSize()) {}
        agent->addObject(tm->GetManagementObject(), key.str());
        tmv.push_back(tm);
    }

    // wait for the objects to be published
    Message m1;
    uint32_t    msgCount = 0;
    while(r1.fetch(m1, Duration::SECOND * 6)) {
        TestObjectVector objs;
        decodeV2ObjectUpdates(m1, objs);
        msgCount += objs.size();
    }

    BOOST_CHECK_EQUAL(msgCount, objCount);

    // destroy some of the objects, then immediately export (before the next poll cycle)

    uint32_t delCount = 0;
    for (size_t i = 0; i < objCount; i += 2) {
        tmv[i]->GetManagementObject()->resourceDestroy();
        delCount++;
    }

    ::qpid::management::ManagementAgent::DeletedObjectList delObjs;
    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK_EQUAL(delObjs.size(), delCount);

    // destroy the broker, and reinistantiate a new one without populating it
    // with TestObjects.

    r1.close();
    delete fix;
    while (tmv.size()) {
        delete tmv.back();
        tmv.pop_back();
    }

    fix = new AgentFixture(3, true);
    r1 = fix->createV2DataIndRcvr("org.apache.qpid.broker.mgmt.test", "#");
    agent = fix->getBrokerAgent();
    agent->importDeletedObjects( delObjs );

    // wait for the deleted object to be published, verify the count

    uint32_t countDels = 0;
    while (r1.fetch(m1, Duration::SECOND * 6)) {
        TestObjectVector objs;
        decodeV2ObjectUpdates(m1, objs);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {

            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0)
                countDels++;
        }
    }

    // make sure we get the correct # of deleted objects
    BOOST_CHECK_EQUAL(countDels, delCount);

    // verify there are no deleted objects to export now.

    agent->exportDeletedObjects( delObjs );
    BOOST_CHECK(delObjs.size() == 0);

    r1.close();
    delete fix;
}

// See QPID-2997
QPID_AUTO_TEST_CASE(v2RapidRestoreObj)
{
    AgentFixture* fix = new AgentFixture(3, true);
    management::ManagementAgent* agent;
    agent = fix->getBrokerAgent();

    // two objects, same ObjID
    TestManageable *tm1 = new TestManageable(agent, std::string("obj2"));
    TestManageable *tm2 = new TestManageable(agent, std::string("obj2"));

    Receiver r1 = fix->createV2DataIndRcvr(tm1->GetManagementObject()->getPackageName(), "#");

    // add, then immediately delete and re-add a copy of the object
    agent->addObject(tm1->GetManagementObject(), "testobj-1");
    tm1->GetManagementObject()->resourceDestroy();
    agent->addObject(tm2->GetManagementObject(), "testobj-1");

    // expect: a delete notification, then an update notification
    TestObjectVector objs;
    bool isDeleted = false;
    bool isAdvertised = false;
    size_t count = 0;
    Message m1;
    while (r1.fetch(m1, Duration::SECOND * 6)) {

        decodeV2ObjectUpdates(m1, objs);
        BOOST_CHECK(objs.size() > 0);

        for (TestObjectVector::iterator oIter = objs.begin(); oIter != objs.end(); oIter++) {
            count++;
            TestManageable::validateTestObjectProperties(**oIter);

            qpid::types::Variant::Map mappy;
            (*oIter)->writeTimestamps(mappy);
            if (mappy["_delete_ts"].asUint64() != 0) {
                isDeleted = true;
                BOOST_CHECK(isAdvertised == false);  // delete must be first
            } else {
                isAdvertised = true;
                BOOST_CHECK(isDeleted == true);     // delete must be first
            }
        }
    }

    BOOST_CHECK(isDeleted);
    BOOST_CHECK(isAdvertised);
    BOOST_CHECK(count == 2);

    r1.close();
    delete fix;
    delete tm1;
    delete tm2;
}

QPID_AUTO_TEST_SUITE_END()
}
}


