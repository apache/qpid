#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class SessionGen < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
    @chassis="server"
    @classname="Session"
  end
  
  def declare_method (m)
    gen "Response #{m.amqp_parent.name.lcaps}#{m.name.caps}(" 
    if (m.content())
      params=m.signature + ["const MethodContent& content"]
    else
      params=m.signature
    end
    indent { gen params.join(",\n") }
    gen ");\n\n"
  end

  def declare_class(c)
    c.amqp_methods_on(@chassis).each { |m| declare_method(m) }
  end

  def define_method (m)
    gen "Response Session::#{m.amqp_parent.name.lcaps}#{m.name.caps}(" 
    if (m.content())
      params=m.signature + ["const MethodContent& content"]
    else
      params=m.signature
    end
    indent { gen params.join(",\n") }
    gen "){\n\n"
    indent (2) { 
      gen "return impl->send(#{m.body_name}(" 
      params = ["version"] + m.param_names
      gen params.join(", ")
      other_params=[]
      if (m.content())
        other_params << "content"
      end
      if m.responses().empty?
        other_params << "false"
      else 
        other_params << "true"
      end
      gen "), #{other_params.join(", ")});\n"
    }
    gen "}\n\n"
  end

  def define_class(c)
    c.amqp_methods_on(@chassis).each { |m| define_method(m) }
  end

  def generate()
    excludes = ["channel", "connection", "session", "execution"]

    h_file("qpid/client/#{@classname}.h") { 
      gen <<EOS
#include <sstream> 
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Response.h"
#include "qpid/client/SessionCore.h"

namespace qpid {
namespace client {

using std::string;
using framing::Content;
using framing::FieldTable;
using framing::MethodContent;
using framing::SequenceNumberSet;

class #{@classname} {
  ConnectionImpl::shared_ptr parent;
  SessionCore::shared_ptr impl;
  framing::ProtocolVersion version;
public:
    #{@classname}(ConnectionImpl::shared_ptr, SessionCore::shared_ptr);
    ~#{@classname}();

    ReceivedContent::shared_ptr get() { return impl->get(); }
    void setSynchronous(bool sync) { impl->setSync(sync); } 
    void close();
EOS
  indent { @amqp.amqp_classes.each { |c| declare_class(c) if !excludes.include?(c.name) } }
  gen <<EOS
}; /* class #{@classname} */
}
}
EOS
}

  # .cpp file
  cpp_file("qpid/client/#{@classname}.cpp") { 
    gen <<EOS
#include "#{@classname}.h"
#include "qpid/framing/all_method_bodies.h"

using std::string;
using namespace qpid::framing;

namespace qpid {
namespace client {

#{@classname}::#{@classname}(ConnectionImpl::shared_ptr _parent, SessionCore::shared_ptr _impl) : parent(_parent), impl(_impl) {}

#{@classname}::~#{@classname}()
{
    impl->stop();
    if (parent) { 
        parent->released(impl);
        parent.reset();
    }
}

void #{@classname}::close()
{
    impl->close(); 
    if (parent) { 
        parent->released(impl);
        parent.reset();
    }
}

EOS

  @amqp.amqp_classes.each { |c| define_class(c) if !excludes.include?(c.name)  }
  
  gen <<EOS
}} // namespace qpid::client
EOS
  }

  end
end

SessionGen.new(ARGV[0], Amqp).generate()

