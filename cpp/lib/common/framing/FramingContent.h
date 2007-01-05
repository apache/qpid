#ifndef _framing_FramingContent_h
#define _framing_FramingContent_h

#include <ostream>

namespace qpid {
namespace framing {

/*
 * TODO: New Content class required for AMQP 0-9. This is a stub only.
 */
class Content
{
  public:
    ~Content();
  
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);
    size_t size() const;

  friend std::ostream& operator<<(std::ostream&, const Content&);
};    

}} // namespace qpid::framing


#endif  /*!_framing_FramingContent_h*/
