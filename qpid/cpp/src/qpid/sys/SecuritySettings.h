#ifndef QPID_SYS_SECURITYSETTINGS_H
#define QPID_SYS_SECURITYSETTINGS_H

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
#include <string>

namespace qpid {
namespace sys {

/**
 * Conveys security information from a given transport to the upper
 * layers.
 */
struct SecuritySettings
{
    /**
     * Security Strength Factor (SSF). Possible values are:
     * 
     *             @li 0  No security
     *             @li 1  Integrity checking only
     *             @li >1 Integrity and confidentiality with the number
     *                    giving the encryption key length.
     */
    unsigned int ssf;
    /**
     * An authorisation id
     */
    std::string authid;

    /**
     * Disables SASL mechanisms that are vulnerable to passive
     * dictionary-based password attacks
     */
    bool nodict;

    SecuritySettings() : ssf(0), nodict(false) {}
};

}} // namespace qpid::sys

#endif  /*!QPID_SYS_SECURITYSETTINGS_H*/
