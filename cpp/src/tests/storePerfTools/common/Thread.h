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
 * \file Thread.h
 */

#ifndef tests_storePerfTools_common_Thread_h_
#define tests_storePerfTools_common_Thread_h_

#include <pthread.h>
#include <string>

namespace tests {
namespace storePerftools {
namespace common {

/**
 * \brief Ultra-simple pthread class.
 */
class Thread {
public:
    typedef void*(*startFn_t)(void*);   ///< Thread entry point function pointer type

    /**
     * \brief Constructor
     * \param sf Pointer to thread entry function
     * \param p Void pointer to parameter of start function
     */
    Thread(startFn_t sf,
           void* p);

    /**
     * \brief Constructor
     * \param sf Pointer to thread entry function
     * \param p Void pointer to parameter of start function
     * \param id Name of this thread instance
     */
    Thread(startFn_t sf,
           void* p,
           const std::string& id);

    /**
     * \brief Destructor
     */
    virtual ~Thread();

    /**
     * \brief Get the name of this thread.
     * \return Name as supplied to the constructor.
     */
    const std::string& getId() const;

    /**
     * \brief Wait for this thread instance to finish running startFn().
     */
    void join();

private:
    ::pthread_t m_thread;   ///< Internal posix thread
    std::string m_id;       ///< Identifier for this thread instance
    bool m_running;         ///< \b true is the thread is active and running, \b false when not yet started or joined.

};

}}} // namespace tests::storePerftools::common

#endif // tests_storePerfTools_common_Thread_h_
