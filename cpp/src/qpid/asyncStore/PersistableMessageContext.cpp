#include "PersistableMessageContext.h"

namespace qpid {
namespace asyncStore {

PersistableMessageContext::PersistableMessageContext(qpid::broker::AsyncStore* store) : m_store(store) {}

PersistableMessageContext::~PersistableMessageContext() {}

void
PersistableMessageContext::encode(qpid::framing::Buffer& /*buffer*/) const {}

uint32_t
PersistableMessageContext::encodedSize() const {
    return 0;
}

bool
PersistableMessageContext::isPersistent() const {
    return false;
}

void
PersistableMessageContext::decodeHeader(framing::Buffer& /*buffer*/) {}

void
PersistableMessageContext::decodeContent(framing::Buffer& /*buffer*/) {}

uint32_t
PersistableMessageContext::encodedHeaderSize() const {
    return 0;
}

boost::intrusive_ptr<qpid::broker::PersistableMessage> PersistableMessageContext::merge(const std::map<std::string, qpid::types::Variant>& /*annotations*/) const {
    boost::intrusive_ptr<qpid::broker::PersistableMessage> pmc;
    return pmc;
}

}} // namespace qpid::asyncStore
