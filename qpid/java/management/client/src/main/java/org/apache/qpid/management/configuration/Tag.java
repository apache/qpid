package org.apache.qpid.management.configuration;

/**
 * Configuration Tag catalogue.
 * 
 * @author Andrea Gazzarini
 */
public enum Tag {
	HANDLER { @Override public String toString() { return "handler"; }},
	MAPPING { @Override public String toString() { return "mapping"; }},
	CODE { @Override public String toString() { return "code"; }},
	CLASS_NAME { @Override public String toString() { return "class-name"; }},
	TYPE_MAPPINGS { @Override public String toString() { return "type-mappings"; }},
	ACCESS_MODE_MAPPINGS { @Override public String toString() { return "access-mode-mappings"; }},
	VALUE { @Override public String toString() { return "value"; }},
	CONFIGURATION { @Override public String toString() { return "configuration"; }},
	MESSAGE_HANDLERS { @Override public String toString() { return "message-handlers"; }},
	OPCODE { @Override public String toString() { return "opcode"; }},
	VALIDATOR_CLASS_NAME { @Override public String toString() { return "validator-class-name"; }},
	BROKER { @Override public String toString() { return "broker"; }},
	HOST { @Override public String toString() { return "host"; }},
	PORT { @Override public String toString() { return "port"; }},
    MAX_POOL_CAPACITY { @Override public String toString() { return "max-pool-capacity"; }},
    MAX_WAIT_TIMEOUT { @Override public String toString() { return "max-wait-timeout"; }},
    INITIAL_POOL_CAPACITY { @Override public String toString() { return "initial-pool-capacity"; }},    
	VIRTUAL_HOST { @Override public String toString() { return "virtual-host"; }},
	USER { @Override public String toString() { return "user"; }},
	PASSWORD { @Override public String toString() { return "password"; }},
	BROKERS { @Override public String toString() { return "brokers"; }};
	
	/**
	 * Returns the enum entry associated to the given tag name.
	 * 
	 * @param name the name of tag.
	 * @return the enum entry associated to the given tag name.
	 */
	public static Tag get(String name) {
		return valueOf(name.replaceAll("-", "_").toUpperCase());
	}
}