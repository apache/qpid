#ifndef _framing_FramingContent_h
#define _framing_FramingContent_h

#include <ostream>

namespace qpid {
namespace framing {

enum discriminator_types { INLINE = 0, REFERENCE = 1 };

/**
 * A representation of the AMQP 'content' data type (used for message
 * bodies) which can hold inline data or a reference.
 */
class Content
{
    u_int8_t discriminator;
    string value;

    void validate();

 public:
    Content();
    Content(u_int8_t _discriminator, const string& _value);
    ~Content();
  
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);
    size_t size() const;
    bool isInline() { return discriminator == INLINE; }
    bool isReference() { return discriminator == REFERENCE; }
    const string& getValue() { return value; }

    friend std::ostream& operator<<(std::ostream&, const Content&);
};    

}} // namespace qpid::framing


#endif  /*!_framing_FramingContent_h*/
