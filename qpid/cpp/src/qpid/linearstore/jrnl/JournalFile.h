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

#ifndef QPID_LINEARSTORE_JOURNALFILE_H_
#define QPID_LINEARSTORE_JOURNALFILE_H_

#include <string>

namespace qpid {
namespace qls_jrnl {

class JournalFile
{
protected:
    const std::string fqfn;
public:
    JournalFile(const std::string& fqFileName_);
    virtual ~JournalFile();

    const std::string directory() const;
    const std::string fileName() const;
    const std::string fqFileName() const;
    bool empty() const;
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_JOURNALFILE_H_
