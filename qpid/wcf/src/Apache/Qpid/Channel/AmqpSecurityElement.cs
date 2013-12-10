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
    using System;
    using System.Collections.Generic;
    using System.Collections.ObjectModel;
    using System.Configuration;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using System.ServiceModel.Configuration;
    using Apache.Qpid.AmqpTypes;

    public sealed class AmqpSecurityElement : ConfigurationElement
    {
        public AmqpSecurityElement()
        {
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityMode, 
                typeof(AmqpSecurityMode), AmqpSecurityMode.None, null, null, ConfigurationPropertyOptions.None));
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityTransport,
                typeof(AmqpTransportSecurityElement), null));

        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityMode, DefaultValue = AmqpSecurityMode.None)]
        public AmqpSecurityMode Mode
        {
            get { return (AmqpSecurityMode)base[AmqpConfigurationStrings.SecurityMode]; }
            set { base[AmqpConfigurationStrings.SecurityMode] = value; }
        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityTransport)]
        public AmqpTransportSecurityElement Transport
        {
            get { return (AmqpTransportSecurityElement)base[AmqpConfigurationStrings.SecurityTransport]; }
            set { base[AmqpConfigurationStrings.SecurityTransport] = value; }
        }
    }

    public class AmqpTransportSecurityElement : ConfigurationElement
    {
        public AmqpTransportSecurityElement()
        {
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportCredentialType,
                typeof(AmqpCredentialType), AmqpCredentialType.Anonymous, null, null, ConfigurationPropertyOptions.None));
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportUseSSL,
                typeof(bool), false, null, null, ConfigurationPropertyOptions.None));
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportDefaultCredential,
                typeof(AmqpCredentialElement), null));
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportIgnoreEndpointCredentials,
                typeof(bool), false, null, null, ConfigurationPropertyOptions.None));

        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportCredentialType, DefaultValue = AmqpCredentialType.Anonymous)]
        public AmqpCredentialType CredentialType
        {
            get { return (AmqpCredentialType)base[AmqpConfigurationStrings.SecurityTransportCredentialType]; }
            set { base[AmqpConfigurationStrings.SecurityTransportCredentialType] = value; }
        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportUseSSL, DefaultValue = false)]
        public bool UseSSL
        {
            get { return (bool)base[AmqpConfigurationStrings.SecurityTransportUseSSL]; }
            set { base[AmqpConfigurationStrings.SecurityTransportUseSSL] = value; }
        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportDefaultCredential, DefaultValue = null)]
        public AmqpCredentialElement DefaultCredential
        {
            get { return (AmqpCredentialElement)base[AmqpConfigurationStrings.SecurityTransportDefaultCredential]; }
            set { base[AmqpConfigurationStrings.SecurityTransportDefaultCredential] = value; }
        }

        [ConfigurationProperty(AmqpConfigurationStrings.SecurityTransportIgnoreEndpointCredentials, DefaultValue = false)]
        public bool IgnoreEndpointCredentials
        {
            get { return (bool)base[AmqpConfigurationStrings.SecurityTransportIgnoreEndpointCredentials]; }
            set { base[AmqpConfigurationStrings.SecurityTransportIgnoreEndpointCredentials] = value; }
        }
    }

    public class AmqpCredentialElement : ConfigurationElement
    {
        public AmqpCredentialElement()
        {
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.CredentialUserName, 
                typeof(string), "", null, null, ConfigurationPropertyOptions.None));
            Properties.Add(new ConfigurationProperty(AmqpConfigurationStrings.CredentialPassword,
                typeof(string), "", null, null, ConfigurationPropertyOptions.None));

        }

        [ConfigurationProperty(AmqpConfigurationStrings.CredentialUserName, DefaultValue = "")]
        public string UserName
        {
            get { return (string)base[AmqpConfigurationStrings.CredentialUserName]; }
            set { base[AmqpConfigurationStrings.CredentialUserName] = value; }
        }

        [ConfigurationProperty(AmqpConfigurationStrings.CredentialPassword, DefaultValue = "")]
        public string Password
        {
            get { return (string)base[AmqpConfigurationStrings.CredentialPassword]; }
            set { base[AmqpConfigurationStrings.CredentialPassword] = value; }
        }
    }
}
