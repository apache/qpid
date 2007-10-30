#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class CppGen
  def session_methods
    excludes = ["channel", "connection", "session", "execution"]
    gen_methods=@amqp.methods_on(@chassis).reject { |m|
      excludes.include? m.parent.name
    }
  end
end

class ContentField               # For extra content parameters
  def cppname() "content"  end
  def signature() "const MethodContent& content" end
  def unpack() "p[arg::content|DefaultContent(std::string())]"; end
end

class AmqpField
  def unpack() "p[arg::#{cppname}|#{cpptype.default_value}]"; end
end

class AmqpMethod
  def fields_c() content ? fields+[ContentField.new] : fields end
  def param_names_c() fields_c.map { |f| f.cppname} end
  def signature_c()  fields_c.map { |f| f.signature }; end
  def argpack_name() "#{parent.cppname}#{name.caps}Parameters"; end
  def argpack_type()
    "boost::parameter::parameters<" +
      fields_c.map { |f| "arg::keyword_tags::"+f.cppname }.join(',') +
      ">"
  end
  def return_type()
    return "TypedResult<qpid::framing::#{result.cpptype.ret}>" if (result)
    return "Response" if (not responses().empty?)
    return "Completion"
  end
  def session_function() "#{parent.name.lcaps}#{name.caps}"; end
end

class SessionNoKeywordGen < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
    @chassis="server"
    @namespace,@classname,@file=
      parse_classname "qpid::client::no_keyword::Session_#{@amqp.version.bars}"
  end

  def generate()
    h_file(@file) {
      include "qpid/framing/amqp_framing.h"
      include "qpid/framing/Uuid.h"
      include "qpid/framing/amqp_structs.h"
      include "qpid/framing/ProtocolVersion.h"
      include "qpid/framing/MethodContent.h"
      include "qpid/framing/TransferContent.h"
      include "qpid/client/Completion.h"
      include "qpid/client/ConnectionImpl.h"
      include "qpid/client/Response.h"
      include "qpid/client/SessionCore.h"
      include "qpid/client/TypedResult.h"
      include "qpid/shared_ptr.h"
      include "<string>"
      namespace("qpid::client") { 
        genl "using std::string;"
        genl "using framing::Content;"
        genl "using framing::FieldTable;"
        genl "using framing::MethodContent;"
        genl "using framing::SequenceNumberSet;"
        genl "using framing::Uuid;"
        genl
        namespace("no_keyword") { 
          cpp_class(@classname) {
            public
            gen <<EOS
#{@classname}();
framing::FrameSet::shared_ptr get() { return impl->get(); }
Uuid getId() const { return impl->getId(); }
void setSynchronous(bool sync) { impl->setSync(sync); } 
void suspend();
void close();
Execution& execution() { return impl->getExecution(); }

typedef framing::TransferContent DefaultContent;
EOS
          session_methods.each { |m|
            genl
            args=m.signature_c.join(", ") 
            genl "#{m.return_type} #{m.session_function}(#{args});" 
            }
            genl
            protected
            gen <<EOS
shared_ptr<SessionCore> impl;
framing::ProtocolVersion version;
friend class Connection;
#{@classname}(shared_ptr<SessionCore>);
EOS
          }}}}

    cpp_file(@file) { 
        include @classname
        include "qpid/framing/all_method_bodies.h"
        namespace(@namespace) {
        gen <<EOS
using namespace framing;
#{@classname}::#{@classname}() {}
#{@classname}::#{@classname}(shared_ptr<SessionCore> core) : impl(core) {}
void #{@classname}::suspend() { impl->suspend(); }
void #{@classname}::close() { impl->close(); }
EOS
        session_methods.each { |m|
          genl
          sig=m.signature_c.join(", ")
          func="#{@classname}::#{m.session_function}"
          scope("#{m.return_type} #{func}(#{sig}) {") {
            args=(["ProtocolVersion()"]+m.param_names).join(", ")
            body="#{m.body_name}(#{args})"
            sendargs=body
            sendargs << ", content" if m.content
            genl "return #{m.return_type}(impl->send(#{sendargs}), impl);"
          }}}}
  end
end

class SessionGen < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
    @chassis="server"
    session="Session_#{@amqp.version.bars}"
    @base="no_keyword::#{session}"
    @fqclass=FqClass.new "qpid::client::#{session}"
    @classname=@fqclass.name
    @fqbase=FqClass.new("qpid::client::#{@base}")
  end

  def gen_keyword_decl(m, prefix)
    return if m.fields_c.empty?     # Inherited function will do.
    scope("BOOST_PARAMETER_MEMFUN(#{m.return_type}, #{m.session_function}, 0, #{m.fields_c.size}, #{m.argpack_name}) {") {
      scope("return #{prefix}#{m.session_function}(",");") {
        gen m.fields_c.map { |f| f.unpack() }.join(",\n")
      }
    }
    genl
  end

  def generate()
    keyword_methods=session_methods.reject { |m| m.fields_c.empty? }
    max_arity = keyword_methods.map{ |m| m.fields_c.size }.max
    
    h_file(@fqclass.file) {
      include @fqbase.file
      genl
      genl "#define BOOST_PARAMETER_MAX_ARITY #{max_arity}"
      include "<boost/parameter.hpp>"
      genl
      namespace("qpid::client") {
        # Generate keyword tag declarations.
        namespace("arg") { 
          keyword_methods.map{ |m| m.param_names_c }.flatten.uniq.each { |k|
            genl "BOOST_PARAMETER_KEYWORD(keyword_tags, #{k})"
          }}
        genl
        cpp_class(@classname,"public #{@base}") {
          private
          genl "#{@classname}(shared_ptr<SessionCore> core) : #{ @base}(core) {}"
          keyword_methods.each { |m| typedef m.argpack_type, m.argpack_name }
          genl "friend class Connection;"
          public
          genl "#{@classname}() {}"
          keyword_methods.each { |m| gen_keyword_decl(m,@base+"::") }
       }}}
  end
end

SessionNoKeywordGen.new(ARGV[0], Amqp).generate()
SessionGen.new(ARGV[0], Amqp).generate()

