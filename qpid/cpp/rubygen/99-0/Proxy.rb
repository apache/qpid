#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ProxyGen < CppGen
    
  def initialize(chassis, outdir, amqp)
    super(outdir, amqp)
    @chassis=chassis
    @classname="AMQP_#{@chassis.caps}Proxy"
    @filename="qpid/framing/#{@classname}"
  end

  def proxy_member(c) c.name.lcaps+"Proxy"; end
  
  def inner_class_decl(c)
    cname=c.name.caps
    cpp_class(cname, "Proxy") {
          gen <<EOS
public:
#{cname}(FrameHandler& f) : Proxy(f) {}
static #{cname}& get(#{@classname}& proxy) { return proxy.get#{cname}(); }
EOS
      c.methods_on(@chassis).each { |m|
        genl "virtual void #{m.cppname}(#{m.signature.join(",\n            ")});"
        genl
      }}
  end

  def inner_class_defn(c)
    cname=c.cppname
    c.methods_on(@chassis).each { |m| 
      genl "void #{@classname}::#{cname}::#{m.cppname}(#{m.signature.join(", ")})"
      scope { 
        params=(["getVersion()"]+m.param_names).join(", ")
        genl "send(#{m.body_name}(#{params}));"
      }}
  end

  def generate
    # .h file
    h_file(@filename) {
      include "qpid/framing/Proxy.h"
      include "qpid/framing/Array.h"
      include "qpid/framing/amqp_types.h"
      include "qpid/framing/amqp_structs.h"
      namespace("qpid::framing") { 
        cpp_class(@classname, "public Proxy") {
          public
          genl "#{@classname}(FrameHandler& out);"
          genl
          @amqp.classes.each { |c|
            inner_class_decl(c)
            genl
            genl "#{c.cppname}& get#{c.cppname}() { return #{proxy_member(c)}; }"
            genl 
          }
          private
          @amqp.classes.each{ |c| gen c.cppname+" "+proxy_member(c)+";\n" }
        }}}

  # .cpp file
  cpp_file(@filename) {
      include "<sstream>"
      include "#{@classname}.h"
      include "qpid/framing/amqp_types_full.h"
      @amqp.methods_on(@chassis).each {
        |m| include "qpid/framing/"+m.body_name
      }
      genl
      namespace("qpid::framing") { 
        genl "#{@classname}::#{@classname}(FrameHandler& f) :"
        gen "    Proxy(f)"
        @amqp.classes.each { |c| gen ",\n    "+proxy_member(c)+"(f)" }
        genl "{}\n"
        @amqp.classes.each { |c| inner_class_defn(c) }
      }}
   end
end


ProxyGen.new("client", $outdir, $amqp).generate;
ProxyGen.new("server", $outdir, $amqp).generate;
    
