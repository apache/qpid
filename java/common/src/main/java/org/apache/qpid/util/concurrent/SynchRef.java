package org.apache.qpid.util.concurrent;

/**
 * A SynchRef is an interface which is returned from the synchronous take and drain methods of {@link BatchSynchQueue},
 * allowing call-backs to be made against the synchronizing strucutre. It allows the consumer to communicate when it
 * wants producers that have their data taken to be unblocked.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Report number of records returned by a taking operation.
 * <tr><td> Provide call-back to release producers of taken records.
 * </table>
 */
public interface SynchRef
{
    /**
     * Reports the number of records taken by the take or drain operation.
     *
     * @return The number of records taken by the take or drain operation.
     */
    public int getNumRecords();

    /**
     * Any producers that have had their data elements taken from the queue but have not been unblocked are
     * unblocked when this method is called. The exception to this is producers that have had their data put back
     * onto the queue by a consumer. Producers that have had exceptions for their data items registered by consumers
     * will be unblocked but will not return from their put call normally, but with an exception instead.
     */
    public void unblockProducers();
}
