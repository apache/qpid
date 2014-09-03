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
package org.apache.qpid.transport.network.security.ssl;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.TransportException;

public class SSLUtilTest extends QpidTestCase
{
    public void testGetIdFromSubjectDN()
    {
        // "normal" dn
        assertEquals("user@somewhere.example.org",SSLUtil.getIdFromSubjectDN("cn=user,dc=somewhere,dc=example,dc=org"));
        // quoting of values, case of types, spacing all ignored
        assertEquals("user2@somewhere.example.org",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,cn=\"user2\",dc=org"));
        // only first cn is used
        assertEquals("user@somewhere.example.org",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,cn=\"user\",dc=org, cn=user2"));
        // no cn, no Id
        assertEquals("",SSLUtil.getIdFromSubjectDN("DC=somewhere, dc=example,dc=org"));
        // cn in value is ignored
        assertEquals("",SSLUtil.getIdFromSubjectDN("C=CZ,O=Scholz,OU=\"JAKUB CN=USER1\""));
        // cn with no dc gives just user
        assertEquals("someone",SSLUtil.getIdFromSubjectDN("ou=someou, CN=\"someone\""));
        // null results in empty string
        assertEquals("",SSLUtil.getIdFromSubjectDN(null));
        // invalid name results in empty string
        assertEquals("",SSLUtil.getIdFromSubjectDN("ou=someou, ="));
        // component containing whitespace
        assertEquals("me@example.com",SSLUtil.getIdFromSubjectDN("CN=me,DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB"));
        // empty CN
        assertEquals("",SSLUtil.getIdFromSubjectDN("CN=,DC=somewhere, dc=example,dc=org"));


    }

    public void testWildCardAndSubjectAltNameMatchingWorks() throws Exception
    {
        doNameMatchingTest(KEYSTORE_1,
                           Arrays.asList("amqp.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "*.example.com"));

        doNameMatchingTest(KEYSTORE_2,
                           Arrays.asList("amqp.example.com", "amqp1.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "*.example.com"));

        doNameMatchingTest(KEYSTORE_3,
                           Arrays.asList("amqp.example.com", "amqp1.example.com", "amqp2.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "*.example.com"));

        doNameMatchingTest(KEYSTORE_4,
                           Arrays.asList("amqp.example.com", "amqp1.example.com", "amqp2.example.com", "foo.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "foo.bar.example.com"));

        doNameMatchingTest(KEYSTORE_5,
                           Arrays.asList("amqp.example.com", "foo.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "foo.bar.example.com", "foo.org"));

        doNameMatchingTest(KEYSTORE_6,
                           Arrays.asList("amqp.example.com"),
                           Arrays.asList("amqp.example.net", "example.com", "foo.bar.example.com", "foo.org", "foo"));

        doNameMatchingTest(KEYSTORE_7,
                           Arrays.asList("amqp.example.org", "amqp1.example.org", "amqp2.example.org"),
                           Arrays.asList("amqp.example.net", "example.com", "foo.bar.example.com", "foo.org", "foo"));

        doNameMatchingTest(KEYSTORE_8,
                           Arrays.asList("amqp.example.org", "example.org"),
                           Arrays.asList("amqp1.example.org", "example.com", "foo.bar.example.com", "foo.org", "foo"));

        doNameMatchingTest(KEYSTORE_9,
                           Arrays.asList("amqp.example.org"),
                           Arrays.asList("amqp1.example.org", "example.org", "*.example.org"));

        doNameMatchingTest(KEYSTORE_10,
                           Arrays.asList("amqp.example.org", "amqp1.example.org"),
                           Arrays.asList("example.org", "a.mqp.example.org"));

        doNameMatchingTest(KEYSTORE_11,
                           Collections.<String>emptyList(),
                           Arrays.asList("example.org", "a.mqp.example.org", "org"));

        doNameMatchingTest(KEYSTORE_12,
                           Collections.<String>emptyList(),
                           Arrays.asList("example.org", "a.mqp.example.org", "org"));

        doNameMatchingTest(KEYSTORE_13,
                           Collections.<String>emptyList(),
                           Arrays.asList("example.org", "a.mqp.example.org", "org"));
    }

    private void doNameMatchingTest(byte[] keystoreBytes, List<String> validAddresses, List<String> invalidAddresses) throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new ByteArrayInputStream(keystoreBytes), "password".toCharArray());


        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new ByteArrayInputStream(TRUSTSTORE), "password".toCharArray());

        for(String validAddress : validAddresses)
        {
            try
            {
                SSLUtil.verifyHostname(getSSLEngineAfterHandshake(keyStore, trustStore, validAddress, 5672),
                                       validAddress);
            }
            catch(TransportException e)
            {
                fail("The address " + validAddress + " should validate but does not");
            }
        }

        for(String invalidAddress : invalidAddresses)
        {
            try
            {
                SSLUtil.verifyHostname(getSSLEngineAfterHandshake(keyStore, trustStore, invalidAddress, 5672),
                                       invalidAddress);
                fail("The address " + invalidAddress + " should not validate but it does");
            }
            catch(TransportException e)
            {
                // pass
            }
        }
    }

    private SSLEngine getSSLEngineAfterHandshake(final KeyStore keyStore,
                                                 final KeyStore trustStore,
                                                 String host,
                                                 int port)
            throws Exception
    {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManager.init(keyStore, "password".toCharArray());
        sslContext.init(keyManager.getKeyManagers(), null,null);

        SSLEngine serverEngine = sslContext.createSSLEngine();
        serverEngine.setUseClientMode(false);


        SSLContext clientContext = SSLContext.getInstance("TLS");
        TrustManagerFactory trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManager.init(trustStore);

        clientContext.init(null, trustManager.getTrustManagers(), null);

        SSLEngine clientEngine = clientContext.createSSLEngine(host, port);

        clientEngine.setUseClientMode(true);
        clientEngine.beginHandshake();

        byte[] clientInput = new byte[0];
        byte[] clientOutput = new byte[0];

        SSLEngineResult.HandshakeStatus clientStatus;
        while((clientStatus = clientEngine.getHandshakeStatus()) != SSLEngineResult.HandshakeStatus.FINISHED
              && clientStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
        {
            switch (clientStatus)
            {
                case NEED_TASK:
                    clientEngine.getDelegatedTask().run();
                    break;
                case NEED_WRAP:
                    ByteBuffer dst = ByteBuffer.allocate(1024*1024);
                    clientEngine.wrap(ByteBuffer.allocate(0), dst);
                    dst.flip();
                    byte[] output = new byte[clientOutput.length+dst.remaining()];
                    System.arraycopy(clientOutput,0,output,0,clientOutput.length);
                    dst.get(output, clientOutput.length, dst.remaining());
                    clientOutput = output;
                    break;
                case NEED_UNWRAP:
                    ByteBuffer unwrapDst = ByteBuffer.allocate(1024*1024);
                    ByteBuffer src = ByteBuffer.wrap(clientInput);
                    clientEngine.unwrap(src, unwrapDst);
                    byte[] input = new byte[src.remaining()];
                    src.get(input,0,src.remaining());
                    clientInput = input;
                default:
                    break;
            }

            SSLEngineResult.HandshakeStatus serverStatus = serverEngine.getHandshakeStatus();
            switch (serverStatus)
            {
                case NEED_TASK:
                    serverEngine.getDelegatedTask().run();
                    break;
                case NEED_WRAP:
                    ByteBuffer dst = ByteBuffer.allocate(1024*1024);
                    serverEngine.wrap(ByteBuffer.allocate(0), dst);
                    dst.flip();
                    byte[] serverOutput = new byte[clientInput.length+dst.remaining()];
                    System.arraycopy(clientInput,0,serverOutput,0,clientInput.length);
                    dst.get(serverOutput, clientInput.length, dst.remaining());
                    clientInput = serverOutput;
                    break;

                case NOT_HANDSHAKING:
                case NEED_UNWRAP:
                    ByteBuffer unwrapDst = ByteBuffer.allocate(1024*1024);
                    ByteBuffer src = ByteBuffer.wrap(clientOutput);
                    serverEngine.unwrap(src, unwrapDst);
                    byte[] input = new byte[src.remaining()];
                    src.get(input,0,src.remaining());
                    clientOutput = input;
            }
        }
        return clientEngine;
    }

    private static byte[] TRUSTSTORE = DatatypeConverter.parseBase64Binary(
            "/u3+7QAAAAIAAAACAAAAAgAGcm9vdGNhAAABSDadDw4ABVguNTA5AAADyjCCA8Yw"
            + "ggKuoAMCAQICAQUwDQYJKoZIhvcNAQEFBQAwdDETMBEGCgmSJomT8ixkARkWA29y"
            + "ZzEWMBQGCgmSJomT8ixkARkWBnNpbXBsZTETMBEGA1UECgwKU2ltcGxlIEluYzEX"
            + "MBUGA1UECwwOU2ltcGxlIFJvb3QgQ0ExFzAVBgNVBAMMDlNpbXBsZSBSb290IENB"
            + "MB4XDTE0MDkwMjExMTc1OFoXDTI0MDkwMTExMTc1OFowdDETMBEGCgmSJomT8ixk"
            + "ARkWA29yZzEWMBQGCgmSJomT8ixkARkWBnNpbXBsZTETMBEGA1UECgwKU2ltcGxl"
            + "IEluYzEXMBUGA1UECwwOU2ltcGxlIFJvb3QgQ0ExFzAVBgNVBAMMDlNpbXBsZSBS"
            + "b290IENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnbksOuGOSUBv"
            + "kbnUvWrgGOQeXQ7QoAMJEPhaxzW5aGZwpxf3F07yEyLKfyFH3URQOrXyl92xoH/u"
            + "b8RDjK8plBFQ93eteTK+k582REQdHHx7zdLAyaNDE/RHGJJV8WDbGj4mzguZGkDi"
            + "MGRS+j/UnQct6v5XXl/Ux2zahb16ZyoVtDlydKNVl8UR0aNn7esgfFw0x2OmplhN"
            + "0A8xqX//sQfVTi2rptBSo73whitUg29abcgtVXZnIQM5kssiJxA9ZewKLWc9K/g+"
            + "S2DOiPkNgVsliBaQUA7C5xlaCHrsyerUh8oOdvBe1eW8jfU3SwvejUvTfhMtu/sh"
            + "6Wu7GD44pQIDAQABo2MwYTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB"
            + "/zAdBgNVHQ4EFgQUWpliNfMupTQEz0td70FDAFy4vc0wHwYDVR0jBBgwFoAUWpli"
            + "NfMupTQEz0td70FDAFy4vc0wDQYJKoZIhvcNAQEFBQADggEBAGUCdZ01c61JtIA+"
            + "mC1+uNGC6wf6+D70TBf8WnrsuFnVU/LFbeuhBg+QhT7GkWx2qAit2L06W4QZKpcT"
            + "nqIX+fKImxlLwBXG7VPJXpQBVZ88LY9bLMRwlwm9AoSR70ip+Sof8nV+siSVV46S"
            + "1WZYO8QE35XXSF5xlmAuUkHa8RDVyHE24okcLG/GcemPwyv7PXwTiCJjwx9GqgHh"
            + "GkNYGPJHig0Vb6j/RXJ9kliw4xhDBcQ53TkUg9Os2t88yuUpNdoJ3fdf59TwcWC7"
            + "P4queBPb190HLE3nR4KmiVR7V/XdVUI31bOb11yVmoQ/mATvy+oHCbmdxzrWeDvv"
            + "8VGW8o4AAAACAAlzaWduaW5nY2EAAAFINp1WbAAFWC41MDkAAAPTMIIDzzCCAreg"
            + "AwIBAgIBBjANBgkqhkiG9w0BAQUFADB0MRMwEQYKCZImiZPyLGQBGRYDb3JnMRYw"
            + "FAYKCZImiZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQKDApTaW1wbGUgSW5jMRcwFQYD"
            + "VQQLDA5TaW1wbGUgUm9vdCBDQTEXMBUGA1UEAwwOU2ltcGxlIFJvb3QgQ0EwHhcN"
            + "MTQwOTAyMTExODE3WhcNMjQwOTAxMTExODE3WjB6MRMwEQYKCZImiZPyLGQBGRYD"
            + "b3JnMRYwFAYKCZImiZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQKDApTaW1wbGUgSW5j"
            + "MRowGAYDVQQLDBFTaW1wbGUgU2lnbmluZyBDQTEaMBgGA1UEAwwRU2ltcGxlIFNp"
            + "Z25pbmcgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDradZr1W8A"
            + "D8DmjziXB0UstOXnIEjL+7QbWeDlpgX5Mp4y8+iV+vxaI8x3ko1IfKsVa5Qge/W0"
            + "O4vVBF4f6Cbs9LBzUzlSeWJSPdGqWhn0nBLrIgnMbSmIy4k9bD3uo4zqZImrRhru"
            + "Y4GCGuc+51MWjIDTAx/UetaYgFk6Gu722yJBmOxzZ3WJmyBjBvKvBsAtetyenE9f"
            + "kXth4XJGOiqQYzW1RGhqOoFFbh92GB/5/0qQHkbMsfirguwjC0WwHJMrnDGolhbE"
            + "d9Spa4S6MtDbHS/PKe3C27D3ikknc3vUtDjGmpTYfSChBFbiNK+UGlcnKwMyBpnU"
            + "EPNVdjMpDVCbAgMBAAGjZjBkMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAG"
            + "AQH/AgEAMB0GA1UdDgQWBBStfadW0wTu5E3J1mEa6qz8/tY9hzAfBgNVHSMEGDAW"
            + "gBRamWI18y6lNATPS13vQUMAXLi9zTANBgkqhkiG9w0BAQUFAAOCAQEAFYmn/VQf"
            + "Fk6LmgXJFhzqNeGo9MmlBb8d/9x9ooXz02pa8gGxnbYz3LmtzGQbf1R2vAwlEHTb"
            + "lNWBfjkhTQ9jz1IpdqlD8YsS7cJtQsOecnA7Yev1BUqisxrDeHnZP5UIxOSaTIOd"
            + "rl16YS5uhdHua63WpV2Da/HbhchKdIER/G6U5L4x2iQkLHFmYhbNqyrABtg3cw+f"
            + "eyWlZJXkPxVmsTn3AGmrDwefC4cjS4+QcB5ZyDAtl3494PqS4/fPC2y/+o8PjybK"
            + "YKG6gXKHbzYHuPeubCMZrOxrQouSRLqxFYBJ1urY1kb/jgfz9Xf1o4zlEa2U5LVU"
            + "I3xqsv1yM8JAKxuM8c3bu5LSXhqzhY/jpvuIiYo0"
                                                                          );
    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp.example.com

    private static byte[] KEYSTORE_1 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp1.example.com
    private static byte[] KEYSTORE_2 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp1.example.com, DNS:amqp2.example.com
    private static byte[] KEYSTORE_3 = DatatypeConverter.parseBase64Binary(
            "/u3+7QAAAAIAAAABAAAAAQABMQAAAUg2l8tcAAAFAzCCBP8wDgYKKwYBBAEqAhEB"
            +"AQUABIIE6+cDkIo+wYoKHcQlj0XPkJcrAjV3Gw9JGLQWgC2nF4EgcHUf/+642gag"
            +"Gy4OBlwXwxhLOtB0D2vEIdKoeD5npFwHbwbkE4uflQRmEMx0M2mlFsLf83Uzoiar"
            +"UgXoXvf6oJ4IrVLaHfKdrfnJQ5kRz3ege6kkRI4UecI1EXcdJNzrSfuJHorSEieW"
            +"trTMCTVwqdrQkz0GwRtcY0b1S4rbrUgysnkVGg+nmsME3ou+rela0J0JSN+KBdSn"
            +"3apebBe0t+Ru1/JaDp8cKeoACj8ibyTIUj/MOAJ2fmV7fHGenCLmRdz6mLrJ6QDK"
            +"zClc3KH+pJBZL79pyRr2qjRRNgnVVruoMBV2PZ3MFa3bsB4GLr/b2lBfyNVaudtJ"
            +"3UVW3KbeIULIWBLlI9OKD5IGUJmmJA+U+vFyMppdZ88TmNPieyRB5huxG5gu61pW"
            +"BnerxmKuj5ZZk+FG4K4albOZDs5Lf+zROyjeb/gouIQd80N1ksB8yPXQUfO/mLLo"
            +"1MsFYfNcGrZZ//vdtadjgiw4E0gJ+s9dKhZERKwJ4Aknb6NXaDBR8bGkqc1l2hpv"
            +"9jk0J2KWn9aDe96iz2BgWtwXfYBK0dnG9kmn+LlSDDTmGOe6APzZn8nu53sP/Oz7"
            +"HayxzM0hNi0hi7V/fWMemoakbs0bhHY6EWkBbzSdqdMYAECAGbfHD8xbnoqwUBTw"
            +"cUBw/lNlm69ls3iUhqXtPJpiVVSbJ0kEN9MYaiWzpmjwk6v/h2iWbo2ZOYh3PEPz"
            +"ZTcdPedlkfTbg5pF6ZI1pH7aK7ZLWgEUbQiIQUItg2oEA19njWiqx/Wc1Ay+TdD9"
            +"dtA5zTh/T/YuuS0NDMEmyaCPqEDj3NziZAEL3UfhtASkvXhMZwmS/dAzPHHfWD+T"
            +"tqXld6a7tSwK7jeypv5Ku7ujZd9MsCVRPkJFNYbC5f1GqBIBlwO0j7PRs6IUC+EJ"
            +"ZyTzsKjYVlSR/+Or67BlABDuPeVGIbjlWUTiERKLBgABCY7cvSEKS3qJWVDDBg3G"
            +"axkaen8ak7e8/QdxalQYcV8mopWu4Qed/SYTU0MzDL4jJlGPTGyPD1GCBWcjLkB7"
            +"CqgljrM5hlanqXFj/SM0urLNYzCWPUs6+lX13MPo4K9ZTU7RnPOtXWcxzD3NQvtb"
            +"7gkm3SCjklf0qA4fvwGoYnrVz+Bt+IDzJCjnESRRQd9SBeR5oyUrcYz6r3J+i1TD"
            +"ceIQ2ys9UC5qc5JIc4+CQpXkyWjWmH5Mtz5+fi7coDX3sC9AdD125A7GTLgqXFk4"
            +"FsLHeC//BCBga05tX3LV6CL+Rdr605hDNbA/mgrmqH4t1XjM/eMwi85bzNnmvHBP"
            +"0VU5JFVuukylh8XNYu/qJGg+NO537XmxLIEjkiNCbE2e0mvTCPnaWB3YiO556iY6"
            +"5nFxNQx1ECvFhSdHd3WpFke85wP3fYOVtV7wL0+SC2JFNmmuvcH32GIS/bAd+DmA"
            +"vLB+Xn5Y3PnzWgAix0ZCX4ZZhQ04aFiwlgGHz6C78PCPF5bFxTEbjmImqJNgsnAm"
            +"1ZetK5WFQ1yrj925Xpd4ZLY81UFSZzKb9D9W8P9rqHnq8KjyAxuieGJyaaw8KLSu"
            +"ButbjmrkSxz5voj73gTr4DawY8EeAUly0F6SxacEjETHGc0+91EGPeH/O+DmKBKn"
            +"48UHMsXZ7tAk7l5i+6U1eZDUAAAAAgAFWC41MDkAAAQEMIIEADCCAuigAwIBAgIB"
            +"BTANBgkqhkiG9w0BAQUFADB6MRMwEQYKCZImiZPyLGQBGRYDb3JnMRYwFAYKCZIm"
            +"iZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQKDApTaW1wbGUgSW5jMRowGAYDVQQLDBFT"
            +"aW1wbGUgU2lnbmluZyBDQTEaMBgGA1UEAwwRU2ltcGxlIFNpZ25pbmcgQ0EwHhcN"
            +"MTQwOTAyMTEyODAwWhcNMTYwOTAxMTEyODAwWjBcMQswCQYDVQQGEwJVUzEQMA4G"
            +"A1UECAwHcHJpdmF0ZTERMA8GA1UEBwwIcHJvdmluY2UxDTALBgNVBAoMBGNpdHkx"
            +"GTAXBgNVBAMMEGFtcXAuZXhhbXBsZS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IB"
            +"DwAwggEKAoIBAQC8jh2z6+cNfv+CoWtPI3FiJ6Bl/rwPpT05Lje25KrtZQ8TBXuf"
            +"k8gOB9zSTlFxWNGUV5FrR+tuqzNSHmx6OtwYgdxsoy4aJ7eSxfxjNG8rAdrngn66"
            +"3pkWBtdBCyQLbV2jo95FnfAyTv5i76RJHDKNc6+GHvQnd2Q7KbKvXLt9aOD96cCI"
            +"fveWW6ZvlzCn1JOVBzwssJbHbQWEvnDS2LVDzD0+f9wN+Mmtj+yZ1fEGaAZ6qMOv"
            +"/ub2Q9wi31WxLLt+Jp75uP/CQz/g7fCOFIJ/cE20KB0P746IgTssU3LVxJvVfPL5"
            +"Fl5WgbzIgw7kVHjyQBMhfz/rzFGLFT5Wfkh5AgMBAAGjga4wgaswDgYDVR0PAQH/"
            +"BAQDAgWgMAkGA1UdEwQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC"
            +"MB0GA1UdDgQWBBTD7xOXn59GJ/f45r1z4HjRmSXktjAfBgNVHSMEGDAWgBStfadW"
            +"0wTu5E3J1mEa6qz8/tY9hzAvBgNVHREEKDAmghFhbXFwMS5leGFtcGxlLmNvbYIR"
            +"YW1xcDIuZXhhbXBsZS5jb20wDQYJKoZIhvcNAQEFBQADggEBAE/AZ1hFZWDUtVV1"
            +"QIpFbIZY831sDxx+gfieGLWmLgKX6x6zEAWfQcri6eyrCw9bZKivoaqbboQ4Y92S"
            +"oW+S+ztdiQVWi6bEzTGJqRNXj/8Dbc0Eii8OT+o+a3iUZi96zdgDf4F/v04KLrX0"
            +"/fEGJ7i3v4Z3xJwW/mDxJ9ihykHJrEmheI7GFsM93XecgLtboxq7qvi1tDPyXaMv"
            +"a9IQ8ouEr8+vFRlsgVuOOqqdLKvwptyiYdJCK7sz2PDGmWFvX7VRCsB2tFiCLged"
            +"D974qkBH8iNh0UK/25uZfbIbX6K1ejOJmQQ5oB+yn54eNFBU+0cm6p+/uvP7Wiur"
            +"Bh2TiPsABVguNTA5AAAD0zCCA88wggK3oAMCAQICAQYwDQYJKoZIhvcNAQEFBQAw"
            +"dDETMBEGCgmSJomT8ixkARkWA29yZzEWMBQGCgmSJomT8ixkARkWBnNpbXBsZTET"
            +"MBEGA1UECgwKU2ltcGxlIEluYzEXMBUGA1UECwwOU2ltcGxlIFJvb3QgQ0ExFzAV"
            +"BgNVBAMMDlNpbXBsZSBSb290IENBMB4XDTE0MDkwMjExMTgxN1oXDTI0MDkwMTEx"
            +"MTgxN1owejETMBEGCgmSJomT8ixkARkWA29yZzEWMBQGCgmSJomT8ixkARkWBnNp"
            +"bXBsZTETMBEGA1UECgwKU2ltcGxlIEluYzEaMBgGA1UECwwRU2ltcGxlIFNpZ25p"
            +"bmcgQ0ExGjAYBgNVBAMMEVNpbXBsZSBTaWduaW5nIENBMIIBIjANBgkqhkiG9w0B"
            +"AQEFAAOCAQ8AMIIBCgKCAQEA62nWa9VvAA/A5o84lwdFLLTl5yBIy/u0G1ng5aYF"
            +"+TKeMvPolfr8WiPMd5KNSHyrFWuUIHv1tDuL1QReH+gm7PSwc1M5UnliUj3RqloZ"
            +"9JwS6yIJzG0piMuJPWw97qOM6mSJq0Ya7mOBghrnPudTFoyA0wMf1HrWmIBZOhru"
            +"9tsiQZjsc2d1iZsgYwbyrwbALXrcnpxPX5F7YeFyRjoqkGM1tURoajqBRW4fdhgf"
            +"+f9KkB5GzLH4q4LsIwtFsByTK5wxqJYWxHfUqWuEujLQ2x0vzyntwtuw94pJJ3N7"
            +"1LQ4xpqU2H0goQRW4jSvlBpXJysDMgaZ1BDzVXYzKQ1QmwIDAQABo2YwZDAOBgNV"
            +"HQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUrX2nVtME"
            +"7uRNydZhGuqs/P7WPYcwHwYDVR0jBBgwFoAUWpliNfMupTQEz0td70FDAFy4vc0w"
            +"DQYJKoZIhvcNAQEFBQADggEBABWJp/1UHxZOi5oFyRYc6jXhqPTJpQW/Hf/cfaKF"
            +"89NqWvIBsZ22M9y5rcxkG39UdrwMJRB025TVgX45IU0PY89SKXapQ/GLEu3CbULD"
            +"nnJwO2Hr9QVKorMaw3h52T+VCMTkmkyDna5demEuboXR7mut1qVdg2vx24XISnSB"
            +"EfxulOS+MdokJCxxZmIWzasqwAbYN3MPn3slpWSV5D8VZrE59wBpqw8HnwuHI0uP"
            +"kHAeWcgwLZd+PeD6kuP3zwtsv/qPD48mymChuoFyh282B7j3rmwjGazsa0KLkkS6"
            +"sRWASdbq2NZG/44H8/V39aOM5RGtlOS1VCN8arL9cjPCQCvCXf4pd3xvKYuWrd8V"
            +"hGx16B8uYQ=="
                                                                          );


    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp1.example.com, DNS:*.example.com
    private static byte[] KEYSTORE_4 = DatatypeConverter.parseBase64Binary(
            "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"
            +"vx7F"
                                                                          );


    //        Subject: C=US, ST=private, L=province, O=city, CN=*.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp1.example.net, DNS:*.org
    private static byte[] KEYSTORE_5 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );


    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.com
    //            X509v3 Subject Alternative Name:
    //                DNS:*
    private static byte[] KEYSTORE_6 = DatatypeConverter.parseBase64Binary(
            "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"
            +"JJw3oAO8wr8AKRM7MuukYfesjw=="
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp.example.org, DNS:amqp1.example.org, DNS:amqp2.example.org
    private static byte[] KEYSTORE_7 = DatatypeConverter.parseBase64Binary(
            "/u3+7QAAAAIAAAABAAAAAQABMQAAAUg6/IKyAAAFAzCCBP8wDgYKKwYBBAEqAhEB"
            +"AQUABIIE6xPeRrIQPV5WZeUis8cSobRcuNJs0or2dJXdHeWJ04IEqEIjryGaM0mk"
            +"FgGn6pqFWldORIxRUs7Wuv8BDrkZdQvruzON0NU+ZMw2jb0SxmgezuZN0P7GqFFh"
            +"T/VCdo/6ACPYE35oruu/04ll+cNuXRUpOnle8UJO255+6gANjc2UXvePw78zhx97"
            +"4l5Oly5iJv15vyS3og1bn7DbMewzATC/kIzp7sOx1jyxgrGGS5+NtSMomovEtZC3"
            +"qFgEipt7yjncalnhD6xI6wIyoAILhgbVmzrTV8z0PQptlMIF5RdH4z3kwePw5De5"
            +"nr3mKb2bY7L7MiyWoTDdi+Gjpq1gXhoqF670Gw2bX3E4ekn7XByhFvpeT15eJ/SE"
            +"t9MeLEx1hjxEsdNdEucca7hCLBLr/7Msq/w9jytYEtRUBp7i9vKtjNEuE9eiisNB"
            +"JdyISKuXwilyGkHvpjy5kySkJqgLLPPa0WXbveNuoyTD8n2eU59EeFiut3/F5Irn"
            +"bj8c8gKHvGQUDlN/RW/OVr5dxVoysG5PBqNVcPfrzDBoo6ZNa6l3C76AoarWsqv8"
            +"meMSuIGBdhWVFaOP8tgspyoHZOX5MmcNuLcipLxlU533+B5fv0YSdEtcIqUxFMl3"
            +"PFJJ4XD8O+6Gw1fR/zLckJgXtwsuKbtHjFYe48oueJ/KyD2XxOmgpjIEcMDXVysK"
            +"2+p39G0Y47voLE5/aNLQJsokr0wk/EVj9Ibdmib7R/j18lsiOy8pvX3s/wkNVw5o"
            +"RjEYDBe8FDY2TfvJvfBu/s7WNVflv5h5ersF8BX4EvZFyN9ysw2mb07OSYbn1rG8"
            +"piFZUIGcnY5xLu9/TEAVV8eTudDWcn1S68C1B7URl6dQiSQQJ616JWI6+6/ho8TF"
            +"e0yO653JzXBMd5jfNLh/8BnfBHtW++7vHNK+444/vgaS242F2uyK6YgkD5oMu9ja"
            +"ao2W9oYGby0ewa9Cda2ghE1HRPbSgMQDGtpDr0dRauBFN6v6SuYNidfN/eTe1R08"
            +"sU3ReUHCReJssF7xRwSpb3MMd4tdL171OtOyjkU6w3RK/6omnaP12PGz0yibqXiK"
            +"42nUPNnGJ3P+BDW5XQmzvrlLCD+HFrU0Xez+5O1P7Q2LwcLp7f4bd6gc172Poflb"
            +"hCnbrb7iWawAb7Hwb1+V4v27TRA/Ws2Og7QkqKR0C3oxaCCORZVCXuPgMvRW5Y89"
            +"T1MDgN0d0d5I7N13xHqMu5azd5L3cHVN/pDguSgMDdWvvkVBgVI8kNz+yfoxlGEb"
            +"pHMef8wEzXZZG0Jii/C23TpFHAcVxhBtfldfUr0oAJapEeBE1JsAt7RDR8s0UQ7b"
            +"C4okMSyfX6SkxSu/AutWnD4pwgYo2q1r0vlC/o/F52Kujv6eeCo1U9wY52BCFxYi"
            +"t9GpqXpobMc+FpzHlIBoA5Rmf1hJaEJJ2/63/Rijlfey9UWVYsV5iJM0jttAvFoJ"
            +"qMN6dPTs3dNzhOQbzY4gcR4JyoUnPP4KErxL3TSYcnBzz0p5Sdhc/epngTWUVGt/"
            +"LwCgmy6yIF9zgEuqQFn98sYnhngWds9SKqGH7Aw4bEtb0cBebaSjM3101kigQXSa"
            +"KKqR7J/qxdcJofcuphCTE89hCarKNL4dvTIRp77B1Yyuu3Q7+iFIBIiT5G1Yfzxd"
            +"E1gSwzSOik45UGjcs8OJddFZAAAAAgAFWC41MDkAAAQHMIIEAzCCAuugAwIBAgIB"
            +"FTANBgkqhkiG9w0BAQUFADB6MRMwEQYKCZImiZPyLGQBGRYDb3JnMRYwFAYKCZIm"
            +"iZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQKDApTaW1wbGUgSW5jMRowGAYDVQQLDBFT"
            +"aW1wbGUgU2lnbmluZyBDQTEaMBgGA1UEAwwRU2ltcGxlIFNpZ25pbmcgQ0EwHhcN"
            +"MTQwOTAzMTAwODE1WhcNMTYwOTAyMTAwODE1WjBNMQswCQYDVQQGEwJVUzEQMA4G"
            +"A1UECAwHcHJpdmF0ZTERMA8GA1UEBwwIcHJvdmluY2UxDTALBgNVBAoMBGNpdHkx"
            +"CjAIBgNVBAMMASowggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCzVrHe"
            +"Ue2+O512CXzSWcG9z8T3JK0xONLPnGznMsYVBlz0uhSDdexxL25QlTGlJYdr5RMj"
            +"hdMXAH8xChRezVtQ9nFyRjjD7/b8FODObHNXKoqNHX6gOre5WlonhLi7xyVwpn5p"
            +"3K450jjkjaKZnadbk3zxAad4PSogNWNwRxKCRm4AFcPNFpN40vav+IACDcF8XC/p"
            +"/OcJdCvUXBblG1nY03jACQL1zAwJlc0z3SMoZ1Zozsnp+BJz26oXJ5HN+j9QFts9"
            +"JPqrxHSeG2G0NQP+dEOIzPDZn2k3aEuqEs3NW3ply7vWb2gtdgXF/vItITOr5q9S"
            +"fXCfWN0nzYsFtIyFAgMBAAGjgcAwgb0wDgYDVR0PAQH/BAQDAgWgMAkGA1UdEwQC"
            +"MAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBRhDip3"
            +"fzplRpkR+VTdmjVH9qoPtDAfBgNVHSMEGDAWgBStfadW0wTu5E3J1mEa6qz8/tY9"
            +"hzBBBgNVHREEOjA4ghBhbXFwLmV4YW1wbGUub3JnghFhbXFwMS5leGFtcGxlLm9y"
            +"Z4IRYW1xcDIuZXhhbXBsZS5vcmcwDQYJKoZIhvcNAQEFBQADggEBAArwhCSAi6sS"
            +"EO93CbmWNvwfKla7QH2wIiXwLrP5Bia5C9YHpDrCR1S7e4Is0YhroIqx5WUR4KMR"
            +"Gwn4rKqihJy4c1nuKSbYyPnN1eiZ+uSNUoTEAFv6g0oGFfslR+F5x6xZdObW1POx"
            +"l7wvSQWHxEFdAmCPgxkR47EuuZD3m8vqv/K/vZg8a802SfE3mUmZF1t1Vj+80xC9"
            +"/YUirCFERJMiurbOAZ6irqv1bxPkZp2S5pgpD2hEU5NdY3XINTUKxkjv/Opr9eS6"
            +"6VVTLikPZaFBRenFY7ibr4wVi9BOYeQ9dUQwkxmT0vjwazRpvLb4fxJqvhl+Nrc1"
            +"DTLvkOrHRsgABVguNTA5AAAD0zCCA88wggK3oAMCAQICAQYwDQYJKoZIhvcNAQEF"
            +"BQAwdDETMBEGCgmSJomT8ixkARkWA29yZzEWMBQGCgmSJomT8ixkARkWBnNpbXBs"
            +"ZTETMBEGA1UECgwKU2ltcGxlIEluYzEXMBUGA1UECwwOU2ltcGxlIFJvb3QgQ0Ex"
            +"FzAVBgNVBAMMDlNpbXBsZSBSb290IENBMB4XDTE0MDkwMjExMTgxN1oXDTI0MDkw"
            +"MTExMTgxN1owejETMBEGCgmSJomT8ixkARkWA29yZzEWMBQGCgmSJomT8ixkARkW"
            +"BnNpbXBsZTETMBEGA1UECgwKU2ltcGxlIEluYzEaMBgGA1UECwwRU2ltcGxlIFNp"
            +"Z25pbmcgQ0ExGjAYBgNVBAMMEVNpbXBsZSBTaWduaW5nIENBMIIBIjANBgkqhkiG"
            +"9w0BAQEFAAOCAQ8AMIIBCgKCAQEA62nWa9VvAA/A5o84lwdFLLTl5yBIy/u0G1ng"
            +"5aYF+TKeMvPolfr8WiPMd5KNSHyrFWuUIHv1tDuL1QReH+gm7PSwc1M5UnliUj3R"
            +"qloZ9JwS6yIJzG0piMuJPWw97qOM6mSJq0Ya7mOBghrnPudTFoyA0wMf1HrWmIBZ"
            +"Ohru9tsiQZjsc2d1iZsgYwbyrwbALXrcnpxPX5F7YeFyRjoqkGM1tURoajqBRW4f"
            +"dhgf+f9KkB5GzLH4q4LsIwtFsByTK5wxqJYWxHfUqWuEujLQ2x0vzyntwtuw94pJ"
            +"J3N71LQ4xpqU2H0goQRW4jSvlBpXJysDMgaZ1BDzVXYzKQ1QmwIDAQABo2YwZDAO"
            +"BgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUrX2n"
            +"VtME7uRNydZhGuqs/P7WPYcwHwYDVR0jBBgwFoAUWpliNfMupTQEz0td70FDAFy4"
            +"vc0wDQYJKoZIhvcNAQEFBQADggEBABWJp/1UHxZOi5oFyRYc6jXhqPTJpQW/Hf/c"
            +"faKF89NqWvIBsZ22M9y5rcxkG39UdrwMJRB025TVgX45IU0PY89SKXapQ/GLEu3C"
            +"bULDnnJwO2Hr9QVKorMaw3h52T+VCMTkmkyDna5demEuboXR7mut1qVdg2vx24XI"
            +"SnSBEfxulOS+MdokJCxxZmIWzasqwAbYN3MPn3slpWSV5D8VZrE59wBpqw8HnwuH"
            +"I0uPkHAeWcgwLZd+PeD6kuP3zwtsv/qPD48mymChuoFyh282B7j3rmwjGazsa0KL"
            +"kkS6sRWASdbq2NZG/44H8/V39aOM5RGtlOS1VCN8arL9cjPCQCtx4aFLK6xAD0Uj"
            +"jQ15cQCSY9N44A=="
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*
    //            X509v3 Subject Alternative Name:
    //                DNS:amqp.example.org, DNS:example.org
    private static byte[] KEYSTORE_8 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=amqp.example.org
    private static byte[] KEYSTORE_9 = DatatypeConverter.parseBase64Binary(
            "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"
            +"xAU="
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*.example.org
    private static byte[] KEYSTORE_10 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*.org
    private static byte[] KEYSTORE_11 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*.*.org
    private static byte[] KEYSTORE_12 = DatatypeConverter.parseBase64Binary(
            "/u3+7QAAAAIAAAABAAAAAQABMQAAAUg2l9u4AAAFAjCCBP4wDgYKKwYBBAEqAhEB"
            +"AQUABIIE6p4HqnSbHITrIX+wJDIMbnFuJiQMvi5ArsqwCBMMuBg7/BYlJxQECWBG"
            +"7EuxN/IuuhnexSeDxZReOtOZ4saM8k6Li5GlEFN/nVvZ/xrPOsG0Vo/bhCEUNp/w"
            +"4akQmpLIndJWGBrjCu46CH9p2h4hwRUOzeM4JYcyjv4R6jNL3EeMDdzHnuc1zv+t"
            +"tadcyPthFFAYT7eLZoGDTJiUy1l0GouAgGKMGSsyRUd3NyEwQx1NgRzx/tMNIkCV"
            +"AFFec5gzchLS8lR8mV9GJ2XQXOK0TKILfGK4/akKlphTGANiCU0q1U0kVJzAGg4j"
            +"gE22x2d2Sy/NQMxEOg8u8kGy97ISaWAvHPtpsZrFf1KqvbTWNjWgvFehDirk1xj+"
            +"lp79OnlkODK+Kb0m8lB2EnY1xj4jhkWu8Z/q4i3J/+rHbax9TFP55dGXLK0pwRv2"
            +"qf8ym57Fbk3QuS9jbf3/bwwXoCemAn2hhDHCBDBCQA9dTNDWt30gQ++7+3O28M0D"
            +"Z5/wicThCs+Ygn2recR6y4L2lZ/8g4amGsuVtaV2RZAwmrA8457PfH2t7tHySnMp"
            +"r6s2ynB6cep+t7DRO+LcDl6sSt6R1AoohsS2sw2/jhdwP2J5K96eiLcOp29aLFvR"
            +"lALJOohKOx7w0KWGtHPmVJlpkM4o9spmQ4va9BeQ4BXOSwFA+WJXSFO0AV7uL3ww"
            +"VXRcXG/yPAnKt1XaC2bhNfQnauBoOqqVlrOcK+5pyupPnaNvjTvdH5hxWkauSaIS"
            +"c7rmpoZOwBjsIx0yVZ5ZBnUq4Wx/b6iyAXBLypdPTJZtUz99h54dEAkKbh6VHDKl"
            +"Mgm2KwdaRJGjKzD7Lb4qSvSdLWViSV2+tJJry6CyI/mjd1XHqk0IIIroYtmInVqY"
            +"ZWJk8mhF38duWLVDQ9cmTmd3YGV1tnZ9RUiMQVxncFWcOl0HSe7cLtJqQhJkkfY9"
            +"GzpECa+KwqNMdNnpmSj0Esrcjvz/yqoZd6vG1yzLQ/+7yrdxe3HBqbdqTktyJ6Uy"
            +"kJdcVbYrQhi6EHLr+kYlffPKPH/fzi0zvgWDfSSkeYPNYpsdUbrPvNsprY4OkVlz"
            +"akej/MLSaYXNTcChgZXYU5RhWNhHlzbu+9mPmupViW+ypn6uLskz+Kst/7tUGtbn"
            +"6qU18y5ddGabUUCw7BnB0Hhpwzxi5JVuKECXW8zD0jcVS7YlMYJJhNo0XphM+am5"
            +"2I3Ftr3JTw8Vmz/vlWvFOQHzvaH8p9DUoEbxs1V82IF4YI6lT6q3JHrGZ8vFKLF9"
            +"Sm2O7BPts3s+dMKRkfzW+s2IitBq4pt0OfsjvMG+WPyB5/WWCfLU9KX5rcS+OQsC"
            +"LZuOZOt3sxYll7GvWR9ZybGfOo0aX6S11PSopiwM5qQspRwz1+8DBRQ4S2OgXKWf"
            +"syBvJTM0wXW/6omEf1A+427YLarQx3R5/fj66OV+2ntnR/gweTSHNn5bM+18N6Qn"
            +"ATzryEraXDmHOSR8bblz4Nx5B9YAb3KMUYA2DF8eIKf0zID78xkSP+JHFOBvZ5k/"
            +"jOCuaiX5ZGXQEKbDw2335odUU9CQsrzAljL8KFisZn1FDx6LULDZmXHJGVj8BIlr"
            +"guXS5KNqnvybv8n9aiuhFD6569G46vjizmKWMVws/S4GhBG6xZKVTC/9uf1VqKBU"
            +"jjtvrvbYDG1/0EME8Sdg9/8AAAACAAVYLjUwOQAAA8gwggPEMIICrKADAgECAgEP"
            +"MA0GCSqGSIb3DQEBBQUAMHoxEzARBgoJkiaJk/IsZAEZFgNvcmcxFjAUBgoJkiaJ"
            +"k/IsZAEZFgZzaW1wbGUxEzARBgNVBAoMClNpbXBsZSBJbmMxGjAYBgNVBAsMEVNp"
            +"bXBsZSBTaWduaW5nIENBMRowGAYDVQQDDBFTaW1wbGUgU2lnbmluZyBDQTAeFw0x"
            +"NDA5MDIxMzA4MDhaFw0xNjA5MDExMzA4MDhaMFMxCzAJBgNVBAYTAlVTMRAwDgYD"
            +"VQQIDAdwcml2YXRlMREwDwYDVQQHDAhwcm92aW5jZTENMAsGA1UECgwEY2l0eTEQ"
            +"MA4GA1UEAwwHKi4qLm9yZzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB"
            +"ALtU5Q4lwiOZCIZsjafk1mVJBeB9KqJwsRb61ONPnuj1aad8UoDGvR9DJ5gApG7W"
            +"5Lkp7LFcFKnpVGIHHVTdlLfsm8wKPxU8ykWZkl6zVSj4KxWMgem3jg+x7FDJFoyl"
            +"t+QEsFCzks3Tj+LSFQ2R3CJgH0UcYo+MMsxTrec9fLIhow/8gN/x9gIhfly8OMgX"
            +"Gz3TrsE0y2N4kRAGzSXmuZxO+VucuE0vGZnRTHs3OuS+b7mheDGbLbcFujdnumzb"
            +"RvjGd/3BhVjMQZWF6HvBxVCtoXyTAyH01WZhDyLbGkHimtaLqPuhOpaAZP2uchGk"
            +"8YbMOAJiNblKHqzf15H+kp8CAwEAAaN8MHowDgYDVR0PAQH/BAQDAgWgMAkGA1Ud"
            +"EwQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBQk"
            +"S82rAWM4EJl7eYTwwFk1I6kYZTAfBgNVHSMEGDAWgBStfadW0wTu5E3J1mEa6qz8"
            +"/tY9hzANBgkqhkiG9w0BAQUFAAOCAQEAN4s6hJ0DrHrJuJwfZ3u8l+jTuuuMGxVf"
            +"zDUYPJIBMVMIl18d92ujJW0RTDkElHZ8zbOVqBGajAWJ/kj6MQNzpqEYhsvJxxwX"
            +"sZ3xE5zOzeeTJEBlaBPzfGKsl4BPJmWljs2lwPbANivB6IZ7jcM1azt8Vqtb5KzJ"
            +"st99VGd4zMhi9rFWV7N7So2zWiERIjq2syNqjd0jQYS8uwp92IcaTGycxqJ+Kafz"
            +"8t3UiG07aU/ZZGSBcSMZjEAgnWzh5abqQNL3l2jKw7krpjDFbD/eIrYIV/jXaRzw"
            +"DmGgfKxktVvpXzmuQ9tdwOktCyrPsLqD/BxwsBJSldYV9tu/vnx/9AAFWC41MDkA"
            +"AAPTMIIDzzCCAregAwIBAgIBBjANBgkqhkiG9w0BAQUFADB0MRMwEQYKCZImiZPy"
            +"LGQBGRYDb3JnMRYwFAYKCZImiZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQKDApTaW1w"
            +"bGUgSW5jMRcwFQYDVQQLDA5TaW1wbGUgUm9vdCBDQTEXMBUGA1UEAwwOU2ltcGxl"
            +"IFJvb3QgQ0EwHhcNMTQwOTAyMTExODE3WhcNMjQwOTAxMTExODE3WjB6MRMwEQYK"
            +"CZImiZPyLGQBGRYDb3JnMRYwFAYKCZImiZPyLGQBGRYGc2ltcGxlMRMwEQYDVQQK"
            +"DApTaW1wbGUgSW5jMRowGAYDVQQLDBFTaW1wbGUgU2lnbmluZyBDQTEaMBgGA1UE"
            +"AwwRU2ltcGxlIFNpZ25pbmcgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK"
            +"AoIBAQDradZr1W8AD8DmjziXB0UstOXnIEjL+7QbWeDlpgX5Mp4y8+iV+vxaI8x3"
            +"ko1IfKsVa5Qge/W0O4vVBF4f6Cbs9LBzUzlSeWJSPdGqWhn0nBLrIgnMbSmIy4k9"
            +"bD3uo4zqZImrRhruY4GCGuc+51MWjIDTAx/UetaYgFk6Gu722yJBmOxzZ3WJmyBj"
            +"BvKvBsAtetyenE9fkXth4XJGOiqQYzW1RGhqOoFFbh92GB/5/0qQHkbMsfirguwj"
            +"C0WwHJMrnDGolhbEd9Spa4S6MtDbHS/PKe3C27D3ikknc3vUtDjGmpTYfSChBFbi"
            +"NK+UGlcnKwMyBpnUEPNVdjMpDVCbAgMBAAGjZjBkMA4GA1UdDwEB/wQEAwIBBjAS"
            +"BgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBStfadW0wTu5E3J1mEa6qz8/tY9"
            +"hzAfBgNVHSMEGDAWgBRamWI18y6lNATPS13vQUMAXLi9zTANBgkqhkiG9w0BAQUF"
            +"AAOCAQEAFYmn/VQfFk6LmgXJFhzqNeGo9MmlBb8d/9x9ooXz02pa8gGxnbYz3Lmt"
            +"zGQbf1R2vAwlEHTblNWBfjkhTQ9jz1IpdqlD8YsS7cJtQsOecnA7Yev1BUqisxrD"
            +"eHnZP5UIxOSaTIOdrl16YS5uhdHua63WpV2Da/HbhchKdIER/G6U5L4x2iQkLHFm"
            +"YhbNqyrABtg3cw+feyWlZJXkPxVmsTn3AGmrDwefC4cjS4+QcB5ZyDAtl3494PqS"
            +"4/fPC2y/+o8PjybKYKG6gXKHbzYHuPeubCMZrOxrQouSRLqxFYBJ1urY1kb/jgfz"
            +"9Xf1o4zlEa2U5LVUI3xqsv1yM8JAK44kbn5woKcmKSS6+GvVtU+kemw0"
                                                                          );

    //        Subject: C=US, ST=private, L=province, O=city, CN=*
    private static byte[] KEYSTORE_13 = DatatypeConverter.parseBase64Binary(
            "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"
                                                                          );


}
