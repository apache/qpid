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
  
  def return_type(m)
    if (m.result)
      return "TypedResult<qpid::framing::#{m.result.cpptype.ret}>"
    elsif (not m.responses().empty?)
      return "Response"
    else
      return "Completion"
    end 
  end

  def declare_method (m)
    gen "#{return_type(m)} #{m.parent.name.lcaps}#{m.name.caps}(" 
    if (m.content())
      params=m.signature + ["const MethodContent& content"]
    else
      params=m.signature
    end
    indent { gen params.join(",\n") }
    gen ");\n\n"
  end

  def declare_class(c)
    c.methods_on(@chassis).each { |m| declare_method(m) }
  end

  def define_method (m)
    gen "#{return_type(m)} Session::#{m.parent.name.lcaps}#{m.name.caps}(" 
    if (m.content())
      params=m.signature + ["const MethodContent& content"]
    else
      params=m.signature
    end
    indent { gen params.join(",\n") }
    gen "){\n\n"
    indent (2) { 
      gen "return #{return_type(m)}(impl()->send(#{m.body_name}(" 
      params = ["version"] + m.param_names
      gen params.join(", ")
      other_params=[]
      if (m.content())
        gen "), content), impl());\n"
      else
        gen ")), impl());\n"
      end
    }
    gen "}\n\n"
  end

  def define_class(c)
    c.methods_on(@chassis).each { |m| define_method(m) }
  end

  def generate()
    excludes = ["channel", "connection", "session", "execution"]

    h_file("qpid/client/#{@classname}.h") { 
      gen <<EOS
#include <sstream> 
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/amqp_structs.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Response.h"
#include "qpid/client/ScopedAssociation.h"
#include "qpid/client/TypedResult.h"

namespace qpid {
namespace client {

using std::string;
using framing::Content;
using framing::FieldTable;
using framing::MethodContent;
using framing::SequenceNumberSet;

class #{@classname} {
  ScopedAssociation::shared_ptr assoc;
  framing::ProtocolVersion version;
  
  SessionCore::shared_ptr impl();

public:
    #{@classname}();
    #{@classname}(ScopedAssociation::shared_ptr);

    framing::FrameSet::shared_ptr get() { return impl()->get(); }
    void setSynchronous(bool sync) { impl()->setSync(sync); } 
    void close();
    Execution& execution() { return impl()->getExecution(); }

EOS
  indent { @amqp.classes.each { |c| declare_class(c) if !excludes.include?(c.name) } }
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

#{@classname}::#{@classname}() {}
#{@classname}::#{@classname}(ScopedAssociation::shared_ptr _assoc) : assoc(_assoc) {}

SessionCore::shared_ptr #{@classname}::impl()
{
    if (!assoc) throw Exception("Uninitialised session");
    return assoc->session;
}

void #{@classname}::close()
{
    impl()->close(); 
}

EOS

  @amqp.classes.each { |c| define_class(c) if !excludes.include?(c.name)  }
  
  gen <<EOS
}} // namespace qpid::client
EOS
  }

  end
end

SessionGen.new(ARGV[0], Amqp).generate()

