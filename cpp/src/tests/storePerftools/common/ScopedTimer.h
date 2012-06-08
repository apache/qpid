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

/**
 * \file ScopedTimer.h
 */

#ifndef tests_storePerftools_common_ScopedTimer_h_
#define tests_storePerftools_common_ScopedTimer_h_

#include <ctime>

namespace tests {
namespace storePerftools {
namespace common {

class ScopedTimable;

/**
 * \brief Scoped timer class that starts timing on construction and finishes on destruction.
 *
 * The scoped timer will take the current time on construction and again on destruction. The destructor
 * will calculate the elapsed time from the difference between these two times and write the result
 * as a double to the double ref supplied to the constructor. A second constructor will accept a class (or
 * subclass) of ScopedTimable, which contains a double to which the result may be written and accessed at a
 * later time.
 */
class ScopedTimer
{
public:
    /**
     * \brief Constructor
     *
     * Constructor which accepts a ref to a double. Will start the time interval measurement.
     *
     * \param elapsed A ref to a double which will contain the elapsed time in seconds after this class instance
     *                is destroyed.
     */
    ScopedTimer(double& elapsed);

    /**
     * \brief Constructor
     *
     * Constructor which accepts a ref to a ScopedTimable. Will start the time interval measurement.
     *
     * \param st A ref to a ScopedTimable into which the result of the ScopedTimer can be written.
     */
    ScopedTimer(ScopedTimable& st);

    /**
     * \brief Destructor
     *
     * Destructor. Will stop the time interval measurement and write the calculated elapsed time into _elapsed.
     */
    virtual ~ScopedTimer();

private:
    double& m_elapsed;      ///< Ref to elapsed time, will be written on destruction of ScopedTimer instances
    ::timespec m_startTime; ///< Start time, set on construction

    /**
     * \brief Convert ::timespec to seconds
     *
     * Static function to convert a ::timespec struct into a double representation in seconds.
     *
     * \param ts std::timespec struct containing the time to be converted.
     * \return A double which represents the time in parameter ts in seconds.
     */
    static double _s_getDoubleTime(const ::timespec& ts);

};

}}} // namespace tests::storePerftools::common

#endif // tests_storePerftools_common_ScopedTimer_h_
