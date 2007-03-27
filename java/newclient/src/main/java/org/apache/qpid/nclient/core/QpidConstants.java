package org.apache.qpid.nclient.core;

public interface QpidConstants {
	
    	// Common properties
	public static long EMPTY_CORRELATION_ID = -1;
	public static int CHANNEL_ZERO = 0;
	public static String CONFIG_FILE_PATH = "ConfigFilePath";
	
	// Phase Context properties
	public final static String AMQP_BROKER_DETAILS = "AMQP_BROKER_DETAILS";
	public final static String MINA_IO_CONNECTOR = "MINA_IO_CONNECTOR";
	//public final static String AMQP_MAJOR_VERSION = "AMQP_MAJOR_VERSION";
	//public final static String AMQP_MINOR_VERSION = "AMQP_MINOR_VERSION";
	//public final static String AMQP_SASL_CLIENT = "AMQP_SASL_CLIENT";
	//public final static String AMQP_CLIENT_ID = "AMQP_CLIENT_ID";
	//public final static String AMQP_CONNECTION_TUNE_PARAMETERS = "AMQP_CONNECTION_TUNE_PARAMETERS";
	//public final static String AMQP_VIRTUAL_HOST = "AMQP_VIRTUAL_HOST";	
	//public final static String AMQP_MESSAGE_STORE = "AMQP_MESSAGE_STORE";
	
	/**---------------------------------------------------------------
	 * 	Configuration file properties
	 * ------------------------------------------------------------
	*/
	
	// Model Layer properties
	public final static String STATE_MANAGER = "stateManager";
	public final static String METHOD_LISTENERS = "methodListeners";
	public final static String METHOD_LISTENER = "methodListener";
	public final static String CLASS = "[@class]";
	public final static String METHOD_CLASS = "methodClass";
	
	public final static String STATE_LISTENERS = "stateListeners";
	public final static String STATE_LISTENER = "stateListener";
	public final static String STATE_TYPE = "stateType";
	
	public final static String AMQP_MESSAGE_STORE_CLASS = "AMQP_MESSAGE_STORE_CLASS";
	public final static String SERVER_TIMEOUT_IN_MILLISECONDS = "serverTimeoutInMilliSeconds";

	// MINA properties
	public final static String USE_SHARED_READ_WRITE_POOL = "useSharedReadWritePool";
	public final static String ENABLE_DIRECT_BUFFERS = "enableDirectBuffers";
	public final static String ENABLE_POOLED_ALLOCATOR = "enablePooledAllocator";
	public final static String TCP_NO_DELAY = "tcpNoDelay";
	public final static String SEND_BUFFER_SIZE_IN_KB = "sendBufferSizeInKb";
	public final static String RECEIVE_BUFFER_SIZE_IN_KB = "reciveBufferSizeInKb";
	
	// Security properties
	public final static String AMQP_SECURITY_SASL_CLIENT_FACTORY_TYPES = "saslClientFactoryTypes";
	public final static String AMQP_SECURITY_SASL_CLIENT_FACTORY  = "saslClientFactory";
	public final static String TYPE = "[@type]";
	
	public final static String AMQP_SECURITY_MECHANISMS = "securityMechanisms";
	public final static String AMQP_SECURITY_MECHANISM_HANDLER = "securityMechanismHandler";
		
	// Execution Layer properties
	public final static String MAX_ACCUMILATED_RESPONSES = "maxAccumilatedResponses";
	
	//Transport Layer properties
	public final static String QPID_VM_BROKER_CLASS = "qpidVMBrokerClass";
	
	//Phase pipe properties
	public final static String PHASE_PIPE = "phasePipe";
	public final static String PHASE = "phase";
	public final static String INDEX = "[@index]";
}
