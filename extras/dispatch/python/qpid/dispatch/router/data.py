#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#


try:
    from dispatch import *
except ImportError:
    from ..stubs import *


def getMandatory(data, key, cls=None):
    """
    Get the value mapped to the requested key.    If it's not present, raise an exception.
    """
    if key in data:
        value = data[key]
        if cls and value.__class__ != cls:
            raise Exception("Protocol field has wrong data type: '%s' type=%r expected=%r" % (key, value.__class__, cls))
        return value
    raise Exception("Mandatory protocol field missing: '%s'" % key)


def getOptional(data, key, default=None, cls=None):
    """
    Get the value mapped to the requested key.  If it's not present, return the default value.
    """
    if key in data:
        value = data[key]
        if cls and value.__class__ != cls:
            raise Exception("Protocol field has wrong data type: '%s' type=%r expected=%r" % (key, value.__class__, cls))
        return value
    return default


class LinkState(object):
    """
    The link-state of a single router.  The link state consists of a list of neighbor routers reachable from
    the reporting router.  The link-state-sequence number is incremented each time the link state changes.
    """
    def __init__(self, body, _id=None, _area=None, _ls_seq=None, _peers=None):
        self.last_seen = 0
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.ls_seq = getMandatory(body, 'ls_seq', long)
            self.peers = getMandatory(body, 'peers', list)
        else:
            self.id = _id
            self.area = _area
            self.ls_seq = long(_ls_seq)
            self.peers = _peers

    def __repr__(self):
        return "LS(id=%s area=%s ls_seq=%d peers=%r)" % (self.id, self.area, self.ls_seq, self.peers)

    def to_dict(self):
        return {'id'     : self.id,
                'area'   : self.area,
                'ls_seq' : self.ls_seq,
                'peers'  : self.peers}

    def add_peer(self, _id):
        if self.peers.count(_id) == 0:
            self.peers.append(_id)
            return True
        return False

    def del_peer(self, _id):
        if self.peers.count(_id) > 0:
            self.peers.remove(_id)
            return True
        return False

    def bump_sequence(self):
        self.ls_seq += 1


class MessageHELLO(object):
    """
    HELLO Message
    scope: neighbors only - HELLO messages travel at most one hop
    This message is used by directly connected routers to determine with whom they have
    bidirectional connectivity.
    """
    def __init__(self, body, _id=None, _area=None, _seen_peers=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.seen_peers = getMandatory(body, 'seen', list)
        else:
            self.id   = _id
            self.area = _area
            self.seen_peers = _seen_peers

    def __repr__(self):
        return "HELLO(id=%s area=%s seen=%r)" % (self.id, self.area, self.seen_peers)

    def get_opcode(self):
        return 'HELLO'

    def to_dict(self):
        return {'id'   : self.id,
                'area' : self.area,
                'seen' : self.seen_peers}

    def is_seen(self, _id):
        return self.seen_peers.count(_id) > 0


class MessageRA(object):
    """
    Router Advertisement (RA) Message
    scope: all routers in the area and all designated routers
    This message is sent periodically to indicate the originating router's sequence numbers
    for link-state and mobile-address-state.
    """
    def __init__(self, body, _id=None, _area=None, _ls_seq=None, _mobile_seq=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.ls_seq = getMandatory(body, 'ls_seq', long)
            self.mobile_seq = getMandatory(body, 'mobile_seq', long)
        else:
            self.id = _id
            self.area = _area
            self.ls_seq = long(_ls_seq)
            self.mobile_seq = long(_mobile_seq)

    def get_opcode(self):
        return 'RA'

    def __repr__(self):
        return "RA(id=%s area=%s ls_seq=%d mobile_seq=%d)" % \
                (self.id, self.area, self.ls_seq, self.mobile_seq)

    def to_dict(self):
        return {'id'         : self.id,
                'area'       : self.area,
                'ls_seq'     : self.ls_seq,
                'mobile_seq' : self.mobile_seq}


class MessageLSU(object):
    """
    """
    def __init__(self, body, _id=None, _area=None, _ls_seq=None, _ls=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.ls_seq = getMandatory(body, 'ls_seq', long)
            self.ls = LinkState(getMandatory(body, 'ls', dict))
        else:
            self.id = _id
            self.area = _area
            self.ls_seq = long(_ls_seq)
            self.ls = _ls

    def get_opcode(self):
        return 'LSU'

    def __repr__(self):
        return "LSU(id=%s area=%s ls_seq=%d ls=%r)" % \
                (self.id, self.area, self.ls_seq, self.ls)

    def to_dict(self):
        return {'id'     : self.id,
                'area'   : self.area,
                'ls_seq' : self.ls_seq,
                'ls'     : self.ls.to_dict()}


class MessageLSR(object):
    """
    """
    def __init__(self, body, _id=None, _area=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
        else:
            self.id = _id
            self.area = _area

    def get_opcode(self):
        return 'LSR'

    def __repr__(self):
        return "LSR(id=%s area=%s)" % (self.id, self.area)

    def to_dict(self):
        return {'id'     : self.id,
                'area'   : self.area}


class MessageMAU(object):
    """
    """
    def __init__(self, body, _id=None, _area=None, _seq=None, _add_list=None, _del_list=None, _exist_list=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.mobile_seq = getMandatory(body, 'mobile_seq', long)
            self.add_list = getOptional(body, 'add', None, list)
            self.del_list = getOptional(body, 'del', None, list)
            self.exist_list = getOptional(body, 'exist', None, list)
        else:
            self.id = _id
            self.area = _area
            self.mobile_seq = long(_seq)
            self.add_list = _add_list
            self.del_list = _del_list
            self.exist_list = _exist_list

    def get_opcode(self):
        return 'MAU'

    def __repr__(self):
        _add = ''
        _del = ''
        _exist = ''
        if self.add_list:   _add   = ' add=%r'   % self.add_list
        if self.del_list:   _del   = ' del=%r'   % self.del_list
        if self.exist_list: _exist = ' exist=%r' % self.exist_list
        return "MAU(id=%s area=%s mobile_seq=%d%s%s%s)" % \
                (self.id, self.area, self.mobile_seq, _add, _del, _exist)

    def to_dict(self):
        body = { 'id'         : self.id,
                 'area'       : self.area,
                 'mobile_seq' : self.mobile_seq }
        if self.add_list:   body['add']   = self.add_list
        if self.del_list:   body['del']   = self.del_list
        if self.exist_list: body['exist'] = self.exist_list
        return body


class MessageMAR(object):
    """
    """
    def __init__(self, body, _id=None, _area=None, _have_seq=None):
        if body:
            self.id = getMandatory(body, 'id', str)
            self.area = getMandatory(body, 'area', str)
            self.have_seq = getMandatory(body, 'have_seq', long)
        else:
            self.id = _id
            self.area = _area
            self.have_seq = long(_have_seq)

    def get_opcode(self):
        return 'MAR'

    def __repr__(self):
        return "MAR(id=%s area=%s have_seq=%d)" % (self.id, self.area, self.have_seq)

    def to_dict(self):
        return {'id'       : self.id,
                'area'     : self.area,
                'have_seq' : self.have_seq}

