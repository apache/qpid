package org.apache.qpidity.api;

import org.apache.qpidity.Header;
import org.apache.qpidity.Option;

/**
 * <p>This message listener is useful if u need to
 * know when each message part becomes available
 * as opposed to knowing when the whole message arrives.</p>
 *
 */
public interface StreamingMessageListener
{
	/**
     * Transfer the given message.
     * <p> Following are the valid options for messageTransfer
     * <ul>
     * <li> CONFIRM
     * <li> PRE_ACQUIRE
     * </ul>
     * </p>
     *
     * <p> In the absence of a particular option, the defaul value is:
     * <ul>
     * <li> CONFIRM = false
     * <li> NO-ACCQUIRE
     * </ul>
     * </p>
     * 
     * @param destination The exchange the message being sent.
     * @return options set of options
     * @throws QpidException If the session fails to send the message due to some error
     */
    public void messageTransfer(String destination,Option... options); 
    
    /**
     * Add the following headers to content bearing frame
     *
     * @param Header Either DeliveryProperties or ApplicationProperties
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void messageHeaders(Header ... headers);
    
    /**
     * Add the following byte array to the content.
     * This method is useful when streaming large messages
     *
     * @param src data to be added or streamed
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void data(byte[] src);
    
    /**
     * Signals the end of data for the message.     * 
     * This method is useful when streaming large messages
     *
     * @throws QpidException If the session fails to execute the method due to some error
     */    
    public void endData(); 
}
