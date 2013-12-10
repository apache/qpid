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

#ifndef QPID_LINEARSTORE_JOURNAL_CHECKSUM_H_
#define QPID_LINEARSTORE_JOURNAL_CHECKSUM_H_

#include <cstddef>
#include <stdint.h>

namespace qpid {
namespace linearstore {
namespace journal {

/*
 * This checksum routine uses the Adler-32 algorithm as described in
 * http://en.wikipedia.org/wiki/Adler-32. It is structured so that the
 * data for which the checksum must be calculated can be added in several
 * stages through the addData() function, and when complete, the checksum
 * is obtained through a call to getChecksum().
 */
class Checksum
{
private:
    uint32_t a;
    uint32_t b;
    const uint32_t MOD_ADLER;
public:
    Checksum();
    virtual ~Checksum();
    void addData(const unsigned char* data, const std::size_t len);
    uint32_t getChecksum();
};

}}}

#endif // QPID_LINEARSTORE_JOURNAL_CHECKSUM_H_
