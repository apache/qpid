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

/*
 * AMQP has a SASL authentication mechanism that doesn't match
 * with existing .NET credentials.  The analogy breaks down further
 * if there is a list of brokers to cycle through on failover.
 * This class will allow arbitrary credentials to be specified
 * by the user, but is meant to be sensibly populated by bindings
 * that use it from ClientCredentials.
 * See the related interplay of ClientCredentials and
 * WebProxy NetworkCredential for the BasicHttpBinding.
 */

namespace Apache.Qpid.Channel
{
    using System;

    /// <summary>
    /// Credentials for establishing a connection to an AMQP server.
    /// </summary>
    public class AmqpCredential
    {
        private string password;
        private string userName; // SASL authentication id
        // Future: private string the_Sasl_Authorization_ID
        // Future: private X509CertificateInitiatorClientCredential tlsClientCertificate;

        public AmqpCredential(string userName, string password)
        {
            if (userName == null)
            {
                throw new ArgumentNullException("user name");
            }

            if (password == null)
            {
                throw new ArgumentNullException("password");
            }

            this.userName = userName;
            this.password = password;
        }

        public string UserName
        {
            get
            {
                if (this.userName == null)
                {
                    this.userName = "";
                }

                return this.userName;
            }

            set
            {
                if (value == null)
                {
                    throw new ArgumentNullException("user name");
                }

                this.userName = value;
            }
        }

        public string Password
        {
            get
            {
                if (this.password == null)
                {
                    this.password = "";
                }

                return this.password;
            }

            set
            {
                if (value == null)
                {
                    throw new ArgumentNullException("password");
                }

                this.password = value;
            }
        }

        public AmqpCredential Clone()
        {
            return (AmqpCredential) this.MemberwiseClone();
        }
    }

}
