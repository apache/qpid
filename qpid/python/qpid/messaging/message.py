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

from qpid.codec010 import StringCodec
from qpid.ops import PRIMITIVE

def codec(name):
  type = PRIMITIVE[name]

  def encode(x):
    sc = StringCodec()
    sc.write_primitive(type, x)
    return sc.encoded

  def decode(x):
    sc = StringCodec(x)
    return sc.read_primitive(type)

  return encode, decode

# XXX: need to correctly parse the mime type and deal with
# content-encoding header

TYPE_MAPPINGS={
  dict: "amqp/map",
  list: "amqp/list",
  unicode: "text/plain; charset=utf8",
  unicode: "text/plain",
  buffer: None,
  str: None,
  None.__class__: None
  }

DEFAULT_CODEC = (lambda x: x, lambda x: x)

def encode_text_plain(x):
  if x is None:
    return None
  else:
    return x.encode("utf8")

def decode_text_plain(x):
  if x is None:
    return None
  else:
    return x.decode("utf8")

TYPE_CODEC={
  "amqp/map": codec("map"),
  "amqp/list": codec("list"),
  "text/plain; charset=utf8": (encode_text_plain, decode_text_plain),
  "text/plain": (encode_text_plain, decode_text_plain),
  "": DEFAULT_CODEC,
  None: DEFAULT_CODEC
  }

def get_type(content):
  return TYPE_MAPPINGS[content.__class__]

def get_codec(content_type):
  return TYPE_CODEC.get(content_type, DEFAULT_CODEC)

UNSPECIFIED = object()

class Message:

  """
  A message consists of a standard set of fields, an application
  defined set of properties, and some content.

  @type id: str
  @ivar id: the message id
  @type subject: str
  @ivar subject: message subject
  @type user_id: str
  @ivar user_id: the user-id of the message producer
  @type reply_to: str
  @ivar reply_to: the address to send replies
  @type correlation_id: str
  @ivar correlation_id: a correlation-id for the message
  @type durable: bool
  @ivar durable: message durability
  @type priority: int
  @ivar priority: message priority
  @type ttl: float
  @ivar ttl: time-to-live measured in seconds
  @type properties: dict
  @ivar properties: application specific message properties
  @type content_type: str
  @ivar content_type: the content-type of the message
  @type content: str, unicode, buffer, dict, list
  @ivar content: the message content
  """

  def __init__(self, content=None, content_type=UNSPECIFIED, id=None,
               subject=None, user_id=None, reply_to=None, correlation_id=None,
               durable=None, priority=None, ttl=None, properties=None):
    """
    Construct a new message with the supplied content. The
    content-type of the message will be automatically inferred from
    type of the content parameter.

    @type content: str, unicode, buffer, dict, list
    @param content: the message content

    @type content_type: str
    @param content_type: the content-type of the message
    """
    self.id = id
    self.subject = subject
    self.user_id = user_id
    self.reply_to = reply_to
    self.correlation_id = correlation_id
    self.durable = durable
    self.priority = priority
    self.ttl = ttl
    self.redelivered = False
    if properties is None:
      self.properties = {}
    else:
      self.properties = properties
    if content_type is UNSPECIFIED:
      self.content_type = get_type(content)
    else:
      self.content_type = content_type
    self.content = content

  def __repr__(self):
    args = []
    for name in ["id", "subject", "user_id", "reply_to", "correlation_id",
                 "priority", "ttl"]:
      value = self.__dict__[name]
      if value is not None: args.append("%s=%r" % (name, value))
    for name in ["durable", "redelivered", "properties"]:
      value = self.__dict__[name]
      if value: args.append("%s=%r" % (name, value))
    if self.content_type != get_type(self.content):
      args.append("content_type=%r" % self.content_type)
    if self.content is not None:
      if args:
        args.append("content=%r" % self.content)
      else:
        args.append(repr(self.content))
    return "Message(%s)" % ", ".join(args)

class Disposition:

  def __init__(self, type, **options):
    self.type = type
    self.options = options

  def __repr__(self):
    args = [str(self.type)] + \
        ["%s=%r" % (k, v) for k, v in self.options.items()]
    return "Disposition(%s)" % ", ".join(args)

__all__ = ["Message", "Disposition"]
