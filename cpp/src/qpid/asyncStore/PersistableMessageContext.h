#ifndef qpid_asyncStore_PersistableMessageContext_h_
#define qpid_asyncStore_PersistableMessageContext_h_

#include "qpid/broker/MessageHandle.h"
#include "qpid/broker/PersistableMessage.h"

namespace qpid {
namespace asyncStore {

class PersistableMessageContext: public qpid::broker::PersistableMessage {
private:
    qpid::broker::MessageHandle m_msgHandle;
    qpid::broker::AsyncStore* m_store;
public:
    PersistableMessageContext(qpid::broker::AsyncStore* store);
    virtual ~PersistableMessageContext();

    // --- Interface Persistable ---
    void encode(qpid::framing::Buffer& buffer) const;
    uint32_t encodedSize() const;

    // --- Class PersistableMessage ---
    bool isPersistent() const;
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer);
    uint32_t encodedHeaderSize() const;
    boost::intrusive_ptr<PersistableMessage> merge(const std::map<std::string, qpid::types::Variant>& annotations) const;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_PersistableMessageContext_h_
