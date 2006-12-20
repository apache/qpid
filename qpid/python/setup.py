#!/usr/bin/python
from distutils.core import setup

setup(name="qpid", version="0.1", packages=["qpid"], scripts=["amqp-doc"],
      url="http://incubator.apache.org/qpid",
      license="Apache Software License",
      description="Python language client implementation for Apache Qpid")
