#include "rec_hdr.h"

void rec_hdr_init(rec_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag, const uint64_t rid) {
    dest->_magic = magic;
    dest->_version = version;
    dest->_uflag = uflag;
    dest->_rid = rid;
}

void rec_hdr_copy(rec_hdr_t* dest, const rec_hdr_t* src) {
    dest->_magic = src->_magic;
    dest->_version = src->_version;
    dest->_uflag = src->_uflag;
    dest->_rid = src->_rid;
}
