package org.apache.qpid.nclient.transport;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.DefaultPhaseContext;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.PhaseContext;
import org.apache.qpid.nclient.core.PhaseFactory;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.pool.PoolingFilter;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.pool.ReferenceCountingExecutorService;

public class VMConnection implements TransportConnection
{
    private static final Logger _logger = Logger.getLogger(VMConnection.class);
    private BrokerDetails _brokerDetails;
    private IoConnector _ioConnector;
    private Phase _phase;
    private PhaseContext _ctx;
    
    protected VMConnection(ConnectionURL url,PhaseContext ctx)
    {
	_brokerDetails = url.getBrokerDetails(0);
	_ctx = ctx;
	
	_ioConnector = new VmPipeConnector();
        final IoServiceConfig cfg = _ioConnector.getDefaultConfig();
        ReferenceCountingExecutorService executorService = ReferenceCountingExecutorService.getInstance();
        PoolingFilter asyncRead = PoolingFilter.createAynschReadPoolingFilter(executorService,
                                                    "AsynchronousReadFilter");
        cfg.getFilterChain().addFirst("AsynchronousReadFilter", asyncRead);
        PoolingFilter asyncWrite = PoolingFilter.createAynschWritePoolingFilter(executorService, 
                                                     "AsynchronousWriteFilter");
        cfg.getFilterChain().addLast("AsynchronousWriteFilter", asyncWrite);
    }
    
    public Phase connect() throws AMQPException
    {		
	createVMBroker();	      
        
        _ctx.setProperty(QpidConstants.AMQP_BROKER_DETAILS,_brokerDetails);
        _ctx.setProperty(QpidConstants.MINA_IO_CONNECTOR,_ioConnector);
	
	_phase = PhaseFactory.createPhasePipe(_ctx);
	_phase.start();
	
	return _phase;

    }
    
    private void createVMBroker()throws AMQPException
    {
	_logger.info("Creating InVM Qpid.AMQP listening on port " + _brokerDetails.getPort());
	
	VmPipeAcceptor acceptor = new VmPipeAcceptor();
        IoServiceConfig config = acceptor.getDefaultConfig();
        config.setThreadModel(ReadWriteThreadModel.getInstance());
        
        IoHandlerAdapter provider = null;
        try
        {
            VmPipeAddress pipe = new VmPipeAddress(_brokerDetails.getPort());
            provider = createBrokerInstance(_brokerDetails.getPort());
            acceptor.bind(pipe, provider);
            _logger.info("Created InVM Qpid.AMQP listening on port " + _brokerDetails.getPort());
        }
        catch (IOException e)
        {
            _logger.error(e);
            VmPipeAddress pipe = new VmPipeAddress(_brokerDetails.getPort());
            acceptor.unbind(pipe);
                        
            throw new AMQPException("Error creating VM broker",e);
        }
    }
    
    private IoHandlerAdapter createBrokerInstance(int port) throws AMQPException
    {
        String protocolProviderClass = ClientConfiguration.get().getString(QpidConstants.QPID_VM_BROKER_CLASS);        
        _logger.info("Creating Qpid protocol provider: " + protocolProviderClass);

        // can't use introspection to get Provider as it is a server class.
        // need to go straight to IoHandlerAdapter but that requries the queues and exchange from the ApplicationRegistry which we can't access.

        //get correct constructor and pass in instancec ID - "port"
        IoHandlerAdapter provider;
        try
        {
            Class[] cnstr = {Integer.class};
            Object[] params = {port};
            provider = (IoHandlerAdapter) Class.forName(protocolProviderClass).getConstructor(cnstr).newInstance(params);
            //Give the broker a second to create
            _logger.info("Created VMBroker Instance:" + port);
        }
        catch (Exception e)
        {
            _logger.info("Unable to create InVM Qpid broker on port " + port + ". due to : " + e.getCause());
            _logger.error(e);
            String because;
            if (e.getCause() == null)
            {
                because = e.toString();
            }
            else
            {
                because = e.getCause().toString();
            }


            throw new AMQPException(port, because + " Stopped InVM Qpid.AMQP creation",e);
        }

        return provider;
    }

    public void close() throws AMQPException
    {
	
    }
    
    public Phase getPhasePipe()
    {
	return _phase;
    }
}
