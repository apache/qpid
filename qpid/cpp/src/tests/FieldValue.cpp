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
#include "qpid/framing/FieldValue.h"

#include "unit_test.h"
#include <boost/test/floating_point_comparison.hpp>

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(FieldValueTestSuite)

using namespace qpid::framing;

Str16Value s("abc");
IntegerValue i(42);
FloatValue f((float)42.42);
DoubleValue df(123.123);

QPID_AUTO_TEST_CASE(testStr16ValueEquals)
{

    BOOST_CHECK(Str16Value("abc") == s);
    BOOST_CHECK(Str16Value("foo") != s);
    BOOST_CHECK(s != i);
    BOOST_CHECK(s.convertsTo<std::string>() == true);
    BOOST_CHECK(s.convertsTo<int>() == false);
    BOOST_CHECK(s.get<std::string>() == "abc");
    BOOST_CHECK_THROW(s.get<int>(), InvalidConversionException);

}

QPID_AUTO_TEST_CASE(testIntegerValueEquals)
{
    BOOST_CHECK(i.get<int>() == 42);
    BOOST_CHECK(IntegerValue(42) == i);
    BOOST_CHECK(IntegerValue(5) != i);
    BOOST_CHECK(i != s);
    BOOST_CHECK(i.convertsTo<std::string>() == false);
    BOOST_CHECK(i.convertsTo<float>() == true);
    BOOST_CHECK(i.convertsTo<int>() == true);
    BOOST_CHECK_THROW(i.get<std::string>(), InvalidConversionException);
    BOOST_CHECK_EQUAL(i.get<float>(), 42.0);
}

QPID_AUTO_TEST_CASE(testFloatValueEquals)
{
    BOOST_CHECK(f.convertsTo<float>() == true);
    BOOST_CHECK(FloatValue((float)42.42) == f);
    BOOST_CHECK_CLOSE(double(f.get<float>()), 42.42, 0.001);
    // Check twice, regression test for QPID-6470 where the value was corrupted during get.
    BOOST_CHECK(FloatValue((float)42.42) == f);
    BOOST_CHECK_CLOSE(f.get<double>(), 42.42, 0.001);

    // Float to double conversion
    BOOST_CHECK(f.convertsTo<double>() == true);
    BOOST_CHECK_CLOSE(f.get<double>(), 42.42, 0.001);

    // Double value
    BOOST_CHECK(f.convertsTo<float>() == true);
    BOOST_CHECK(f.convertsTo<double>() == true);
    BOOST_CHECK_CLOSE(double(df.get<float>()), 123.123, 0.001);
    BOOST_CHECK_CLOSE(df.get<double>(), 123.123, 0.001);

    // Invalid conversions should fail.
    BOOST_CHECK(!f.convertsTo<std::string>());
    BOOST_CHECK(!f.convertsTo<int>());
    BOOST_CHECK_THROW(f.get<std::string>(), InvalidConversionException);
    BOOST_CHECK_THROW(f.get<int>(), InvalidConversionException);

    // getFloatingPointValue: check twice, regression test for QPID-6470
    BOOST_CHECK_CLOSE((double(f.getFloatingPointValue<float,sizeof(float)>())), 42.42, 0.001);
    BOOST_CHECK_CLOSE((double(f.getFloatingPointValue<float,sizeof(float)>())), 42.42, 0.001);
    BOOST_CHECK_CLOSE((df.getFloatingPointValue<double,sizeof(double)>()), 123.123, 0.001);
    // getFloatingPointValue should *not* convert float/double, require exact type.
    BOOST_CHECK_THROW((f.getFloatingPointValue<double,sizeof(double)>()), InvalidConversionException);
    BOOST_CHECK_THROW((double(df.getFloatingPointValue<float,sizeof(float)>())), InvalidConversionException);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
