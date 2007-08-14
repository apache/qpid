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
    cpp_class(cname) {
          gen <<EOS          
ChannelAdapter& channel;

public:
#{cname}(ChannelAdapter& ch) : channel(ch) {}
virtual ~#{cname}() {}

static #{cname}& get(#{@classname}& proxy) { return proxy.get#{cname}(); }

EOS
      c.amqp_methods_on(@chassis).each { |m|
        genl "virtual void #{m.cppname}(#{m.signature.join(",\n            ")});"
        genl
      }}
  end

  def inner_class_defn(c)
    cname=c.cppname
    c.amqp_methods_on(@chassis).each { |m| 
      genl "void #{@classname}::#{cname}::#{m.cppname}(#{m.signature.join(", ")})"
      scope { 
        params=(["channel.getVersion()"]+m.param_names).join(", ")
        genl "channel.send(make_shared_ptr(new #{m.body_name}(#{params})));"
      }}
  end

  def generate
    # .h file
    h_file(@filename) {
      include "qpid/framing/Proxy.h"
      namespace("qpid::framing") { 
        cpp_class(@classname, "public Proxy") {
          public
          genl "#{@classname}(ChannelAdapter& ch);"
          genl
          @amqp.amqp_classes.each { |c|
            inner_class_decl(c)
            genl
            genl "#{c.cppname}& get#{c.cppname}() { return #{proxy_member(c)}; }"
            genl 
          }
          private
          @amqp.amqp_classes.each{ |c| gen c.cppname+" "+proxy_member(c)+";\n" }
        }}}

  # .cpp file
  cpp_file(@filename) {
      include "<sstream>"
      include "#{@classname}.h"
      include "qpid/framing/ChannelAdapter.h"
      include "qpid/framing/amqp_types_full.h"
      Amqp.amqp_methods_on(@chassis).each { |m| include "qpid/framing/"+m.body_name }
      genl
      namespace("qpid::framing") { 
        genl "#{@classname}::#{@classname}(ChannelAdapter& ch) :"
        gen "    Proxy(ch)"
        @amqp.amqp_classes.each { |c| gen ",\n    "+proxy_member(c)+"(channel)" }
        genl "{}\n"
        @amqp.amqp_classes.each { |c| inner_class_defn(c) }
      }}
   end
end


ProxyGen.new("client", Outdir, Amqp).generate;
ProxyGen.new("server", Outdir, Amqp).generate;
    
