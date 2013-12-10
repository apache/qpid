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

#include "../unit_test.h"

#include <cstring>
#include <iostream>
#include "qpid/legacystore/jrnl/jerrno.h"

using namespace boost::unit_test;
using namespace mrg::journal;
using namespace std;

QPID_AUTO_TEST_SUITE(jerrno_suite)
using namespace mrg::journal;

const string test_filename("_ut_jerrno");

QPID_AUTO_TEST_CASE(jerrno_val)
{
    cout << test_filename << ".jerrno_val: " << flush;
    const char* m = "JERR__MALLOC";
    string malloc_msg = string(jerrno::err_msg(jerrno::JERR__MALLOC));
    BOOST_CHECK(malloc_msg.substr(0, std::strlen(m)).compare(m) == 0);
    BOOST_CHECK(std::strcmp(jerrno::err_msg(0), "<Unknown error code>") == 0);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
