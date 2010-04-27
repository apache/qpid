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

namespace Apache.Qpid.Channel
{
    /// <summary>
    /// This class is used by the AMQP Transport to set transport-level security settings for a binding
    /// </summary>
    public sealed class AmqpTransportSecurity
    {
        private AmqpCredentialType credentialType;

        // WCF frowns on unencrypted credentials on the wire, but AMQP is agnostic.
        // For interoperability, allow SSL to be turned on/off independentaly.
        private bool useSSL;

        // Allow per channel credentials, but also ease the common case where
        // credentials are shared and wish to be globally set in a config file.
        private AmqpCredential defaultCredential;

        // if true, do not look at context for ServiceModel.Description.ClientCredentials.
        // ClientCredentials will be place of choice for WCF traditionalists
        // to specify auth tokens to the AMQP server when Windows and SASL tokens
        // look the same.  At other times it makes no sense and sometimes it is
        // confusing with Message-level credentials.
        private bool ignoreEndpointClientCredentials;


        internal AmqpTransportSecurity()
        {
            this.credentialType = AmqpCredentialType.Anonymous;
            this.useSSL = true;
        }

        /// <summary>
        /// gets or sets the SASL mechanism for AMQP authentication between client and server.
        /// </summary>
        public AmqpCredentialType CredentialType
        {
            get { return this.credentialType; }

            set { this.credentialType = value; }
        }

        /// <summary>
        /// gets or sets the flag that controls the use of SSL encryption
        /// over the network connection.
        /// </summary>
        public bool UseSSL
        {
            get { return this.useSSL; }
            set { this.useSSL = value; }
        }

        /// <summary>
        /// gets the default credential object for authentication with the AMQP server.
        /// </summary>
        public AmqpCredential DefaultCredential
        {
            get { return this.defaultCredential; }
            set { this.defaultCredential = value; }
        }

        /// <summary>
        /// gets or sets the endpoint ClientCredentials search parameter.  If true,
        /// only AmqpCredential objects are searched for in the surrounding context.
        /// </summary>
        public bool IgnoreEndpointClientCredentials
        {
            get { return this.ignoreEndpointClientCredentials; }
            set { this.ignoreEndpointClientCredentials = value; }
        }

        internal AmqpTransportSecurity Clone()
        {
            AmqpTransportSecurity sec = (AmqpTransportSecurity)this.MemberwiseClone();
            if (this.defaultCredential != null)
            {
                sec.defaultCredential = this.defaultCredential.Clone();
            }

            return sec;
        }
    }
}
