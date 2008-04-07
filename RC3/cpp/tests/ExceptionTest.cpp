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
#include <Exception.h>
#include <qpid_test_plugin.h>

using namespace qpid;

struct CountDestroyedException : public Exception {
    int& count;
    static int staticCount;
    CountDestroyedException() : count(staticCount) { }
    CountDestroyedException(int& n) : count(n) {}
    ~CountDestroyedException() throw() { count++; }
    void throwSelf() const { throw *this; }
};

int CountDestroyedException::staticCount = 0;
         
        
class ExceptionTest : public CppUnit::TestCase 
{
    CPPUNIT_TEST_SUITE(ExceptionTest);
    CPPUNIT_TEST(testHeapException);
    CPPUNIT_TEST_SUITE_END();
  public:
    // Verify proper memory management for heap-allocated exceptions.
    void testHeapException() {
        int count = 0;
        try {
            std::auto_ptr<Exception> p(
                new CountDestroyedException(count));
            p.release()->throwSelf();
            CPPUNIT_FAIL("Expected CountDestroyedException.");
        } catch (const CountDestroyedException& e) {
            CPPUNIT_ASSERT(&e.count == &count);
        }
        CPPUNIT_ASSERT_EQUAL(1, count);
    }
};

    
// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ExceptionTest);

