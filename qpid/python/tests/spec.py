from unittest import TestCase
from qpid.spec import load


class SpecTest(TestCase):

  def check_load(self, *urls):
    spec = load(*urls)
    qdecl = spec.method("queue_declare")
    assert qdecl != None
    assert not qdecl.content

    queue = qdecl.fields.byname["queue"]
    assert queue != None
    assert queue.domain.name == "queue_name"
    assert queue.type == "shortstr"

    qdecl_ok = spec.method("queue_declare_ok")

    # 0-8 is actually 8-0
    if (spec.major == 8 and spec.minor == 0 or
        spec.major == 0 and spec.minor == 9):
      assert qdecl_ok != None

      assert len(qdecl.responses) == 1
      assert qdecl_ok in qdecl.responses

    publish = spec.method("basic_publish")
    assert publish != None
    assert publish.content

    if (spec.major == 0 and spec.minor == 10):
      assert qdecl_ok == None
      reply_to = spec.domains.byname["reply_to"]
      assert reply_to.type.size == "short"
      assert reply_to.type.pack == "short"
      assert len(reply_to.type.fields) == 2

      qq = spec.method("queue_query")
      assert qq != None
      assert qq.result.size == "long"
      assert qq.result.type != None
      args = qq.result.fields.byname["arguments"]
      assert args.type == "table"

  def test_load_0_8(self):
    self.check_load("../specs/amqp.0-8.xml")

  def test_load_0_9(self):
    self.check_load("../specs/amqp.0-9.xml")

  def test_load_0_9_errata(self):
    self.check_load("../specs/amqp.0-9.xml", "../specs/amqp-errata.0-9.xml")

  def test_load_0_10(self):
    self.check_load("../specs/amqp.0-10-preview.xml")
