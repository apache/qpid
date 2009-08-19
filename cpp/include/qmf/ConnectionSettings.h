#ifndef _QmfConnectionSettings_
#define _QmfConnectionSettings_

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

#include "qmf/QmfImportExport.h"

namespace qmf {

    class ConnectionSettingsImpl;
    class Value;

    /**
     * Settings for AMQP connections to the broker.
     *
     * \ingroup qmfapi
     */
    class ConnectionSettings {
    public:

        /**
         * Create a set of default connection settings.
         *
         * If no further attributes are set, the settings will cause a connection to be made to
         * the default broker (on localhost or at a host/port supplied by service discovery) and
         * authentication will be the best-available (GSSAPI/Kerberos, Anonymous, Plain with prompts
         * for username and password).
         */
        QMF_EXTERN ConnectionSettings();

        /**
         * Create a set of connection settings by URL.
         *
         * @param url Universal resource locator describing the broker address and additional attributes.
         *
         * The URL is of the form:
         *    amqp[s]://host[:port][?key=value[&key=value]*]
         *
         * For example:
         *    amqp://localhost
         *    amqp://broker?transport=rdma&authmech=GSSAPI&authservice=qpidd
         *    amqps://broker?authmech=PLAIN&authuser=guest&authpass=guest
         */
        QMF_EXTERN ConnectionSettings(const char* url);

        /**
         * Destroy the connection settings object.
         */
        QMF_EXTERN ~ConnectionSettings();

        /**
         * Set an attribute to control connection setup.
         *
         * @param key A null-terminated string that is an attribute name.
         *
         * @param value Reference to a value to be stored as the attribute.  The type of the value
         *              is specific to the key.
         */
        QMF_EXTERN void setAttr(const char* key, const Value& value);

        /**
         * Get the value of an attribute.
         *
         * @param key A null-terminated attribute name.
         */
        QMF_EXTERN const Value& getAttr(const char* key) const;

        /**
         * Get the attribute string (the portion of the URL following the '?') for the settings.
         *
         * @return A pointer to the attribute string.  If the content of this string needs to be
         *         available beyond the scope of the calling function, it should be copied.  The
         *         returned pointer may become invalid if the set of attributes is changed.
         */
        QMF_EXTERN const char* getAttrString() const;

        /**
         * Shortcuts for setting the transport for the connection.
         *
         * @param port The port value for the connection address.
         */
        QMF_EXTERN void transportTcp(uint16_t port = 5672);
        QMF_EXTERN void transportSsl(uint16_t port = 5671);
        QMF_EXTERN void transportRdma(uint16_t port = 5672);

        /**
         * Shortcuts for setting authentication mechanisms.
         *
         * @param username Null-terminated authentication user name.
         *
         * @param password Null-terminated authentication password.
         *
         * @param serviceName Null-terminated GSSAPI service name (Kerberos service principal)
         *
         * @param minSsf Minimum security factor for connections. 0 = encryption not required.
         *
         * @param maxSsf Maximum security factor for connections. 0 = encryption not permitted.
         */
        QMF_EXTERN void authAnonymous(const char* username = 0);
        QMF_EXTERN void authPlain(const char* username = 0, const char* password = 0);
        QMF_EXTERN void authGssapi(const char* serviceName, uint32_t minSsf = 0, uint32_t maxSsf = 256);

        /**
         * Shortcut for setting connection retry attributes.
         *
         * @param delayMin Minimum delay (in seconds) between connection attempts.
         *
         * @param delaxMax Maximum delay (in seconds) between connection attempts.
         *
         * @param delayFactor Factor to multiply the delay by between failed connection attempts.
         */
        QMF_EXTERN void setRetry(int delayMin = 1, int delayMax = 128, int delayFactor = 2);

    private:
        ConnectionSettingsImpl* impl;
    };

}

#endif
