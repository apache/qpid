#ifndef QPID_DATADIR_H
#define QPID_DATADIR_H

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

#include <string>

namespace qpid {

/**
 * DataDir class.
 */
class DataDir
{
    const bool        enabled;
    const std::string dirPath;

  public:

    DataDir (std::string path);
    ~DataDir ();

    bool        isEnabled () { return enabled; }
    std::string getPath   () { return dirPath; }
};
 
} // namespace qpid

#endif  /*!QPID_DATADIR_H*/
