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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.xml.bind.DatatypeConverter;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.util.urlstreamhandler.data.Handler;

@ManagedObject( category = false )
public class NonJavaKeyStoreImpl extends AbstractConfiguredObject<NonJavaKeyStoreImpl> implements NonJavaKeyStore<NonJavaKeyStoreImpl>
{
    private static final Logger LOGGER = Logger.getLogger(NonJavaKeyStoreImpl.class);

    private final Broker<?> _broker;

    @ManagedAttributeField( afterSet = "updateKeyManagers" )
    private String _privateKeyUrl;
    @ManagedAttributeField( afterSet = "updateKeyManagers" )
    private String _certificateUrl;
    @ManagedAttributeField( afterSet = "updateKeyManagers" )
    private String _intermediateCertificateUrl;

    private volatile KeyManager[] _keyManagers = new KeyManager[0];

    private static final SecureRandom RANDOM = new SecureRandom();

    static
    {
        Handler.register();
    }

    private X509Certificate _certificate;

    @ManagedObjectFactoryConstructor
    public NonJavaKeyStoreImpl(final Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), attributes);
        _broker = broker;
    }

    @Override
    public String getPrivateKeyUrl()
    {
        return _privateKeyUrl;
    }

    @Override
    public String getCertificateUrl()
    {
        return _certificateUrl;
    }

    @Override
    public String getIntermediateCertificateUrl()
    {
        return _intermediateCertificateUrl;
    }

    @Override
    public String getSubjectName()
    {
        if(_certificate != null)
        {
            try
            {
                String dn = _certificate.getSubjectX500Principal().getName();
                LdapName ldapDN = new LdapName(dn);
                String name = dn;
                for (Rdn rdn : ldapDN.getRdns())
                {
                    if (rdn.getType().equalsIgnoreCase("CN"))
                    {
                        name = String.valueOf(rdn.getValue());
                        break;
                    }
                }
                return name;
            }
            catch (InvalidNameException e)
            {
                LOGGER.error("Error getting subject name from certificate");
                return null;
            }
        }
        else
        {
            return null;
        }
    }

    @Override
    public long getCertificateValidEnd()
    {
        return _certificate == null ? 0 : _certificate.getNotAfter().getTime();
    }

    @Override
    public long getCertificateValidStart()
    {
        return _certificate == null ? 0 : _certificate.getNotBefore().getTime();
    }


    @Override
    public KeyManager[] getKeyManagers() throws GeneralSecurityException
    {

        return _keyManagers;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        validateKeyStoreAttributes(this);
    }

    @StateTransition(currentState = {State.ACTIVE, State.ERRORED}, desiredState = State.DELETED)
    protected ListenableFuture<Void> doDelete()
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
        setState(State.DELETED);
        return Futures.immediateFuture(null);
    }

    @StateTransition(currentState = {State.UNINITIALIZED, State.ERRORED}, desiredState = State.ACTIVE)
    protected ListenableFuture<Void> doActivate()
    {
        setState(State.ACTIVE);
        return Futures.immediateFuture(null);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        NonJavaKeyStore changedStore = (NonJavaKeyStore) proxyForValidation;
        if (changedAttributes.contains(NAME) && !getName().equals(changedStore.getName()))
        {
            throw new IllegalConfigurationException("Changing the key store name is not allowed");
        }
        validateKeyStoreAttributes(changedStore);
    }

    private void validateKeyStoreAttributes(NonJavaKeyStore<?> keyStore)
    {
        try
        {
            readPrivateKey(getUrlFromString(keyStore.getPrivateKeyUrl()));
            readCertificates(getUrlFromString(keyStore.getCertificateUrl()));
            if(keyStore.getIntermediateCertificateUrl() != null)
            {
                readCertificates(getUrlFromString(keyStore.getIntermediateCertificateUrl()));
            }
        }
        catch (IOException | GeneralSecurityException e )
        {
            throw new IllegalConfigurationException("Cannot validate private key or certificate(s):" + e, e);
        }
    }

    @SuppressWarnings("unused")
    private void updateKeyManagers()
    {
        try
        {
            if (_privateKeyUrl != null && _certificateUrl != null)
            {
                PrivateKey privateKey = readPrivateKey(getUrlFromString(_privateKeyUrl));
                X509Certificate[] certs = readCertificates(getUrlFromString(_certificateUrl));
                if(_intermediateCertificateUrl != null)
                {
                    List<X509Certificate> allCerts = new ArrayList<>(Arrays.asList(certs));
                    allCerts.addAll(Arrays.asList(readCertificates(getUrlFromString(_intermediateCertificateUrl))));
                    certs = allCerts.toArray(new X509Certificate[allCerts.size()]);
                }

                java.security.KeyStore inMemoryKeyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());

                byte[] bytes = new byte[64];
                char[] chars = new char[64];
                RANDOM.nextBytes(bytes);
                StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes)).get(chars);
                inMemoryKeyStore.load(null, chars);
                inMemoryKeyStore.setKeyEntry("1", privateKey, chars, certs);


                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(inMemoryKeyStore, chars);
                _keyManagers = kmf.getKeyManagers();
                _certificate = certs[0];
            }

        }
        catch (IOException | GeneralSecurityException e)
        {
            throw new IllegalConfigurationException("Cannot load private key or certificate(s): " + e, e);
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

    private static PrivateKey readPrivateKey(final URL url)
            throws IOException, GeneralSecurityException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream urlStream = url.openStream())
        {
            byte[] tmp = new byte[1024];
            int read;
            while((read = urlStream.read(tmp)) != -1)
            {
                buffer.write(tmp,0,read);
            }
        }

        byte[] content = buffer.toByteArray();
        String contentAsString = new String(content, StandardCharsets.US_ASCII);
        if(contentAsString.contains("-----BEGIN ") && contentAsString.contains(" PRIVATE KEY-----"))
        {
            BufferedReader lineReader = new BufferedReader(new StringReader(contentAsString));

            String line;
            do
            {
                line = lineReader.readLine();
            } while(line != null && !(line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")));

            if(line != null)
            {
                StringBuilder keyBuilder = new StringBuilder();

                while((line = lineReader.readLine()) != null)
                {
                    if(line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----"))
                    {
                        break;
                    }
                    keyBuilder.append(line);
                }

                content = DatatypeConverter.parseBase64Binary(keyBuilder.toString());
            }
        }
        PrivateKey key;
        try
        {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(content);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            key = kf.generatePrivate(keySpec);
        }
        catch(InvalidKeySpecException e)
        {
            // not in PCKS#8 format - try parsing as PKCS#1
            RSAPrivateCrtKeySpec keySpec = getRSAKeySpec(content);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            try
            {
                key = kf.generatePrivate(keySpec);
            }
            catch(InvalidKeySpecException e2)
            {
                throw new InvalidKeySpecException("Cannot parse the provided key as either PKCS#1 or PCKS#8 format");
            }

        }
        return key;
    }


    private static RSAPrivateCrtKeySpec getRSAKeySpec(byte[] keyBytes) throws InvalidKeySpecException
    {

        ByteBuffer buffer = ByteBuffer.wrap(keyBytes);
        try
        {
            // PKCS#1 is encoded as a DER sequence of:
            // (version, modulus, publicExponent, privateExponent, primeP, primeQ,
            //  primeExponentP, primeExponentQ, crtCoefficient)

            int tag = ((int)buffer.get()) & 0xff;

            // check tag is that of a sequence
            if(((tag & 0x20) != 0x20) || ((tag & 0x1F) != 0x10))
            {
                throw new InvalidKeySpecException("Unable to parse key as PKCS#1 format");
            }

            int length = getLength(buffer);

            buffer = buffer.slice();
            buffer.limit(length);

            // first tlv is version - which we'll ignore
            byte versionTag = buffer.get();
            int versionLength = getLength(buffer);
            buffer.position(buffer.position()+versionLength);


            RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
                    getInteger(buffer), getInteger(buffer), getInteger(buffer), getInteger(buffer), getInteger(buffer),
                    getInteger(buffer), getInteger(buffer), getInteger(buffer));

            return keySpec;
        }
        catch(BufferUnderflowException e)
        {
            throw new InvalidKeySpecException("Unable to parse key as PKCS#1 format");
        }
    }

    private static int getLength(ByteBuffer buffer)
    {

        int i = ((int) buffer.get()) & 0xff;

        // length 0 <= i <= 127 encoded as a single byte
        if ((i & ~0x7F) == 0)
        {
            return i;
        }

        // otherwise the first octet gives us the number of octets needed to read the length
        byte[] bytes = new byte[i & 0x7f];
        buffer.get(bytes);

        return new BigInteger(1, bytes).intValue();
    }

    private static BigInteger getInteger(ByteBuffer buffer) throws InvalidKeySpecException
    {
        int tag = ((int) buffer.get()) & 0xff;
        // 0x02 indicates an integer type
        if((tag & 0x1f) != 0x02)
        {
            throw new InvalidKeySpecException("Unable to parse key as PKCS#1 format");
        }
        byte[] num = new byte[getLength(buffer)];
        buffer.get(num);
        return new BigInteger(num);
    }

}
