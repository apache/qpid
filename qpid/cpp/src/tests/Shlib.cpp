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
 *
 */

#include "test_tools.h"
#include "qpid/sys/Shlib.h"
#include "qpid/Exception.h"

#include "unit_test.h"

#ifndef WIN32
#  include <sys/stat.h>
#endif

QPID_AUTO_TEST_SUITE(ShlibTestSuite)

using namespace qpid::sys;
typedef void (*CallMe)(int*);

// Figure out the correct combination of tokens to use for a loadable
// library.
namespace {
  const char *assemble_name (const char *base)
  {
    static char full_name[1024];
#   if defined (WIN32)
    sprintf (full_name, "%s.dll", base);
#   else
    // If we're in a libtool environment, use that; else look here.
    struct stat s;
    if (stat(".libs", &s) == 0)
        sprintf (full_name, ".libs/lib%s.so", base);
    else
        sprintf (full_name, "./lib%s.so", base);
#   endif /* WIN32 */
    return full_name;
  }
}

QPID_AUTO_TEST_CASE(testShlib) {
    Shlib sh(assemble_name("shlibtest"));
    // Double cast to avoid ISO warning.
    CallMe callMe=sh.getSymbol<CallMe>("callMe");
    BOOST_REQUIRE(callMe != 0);
    int unloaded=0;
    callMe(&unloaded);
    sh.unload();
    BOOST_CHECK_EQUAL(42, unloaded);
    try {
        sh.getSymbol("callMe");
        BOOST_FAIL("Expected exception");
    }
    catch (const qpid::Exception&) {}
}
    
QPID_AUTO_TEST_CASE(testAutoShlib) {
    int unloaded = 0;
    {
        AutoShlib sh(assemble_name("shlibtest"));
        CallMe callMe=sh.getSymbol<CallMe>("callMe");
        BOOST_REQUIRE(callMe != 0);
        callMe(&unloaded);
    }
    BOOST_CHECK_EQUAL(42, unloaded);
}
    

QPID_AUTO_TEST_SUITE_END()
