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
package org.apache.qpid.server.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.urlstreamhandler.data.Handler;

@ManagedObject( category = false )
public class NonJavaTrustStoreImpl
        extends AbstractConfiguredObject<NonJavaTrustStoreImpl> implements NonJavaTrustStore<NonJavaTrustStoreImpl>
{
    private static final Logger LOGGER = Logger.getLogger(NonJavaTrustStoreImpl.class);

    private final Broker<?> _broker;

    @ManagedAttributeField( afterSet = "updateTrustManagers" )
    private String _certificatesUrl;

    private volatile TrustManager[] _trustManagers = new TrustManager[0];



    static
    {
        Handler.register();
    }

    private X509Certificate[] _certificates;

    @ManagedObjectFactoryConstructor
    public NonJavaTrustStoreImpl(final Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), attributes);
        _broker = broker;
    }

    @Override
    public String getCertificatesUrl()
    {
        return _certificatesUrl;
    }


    @Override
    public List<Map<CertificateDetails,Object>> getCertificateDetails()
    {
        List<Map<CertificateDetails,Object>> certificateDetails = new ArrayList<>();
        if(_certificates != null)
        {
            for (X509Certificate certificate : _certificates)
            {
                Map<CertificateDetails, Object> details = new EnumMap<>(CertificateDetails.class);

                details.put(CertificateDetails.SUBJECT_NAME, getNameFromCertificate(certificate));
                details.put(CertificateDetails.ISSUER_NAME, certificate.getIssuerX500Principal().getName());
                details.put(CertificateDetails.VALID_START, certificate.getNotBefore());
                details.put(CertificateDetails.VALID_END, certificate.getNotAfter());
                certificateDetails.add(details);
            }
        }
        return certificateDetails;
    }

    private String getNameFromCertificate(final X509Certificate certificate)
    {
        String name;
        X500Principal subjectX500Principal = certificate.getSubjectX500Principal();
        name = getCommonNameFromPrincipal(subjectX500Principal);

        return name;
    }

    private String getCommonNameFromPrincipal(final X500Principal subjectX500Principal)
    {
        String name;
        String dn = subjectX500Principal.getName();
        try
        {
            LdapName ldapDN = new LdapName(dn);
            name = dn;
            for (Rdn rdn : ldapDN.getRdns())
            {
                if (rdn.getType().equalsIgnoreCase("CN"))
                {
                    name = String.valueOf(rdn.getValue());
                    break;
                }
            }

        }
        catch (InvalidNameException e)
        {
            LOGGER.error("Error getting subject name from certificate");
            name =  null;
        }
        return name;
    }


    @Override
    public TrustManager[] getTrustManagers() throws GeneralSecurityException
    {

        return _trustManagers;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        validateTrustStoreAttributes(this);
    }

    @Override
    public State getState()
    {
        return State.ACTIVE;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (KeyStore.STATE.equals(name))
        {
            return getState();
        }

        return super.getAttribute(name);
    }

    @Override
    protected boolean setState(State desiredState)
    {
        if (desiredState == State.DELETED)
        {
            // verify that it is not in use
            String storeName = getName();

            Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
            for (Port port : ports)
            {
                if (port.getKeyStore() == this)
                {
                    throw new IntegrityViolationException("Key store '"
                                                          + storeName
                                                          + "' can't be deleted as it is in use by a port:"
                                                          + port.getName());
                }
            }
            deleted();
            return true;
        }

        return false;
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if (desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), KeyStore.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of key store is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes)
            throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), KeyStore.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting key store attributes is denied");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        NonJavaTrustStore changedStore = (NonJavaTrustStore) proxyForValidation;
        if (changedAttributes.contains(NAME) && !getName().equals(changedStore.getName()))
        {
            throw new IllegalConfigurationException("Changing the key store name is not allowed");
        }
        validateTrustStoreAttributes(changedStore);
    }

    private void validateTrustStoreAttributes(NonJavaTrustStore<?> keyStore)
    {
        try
        {
            getUrlFromString(keyStore.getCertificatesUrl()).openStream();
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("unused")
    private void updateTrustManagers()
    {
        try
        {
            if (_certificatesUrl != null)
            {
                X509Certificate[] certs = readCertificates(getUrlFromString(_certificatesUrl));
                java.security.KeyStore inMemoryKeyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());

                inMemoryKeyStore.load(null, null);
                int i = 1;
                for(Certificate cert : certs)
                {
                    inMemoryKeyStore.setCertificateEntry(String.valueOf(i++), cert);
                }



                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(inMemoryKeyStore);
                _trustManagers = tmf.getTrustManagers();
                _certificates = certs;
            }

        }
        catch (IOException | GeneralSecurityException e)
        {
            LOGGER.error("Error attempting to create KeyStore from private key and certificates", e);
            _trustManagers = new TrustManager[0];
        }
    }

    private URL getUrlFromString(String urlString) throws MalformedURLException
    {
        URL url;

        try
        {
            url = new URL(urlString);
        }
        catch (MalformedURLException e)
        {
            File file = new File(urlString);
            url = file.toURI().toURL();

        }
        return url;
    }

    public static X509Certificate[] readCertificates(URL certFile)
            throws IOException, GeneralSecurityException
    {
        List<X509Certificate> crt = new ArrayList<>();
        try (InputStream is = certFile.openStream())
        {
            do
            {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                crt.add( (X509Certificate) cf.generateCertificate(is));
            } while(is.available() != 0);
        }
        catch(CertificateException e)
        {
            if(crt.isEmpty())
            {
                throw e;
            }
        }
        return crt.toArray(new X509Certificate[crt.size()]);
    }

}
