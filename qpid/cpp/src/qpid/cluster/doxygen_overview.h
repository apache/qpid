// This file contains doxygen documentation only.

/** @defgroup refactor Frame dispatch refactoring proposal.
 *
 * @section Overview
 * 
 * More consistent & flexible use of handler chains for frame dispatch
 * to enable clustering. Clustering needs to "intercept" the dispatch
 * process at various points.
 *
 * Qpid already has input and output handlers. This proposal would
 *  - Unify InputHandler and OutputHandler to a single FrameHandler.
 *  - Fold AMQFrame and AMQBody into a single Frame class hierarchy.
 *  - Configure different behavior by altering handler chain configuration.
 *  - Refactor request/reply numbering as a handler.
 *  - Dipatch concrete frame type with all frame data not just method args.
 *
 * Side/future benefits:
 *  - Generally simpler, cleaner and more flexibly broker architecture.
 *  - We can unify client and broker-side dispatch mechanisms.
 *  - Helps multi-version, separates version-specific/generic code.
 *  - Helps multi-protocol, can replace protocol handlers.
 *
 * @section Session
 * 
 * A Session holds information about the context in which frames
 * are processed. In 0-9 Sesions have 1-1 relationship with Channels.
 *
 * A Frame holds a pointer to the Session it is associated with.
 *
 * @section chains 0-9 handler chains
 *
 * Each Channel has an associated Session. A Session has two chains of
 * FrameHandlers for incoming and outgoing frames. A channel delivers
 * incoming frames to the Session::incoming() handler. Handlers are
 * per-session and may hold per-session state.
 *
 * For 0-9 the handlers are:
 * - SetSessionHandler: calls Frame::setSession(this)
 * - RequestResponseHandler: Update 0-9 request/response IDs.
 * - ProcessFrameHandler: Delegate to version-specific AMQP method handlers.
 * 
 * If an AMQP method handler wants to send frames back to the client it
 * uses the Session::outgoing() chain. For 0-9 that would be:
 * 
 * - SetSessionHandler: calls Frame::setSession(this)
 * - RequestResponseHandler: update 0-9 request/response IDs.
 * - ConnectionSendHandler: send frame to client via AMQP connection.
 * 
 * @section cluster Handler chains for clustering.
 * 
 * Initial cluster implementation will have a common broker model
 * synchronized across all nodes with CPG (Closed Process Group, part
 * of openais.)
 *
 * CPG guarantees ordrer of delivery with "self-delivery". A node
 * that sends a message cannot take actions associated with that
 * message until it is self-delivered via the CPG API.
 *
 * The sequence of events is:
 * - frame arrives via AMQP
 * - frame multicast via CPG
 * - frame delivered via CPG
 * - update the local model
 * - IF session is local then reply to client, else update local session model to indicate reply was sent.
 *
 * ClusterSession::incoming has a short chain:
 * - ReplicateOutHandler: multicast frame to cluster
 *
 * When a frame is (self-) delivered by CPG it is handed to the
 * ClusterSession::clusterIncoming() chain which is just like a 0-9
 * incoming chain.
 *
 * ClusterSession::outgoing depends on whether the session is local
 * (current node is primary) or not. For a local session it is just like
 * 0-9. For a remote session:
 * - SetSessionHandler: calls Frame::setSession(this)
 * - RequestResponseHandler: update 0-9 request/response IDs.
 * - UpdateSessionHandler: update local session replica.
 *
 * Some important details omitted: esp correspondence between
 * connections, Channels, Sessions and channel-ids/session-ids.
 * Multicast frames will be accompanied by a cluster-wide unique
 * session identifier so nodes identify the correct Session object.
 *
 * Note that although this is not our final goal for clustering, the
 * final goal can be reached by implementing additional handlers (e.g.
 * PointToPointBackupHandler) and adding logic to choose the right handlers
 * based on configuration of primary, backup etc.
 *
 * We will retain multicast backup for wiring, so the above will still apply
 * to queue.declare et. al.
 *
 * We will probably need a Cluster class for inter-node communication at some
 * point.
 * 
 * @section methodcontext MethodContext and method handlers refactor.
 *
 * Replace MethodContext with Frame in handler signatures to give
 * handlers full access to frame data.
 *
 * This actually makes all the other arguments redundant. Getting rid
 * of them would open some interesting template possibilities that
 * would help multi-version but it's not critical for clustering.
 *
 */


class Frame;

/** @brief Common interface for frame handlers. @ingroup refactor */
class FrameHandler {
  public:
    virtual ~FrameHandler() {}
    /** Handle a frame */
    void handle(Frame&)=0;
};


/** @brief Represents a Session. Much omitted. @ingroup refactor */
class Session {
    /** Handler to use for frames arriving from the client in this session. */
    FrameHandler& incoming();
    
    /** Handler to use if frames are sent to the client in this session */
    FrameHandler& outgoing();
};

/** @brief Cluster session with extra chains. 
 *@ingroup refactor
 */
class ClusterSession : pulic Session {
    /** Handler to use for frames delivered by cluster mutlicast */
    FrameHandler& clusterIncoming();
};

/** @brief New Frame methods. @ingroup refactor
 * Combine AMQFrame and AMQBody into a single Frame class.
 * (Existing AMQFrame and AMQBody methods not shown.)
 * 
 * Rationale:
 *  - Remove one memory allocation per frame for frame body.
 *  - All frame data available in base class of frame hierarchy.
 *
 * Add getSession()
*/
class Frame {
  public:

    /** If true, this frame was received from the client,
     * else this frame is being sent to the client.
     */
    bool isFromClient() const;

    /** @return Session associated with this frame. */
    Session* getSession() const;

    /** Set the session associated with this frame */
    void setSession(Session*);
};

