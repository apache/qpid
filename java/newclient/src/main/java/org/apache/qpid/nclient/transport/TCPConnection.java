package org.apache.qpid.nclient.transport;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.DefaultPhaseContext;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.PhaseContext;
import org.apache.qpid.nclient.core.PhaseFactory;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.pool.ReadWriteThreadModel;

public class TCPConnection implements TransportConnection
{
    private static final Logger _logger = Logger.getLogger(TCPConnection.class);
    private BrokerDetails _brokerDetails;
    private IoConnector _ioConnector;
    private Phase _phase;  
    
    protected TCPConnection(ConnectionURL url)
    {
	_brokerDetails = url.getBrokerDetails(0);
	
	ByteBuffer.setUseDirectBuffers(ClientConfiguration.get().getBoolean(QpidConstants.ENABLE_DIRECT_BUFFERS));

        // the MINA default is currently to use the pooled allocator although this may change in future
        // once more testing of the performance of the simple allocator has been done
        if (ClientConfiguration.get().getBoolean(QpidConstants.ENABLE_POOLED_ALLOCATOR))
        {
            // Not sure what the original code wanted use :)
        }
        else
        {
            _logger.info("Using SimpleByteBufferAllocator");
            ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        }

        final IoConnector ioConnector = new SocketConnector();
        SocketConnectorConfig cfg = (SocketConnectorConfig) ioConnector.getDefaultConfig();

        // if we do not use our own thread model we get the MINA default which is to use
        // its own leader-follower model
        if (ClientConfiguration.get().getBoolean(QpidConstants.USE_SHARED_READ_WRITE_POOL))
        {
            cfg.setThreadModel(ReadWriteThreadModel.getInstance());
        }

        SocketSessionConfig scfg = (SocketSessionConfig) cfg.getSessionConfig();
        scfg.setTcpNoDelay(ClientConfiguration.get().getBoolean(QpidConstants.TCP_NO_DELAY));
        scfg.setSendBufferSize(ClientConfiguration.get().getInt(QpidConstants.SEND_BUFFER_SIZE_IN_KB)*1024);
        scfg.setReceiveBufferSize(ClientConfiguration.get().getInt(QpidConstants.RECEIVE_BUFFER_SIZE_IN_KB)*1024);
    }

    // Returns the phase pipe
    public Phase connect() throws AMQPException
    {	
	PhaseContext ctx = new DefaultPhaseContext();
	ctx.setProperty(QpidConstants.AMQP_BROKER_DETAILS,_brokerDetails);
	ctx.setProperty(QpidConstants.MINA_IO_CONNECTOR,_ioConnector);
	
	_phase = PhaseFactory.createPhasePipe(ctx);
	_phase.start();
	
	return _phase;
    }

    public void close() throws AMQPException
    {
	
    }
    
    public Phase getPhasePipe()
    {
	return _phase;
    }
}
