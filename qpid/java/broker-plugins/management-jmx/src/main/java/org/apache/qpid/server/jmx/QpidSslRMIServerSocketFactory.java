package org.apache.qpid.server.jmx;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class QpidSslRMIServerSocketFactory extends SslRMIServerSocketFactory
{
    private final SSLContext _sslContext;

    /**
     * SslRMIServerSocketFactory which creates the ServerSocket using the
     * supplied SSLContext rather than the system default context normally
     * used by the superclass, allowing us to use a configuration-specified
     * key store.
     *
     * @param sslContext previously created sslContext using the desired key store.
     * @throws NullPointerException if the provided {@link SSLContext} is null.
     */
    public QpidSslRMIServerSocketFactory(SSLContext sslContext) throws NullPointerException
    {
        super();

        if(sslContext == null)
        {
            throw new NullPointerException("The provided SSLContext must not be null");
        }

        _sslContext = sslContext;

        //TODO: settings + implementation for SSL client auth, updating equals and hashCode appropriately.
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException
    {
        final SSLSocketFactory factory = _sslContext.getSocketFactory();

        return new ServerSocket(port)
        {
            public Socket accept() throws IOException
            {
                Socket socket = super.accept();

                SSLSocket sslSocket =
                    (SSLSocket) factory.createSocket(socket,
                                                     socket.getInetAddress().getHostName(),
                                                     socket.getPort(),
                                                     true);
                sslSocket.setUseClientMode(false);

                return sslSocket;
            }
        };
    }

    /**
     * One QpidSslRMIServerSocketFactory is equal to
     * another if their (non-null) SSLContext are equal.
     */
    @Override
    public boolean equals(Object object)
    {
        if (!(object instanceof QpidSslRMIServerSocketFactory))
        {
            return false;
        }

        QpidSslRMIServerSocketFactory that = (QpidSslRMIServerSocketFactory) object;

        return _sslContext.equals(that._sslContext);
    }

    @Override
    public int hashCode()
    {
        return _sslContext.hashCode();
    }

}
