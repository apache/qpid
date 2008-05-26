#!/usr/bin/env ruby
# Usage: output_directory xml_spec_file [xml_spec_file...]
# 
$: << '..'
require 'cppgen'

class CppGen
  def session_methods
    excludes = ["connection", "session", "file", "stream"]
    gen_methods=@amqp.methods_on(@chassis).reject { |m|
      excludes.include? m.parent.name or m.body_name.include?("010")
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

# Sync vs. async APIs
module SyncAsync
  def sync_prefix() @async ? "Async" : ""  end
  def sync_adjective() @async ? "asynchronous" : "synchronous" end
  def sync_convert() @async ? "async" :  "sync" end


  def decl_ctor_opeq()
    genl
    genl "#{@classname}();"
    genl "#{@classname}(const #{@version_base}& other);"
    genl "#{@classname}& operator=(const #{@version_base}& other);"
  end

  def defn_ctor_opeq(inline="")
    genl
    genl "#{inline} #{@classname}::#{@classname}() {}"
    scope("#{inline} #{@classname}::#{@classname}(const #{@version_base}& other) {") {
      genl "*this = other;"
    }
    scope("#{inline} #{@classname}& #{@classname}::operator=(const #{@version_base}& other) {") {
      genl "impl = static_cast<const #{@classname}&>(other).impl;"
      genl "return *this;"
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

  def return_type(async)
    if (async)
      return "TypedResult<qpid::framing::#{result.cpptype.ret_by_val}>" if (result)
      return "Completion"
    else
      return "qpid::framing::#{result.cpptype.ret_by_val}" if (result)
      return "void"
    end
  end

  def session_function() "#{parent.name.lcaps}#{name.caps}"; end
end

class SessionNoKeywordGen < CppGen
  include SyncAsync
  
  def initialize(outdir, amqp, async)
    super(outdir, amqp)
    @async=async
    @chassis="server"
    @namespace,@classname,@file=
      parse_classname "qpid::client::no_keyword::#{sync_prefix}Session_#{@amqp.version.bars}"
    @version_base="SessionBase_#{@amqp.major}_#{@amqp.minor}"
  end

  def generate()
    h_file(@file) {
      include "qpid/client/#{@version_base}.h"
      namespace(@namespace) { 
        doxygen_comment {
          genl "AMQP #{@amqp.version} #{sync_adjective} session API."
          genl @amqp.class_("session").doc
          # FIXME aconway 2008-05-23: additional doc on sync/async use.
        }
        cpp_class(@classname, "public #{@version_base}") {
          public
          decl_ctor_opeq()
          session_methods.each { |m|
            genl
            doxygen(m)
            args=m.sig_c_default.join(", ") 
            genl "#{m.return_type(@async)} #{m.session_function}(#{args});" 
          }
        }
      }}

    cpp_file(@file) { 
      include @classname
      include "qpid/framing/all_method_bodies.h"
      namespace(@namespace) {
        genl "using namespace framing;"
        session_methods.each { |m|
          genl
          sig=m.signature_c.join(", ")
          func="#{@classname}::#{m.session_function}"
          scope("#{m.return_type(@async)} #{func}(#{sig}) {") {
            args=(["ProtocolVersion(#{@amqp.major},#{@amqp.minor})"]+m.param_names).join(", ")
            genl "#{m.body_name} body(#{args});";
            genl "body.setSync(#{@async ? 'false':'true'});"
            sendargs="body"
            sendargs << ", content" if m.content
            async_retval="#{m.return_type(true)}(impl->send(#{sendargs}), impl)"
            if @async then
              genl "return #{async_retval};"
            else
              if m.result
                genl "return #{async_retval}.get();"
              else
                genl "#{async_retval}.wait();"
              end
            end
          }}
        defn_ctor_opeq()
      }}
  end
end

class SessionGen < CppGen
  include SyncAsync

  def initialize(outdir, amqp, async)
    super(outdir, amqp)
    @async=async
    @chassis="server"
    session="#{sync_prefix}Session_#{@amqp.version.bars}"
    @base="no_keyword::#{session}"
    @fqclass=FqClass.new "qpid::client::#{session}"
    @classname=@fqclass.name
    @fqbase=FqClass.new("qpid::client::#{@base}")
    @version_base="SessionBase_#{@amqp.major}_#{@amqp.minor}"
  end

  def gen_keyword_decl(m)
    return if m.fields_c.empty?     # Inherited function will do.
    scope("BOOST_PARAMETER_MEMFUN(#{m.return_type(@async)}, #{m.session_function}, 0, #{m.fields_c.size}, #{m.argpack_name}) {") {
      scope("return #{@base}::#{m.session_function}(",");") {
        gen m.fields_c.map { |f| f.unpack() }.join(",\n")
      }
    }
    genl
  end

  def generate()
    keyword_methods=session_methods.reject { |m| m.fields_c.empty? }
    max_arity = keyword_methods.map{ |m| m.fields_c.size }.max

    h_file("qpid/client/arg.h") {
      # Generate keyword tag declarations.
      genl "#define BOOST_PARAMETER_MAX_ARITY #{max_arity}"
      include "<boost/parameter.hpp>"
      namespace("qpid::client::arg") { 
        keyword_methods.map{ |m| m.param_names_c }.flatten.uniq.each { |k|
          genl "BOOST_PARAMETER_KEYWORD(keyword_tags, #{k})"
        }}
    }    
    
    h_file(@fqclass.file) {
      include @fqbase.file
      include "qpid/client/arg"
      namespace("qpid::client") {
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
          public
          decl_ctor_opeq()
          private
          keyword_methods.each { |m| typedef m.argpack_type, m.argpack_name }
          genl "friend class Connection;"
          public
          keyword_methods.each { |m| gen_keyword_decl(m) }
        }
        genl "/** Conversion to #{@classname} from another session type */"
        genl "inline #{@classname} #{sync_convert}(const #{@version_base}& other) { return #{@clasname}(other); }"
        defn_ctor_opeq("inline")
      }}
  end
end

SessionNoKeywordGen.new(ARGV[0], $amqp, true).generate()
SessionNoKeywordGen.new(ARGV[0], $amqp, false).generate()
SessionGen.new(ARGV[0], $amqp, true).generate()
SessionGen.new(ARGV[0], $amqp, false).generate()

