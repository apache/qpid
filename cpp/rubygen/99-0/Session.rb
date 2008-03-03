#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class CppGen
  def session_methods
    excludes = ["channel", "connection", "session", "execution", "connection010", "session010"]
    gen_methods=@amqp.methods_on(@chassis).reject { |m|
      excludes.include? m.parent.name
    }
  end

  def doxygen(m)
    doxygen_comment {
      genl m.doc
      genl
      m.fields_c.each { |f|
        genl "@param #{f.cppname}"
        genl f.doc if f.doc
        genl
      }
    }
  end
end

class ContentField               # For extra content parameters
  def cppname() "content"  end
  def signature() "const MethodContent& content" end
  def sig_default() signature+"="+"DefaultContent(std::string())" end
  def unpack() "p[arg::content|DefaultContent(std::string())]"; end
  def doc() "Message content"; end
end

class AmqpField
  def unpack() "p[arg::#{cppname}|#{cpptype.default_value}]"; end
  def sig_default() signature+"="+cpptype.default_value; end
end

class AmqpMethod
  def fields_c() content ? fields+[ContentField.new] : fields end
  def param_names_c() fields_c.map { |f| f.cppname} end
  def signature_c()  fields_c.map { |f| f.signature }; end
  def sig_c_default()  fields_c.map { |f| f.sig_default }; end
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
      include "qpid/client/SessionBase.h"

      namespace("qpid::client") { 
        genl "using std::string;"
        genl "using framing::Content;"
        genl "using framing::FieldTable;"
        genl "using framing::MethodContent;"
        genl "using framing::SequenceNumberSet;"
        genl "using framing::Uuid;"
        genl
        namespace("no_keyword") {
          doxygen_comment {
            genl "AMQP #{@amqp.version} session API."
            genl @amqp.class_("session").doc
          }
          cpp_class(@classname, "public SessionBase") {
            public
            genl "Session_#{@amqp.version.bars}() {}"
            genl "Session_#{@amqp.version.bars}(shared_ptr<SessionCore> core) : SessionBase(core) {}"
            session_methods.each { |m|
              genl
              doxygen(m)
              args=m.sig_c_default.join(", ") 
              genl "#{m.return_type} #{m.session_function}(#{args});" 
            }
          }}}}

    cpp_file(@file) { 
      include @classname
      include "qpid/framing/all_method_bodies.h"
      namespace(@namespace) {
        genl "using namespace framing;"
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
        # Doxygen comment.
        doxygen_comment {
          genl "AMQP #{@amqp.version} session API with keyword arguments."
          genl <<EOS
This class provides the same set of functions as #{@base}, but also
allows parameters be passed using keywords. The keyword is the
parameter name in the namespace "arg".

For example given the normal function "foo(int x=0, int y=0, int z=0)"
you could call it in either of the following ways:

@code
session.foo(1,2,3);             // Normal no keywords
session.foo(arg::z=3, arg::x=1); // Keywords and a default
@endcode

The keyword functions are easy to use but their declarations are hard
to read. You may find it easier to read the documentation for #{@base}
which provides the same set of functions using normal non-keyword
declarations.

\\ingroup clientapi
EOS
        }
        # Session class.
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

SessionNoKeywordGen.new(ARGV[0], $amqp).generate()
SessionGen.new(ARGV[0], $amqp).generate()

