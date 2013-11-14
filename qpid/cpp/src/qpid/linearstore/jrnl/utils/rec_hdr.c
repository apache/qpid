#include "rec_hdr.h"

void rec_hdr_init(rec_hdr_t* dest, const uint32_t magic, const uint16_t version, const uint16_t uflag, const uint64_t serial, const uint64_t rid) {
    dest->_magic = magic;
    dest->_version = version;
    dest->_uflag = uflag;
    dest->_serial = serial;
    dest->_rid = rid;
}

void rec_hdr_copy(rec_hdr_t* dest, const rec_hdr_t* src) {
    dest->_magic = src->_magic;
    dest->_version = src->_version;
    dest->_uflag = src->_uflag;
    dest->_serial = src->_serial;
    dest->_rid = src->_rid;
}

int rec_hdr_check_base(rec_hdr_t* header, const uint32_t magic, const uint16_t version) {
    if (header->_magic != magic) return 1;
    if (header->_version != version) return 2;
    return 0;
}

int rec_hdr_check(rec_hdr_t* header, const uint32_t magic, const uint16_t version, const uint64_t serial) {
    int res = rec_hdr_check_base(header, magic, version);
    if (res != 0) return res;
    if (header->_serial != serial) return 3;
    return 0;
}
