#ifndef RDMA_EXCEPTION_H
#define RDMA_EXCEPTION_H

#include <exception>

#include <errno.h>
#include <string.h>

namespace Rdma {
    static __thread char s[50];
    class Exception : public std::exception {
        int err;

    public:
        Exception(int e) : err(e) {}
        int getError() { return err; }
        const char* what() const throw() {
            return ::strerror_r(err, s, 50);
        }
    };

    inline void THROW_ERRNO() {
        throw Rdma::Exception(errno);
    }

    inline void CHECK(int rc) {
        if (rc != 0)
            throw Rdma::Exception((rc == -1) ? errno : rc >0 ? rc : -rc);
    }

    inline void CHECK_IBV(int rc) {
        if (rc != 0)
            throw Rdma::Exception(rc);
    }

    template <typename T>
    inline
    T* CHECK_NULL(T* rc) {
        if (rc == 0)
            THROW_ERRNO();
        return rc;
    }
}

#endif // RDMA_EXCEPTION_H
