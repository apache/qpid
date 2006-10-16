#ifndef __UTIL_MEMORY__
#define __UTIL_MEMORY__

#if __GNUC__ < 4
  #include "boost/shared_ptr.hpp"
  namespace std {
  namespace tr1 {
    using boost::shared_ptr;
    using boost::dynamic_pointer_cast;
    using boost::static_pointer_cast;
  }
  }
#else
  #include <tr1/memory>
#endif
#endif

