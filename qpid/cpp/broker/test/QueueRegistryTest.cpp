#include "QueueRegistry.h"
#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>
#include <string>

using namespace qpid::broker;

class QueueRegistryTest : public CppUnit::TestCase 
{
    CPPUNIT_TEST_SUITE(QueueRegistryTest);
    CPPUNIT_TEST(testDeclare);
    CPPUNIT_TEST(testDeclareTmp);
    CPPUNIT_TEST(testFind);
    CPPUNIT_TEST(testDestroy);
    CPPUNIT_TEST_SUITE_END();

  private:
    std::string foo, bar;
    QueueRegistry reg;
    std::pair<Queue::shared_ptr,  bool> qc;
    
  public:
    void setUp() {
        foo = "foo";
        bar = "bar";
    }
    
    void testDeclare() {
        qc = reg.declare(foo, false, 0, 0);
        Queue::shared_ptr q = qc.first;
        CPPUNIT_ASSERT(q);
        CPPUNIT_ASSERT(qc.second); // New queue
        CPPUNIT_ASSERT_EQUAL(foo, q->getName());

        qc = reg.declare(foo, false, 0, 0);
        CPPUNIT_ASSERT_EQUAL(q, qc.first);
        CPPUNIT_ASSERT(!qc.second);

        qc = reg.declare(bar, false, 0, 0);
        q = qc.first;
        CPPUNIT_ASSERT(q);
        CPPUNIT_ASSERT_EQUAL(true, qc.second);
        CPPUNIT_ASSERT_EQUAL(bar, q->getName());
    }

    void testDeclareTmp() 
    {
        qc = reg.declare(std::string(), false, 0, 0);
        CPPUNIT_ASSERT(qc.second);
        CPPUNIT_ASSERT_EQUAL(std::string("tmp_1"), qc.first->getName());
    }
    
    void testFind() {
        CPPUNIT_ASSERT(reg.find(foo) == 0);

        reg.declare(foo, false, 0, 0);
        reg.declare(bar, false, 0, 0);
        Queue::shared_ptr q = reg.find(bar);
        CPPUNIT_ASSERT(q);
        CPPUNIT_ASSERT_EQUAL(bar, q->getName());
    }

    void testDestroy() {
        qc = reg.declare(foo, false, 0, 0);
        reg.destroy(foo);
        // Queue is gone from the registry.
        CPPUNIT_ASSERT(reg.find(foo) == 0);
        // Queue is not actually destroyed till we drop our reference.
        CPPUNIT_ASSERT_EQUAL(foo, qc.first->getName());
        // We shoud be the only reference.
        CPPUNIT_ASSERT_EQUAL(1L, qc.first.use_count());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(QueueRegistryTest);
