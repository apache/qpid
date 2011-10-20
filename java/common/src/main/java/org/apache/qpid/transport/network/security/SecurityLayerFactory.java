package org.apache.qpid.transport.network.security;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.*;
import org.apache.qpid.transport.network.security.sasl.SASLReceiver;
import org.apache.qpid.transport.network.security.sasl.SASLSender;
import org.apache.qpid.transport.network.security.ssl.SSLReceiver;
import org.apache.qpid.transport.network.security.ssl.SSLSender;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

public class SecurityLayerFactory
{
    public static SecurityLayer newInstance(ConnectionSettings settings)
    {

        SecurityLayer layer = NullSecurityLayer.getInstance();

        if (settings.isUseSSL())
        {
            layer = new SSLSecurityLayer(settings, layer);
        }
        if (settings.isUseSASLEncryption())
        {
            layer = new SASLSecurityLayer(layer);
        }

        return layer;

    }

    static class SSLSecurityLayer implements SecurityLayer
    {

        private final SSLEngine _engine;
        private final SSLStatus _sslStatus = new SSLStatus();
        private String _hostname;
        private SecurityLayer _layer;


        public SSLSecurityLayer(ConnectionSettings settings, SecurityLayer layer)
        {

            SSLContext sslCtx;
            _layer = layer;
            try
            {
                sslCtx = SSLContextFactory
                        .buildClientContext(settings.getTrustStorePath(),
                                settings.getTrustStorePassword(),
                                settings.getTrustStoreCertType(),
                                settings.getKeyStorePath(),
                                settings.getKeyStorePassword(),
                                settings.getKeyStoreCertType(),
                                settings.getCertAlias());
            }
            catch (Exception e)
            {
                throw new TransportException("Error creating SSL Context", e);
            }

            if(settings.isVerifyHostname())
            {
                _hostname = settings.getHost();
            }

            try
            {
                _engine = sslCtx.createSSLEngine();
                _engine.setUseClientMode(true);
            }
            catch(Exception e)
            {
                throw new TransportException("Error creating SSL Engine", e);
            }

        }

        public Sender<ByteBuffer> sender(Sender<ByteBuffer> delegate)
        {
            SSLSender sender = new SSLSender(_engine, _layer.sender(delegate), _sslStatus);
            sender.setHostname(_hostname);
            return sender;
        }

        public Receiver<ByteBuffer> receiver(Receiver<ByteBuffer> delegate)
        {
            SSLReceiver receiver = new SSLReceiver(_engine, _layer.receiver(delegate), _sslStatus);
            receiver.setHostname(_hostname);
            return receiver;
        }

        public String getUserID()
        {
            return SSLUtil.retriveIdentity(_engine);
        }
    }


    static class SASLSecurityLayer implements SecurityLayer
    {

        private SecurityLayer _layer;

        SASLSecurityLayer(SecurityLayer layer)
        {
            _layer = layer;
        }

        public SASLSender sender(Sender<ByteBuffer> delegate)
        {
            SASLSender sender = new SASLSender(_layer.sender(delegate));
            return sender;
        }

        public SASLReceiver receiver(Receiver<ByteBuffer> delegate)
        {
            SASLReceiver receiver = new SASLReceiver(_layer.receiver(delegate));
            return receiver;
        }

        public String getUserID()
        {
            return _layer.getUserID();
        }
    }


    static class NullSecurityLayer implements SecurityLayer
    {

        private static final NullSecurityLayer INSTANCE = new NullSecurityLayer();

        private NullSecurityLayer()
        {
        }

        public Sender<ByteBuffer> sender(Sender<ByteBuffer> delegate)
        {
            return delegate;
        }

        public Receiver<ByteBuffer> receiver(Receiver<ByteBuffer> delegate)
        {
            return delegate;
        }

        public String getUserID()
        {
            return null;
        }

        public static NullSecurityLayer getInstance()
        {
            return INSTANCE;
        }
    }
}
