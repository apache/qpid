#
# Generic AMQP code generation library.
#

# TODO aconway 2008-02-21:
#
# The amqp_attr_reader and amqp_child_reader for each Amqp* class
# should correspond exactly to ampq.dtd.  Currently they are more
# permissive so we can parse 0-10 preview and 0-10 final XML.
#
# Code marked with "# preview" should be removed/modified when final  0-10
# is complete and we are ready to remove preview-related code.
#

require 'delegate'
require 'rexml/document'
require 'pathname'
include REXML

# Handy String functions for converting names.
class String
  # Convert to CapitalizedForm.
  def caps() gsub( /(^|\W)(\w)/ ) { |m| $2.upcase } end

  # Convert to underbar_separated_form.
  def bars() tr('- .','_'); end

  # Convert to ALL_UPPERCASE_FORM
  def shout() bars.upcase!;  end

  # Convert to lowerCaseCapitalizedForm
  def lcaps() gsub( /\W(\w)/ ) { |m| $1.upcase } end

  def plural() self + (/[xs]$/ === self ? 'es' : 's'); end
end

# Sort an array by name.
module Enumerable
  def sort_by_name() sort { |a,b| a.name <=> b.name }; end
end

# Add functions similar to attr_reader for AMQP attributes/children.
# Symbols that are ruby Object function names (e.g. class) get
# an "_" suffix.
class Module
  # Add trailing _ to avoid conflict with Object methods.
  def mangle(sym)
    (Object.method_defined? sym) ? (sym.to_s+"_").intern : sym
  end

  # Add attribute reader for XML attribute.
  def amqp_attr_reader(*attrs)
    attrs.each { |a|
      case a
      when Symbol
        define_method(mangle(a)) {
          @amqp_attr_reader||={ }
          @amqp_attr_reader[a] ||= xml.attributes[a.to_s]
        }
      when Hash
        a.each { |attr, default|
          define_method(mangle(attr)) {
            @amqp_attr_reader||={ }
            value = xml.attributes[attr.to_s]
            if value
              @amqp_attr_reader[attr] ||= value
            else
              @amqp_attr_reader[attr] ||= default
            end
          }
        }
      end
    }
  end

  # Add 2 child readers:
  # elname(name) == child('elname',name)
  # elnames() == children('elname')
  def amqp_child_reader(*element_names)
    element_names.each { |e|
      define_method(mangle(e)) { |name| child(e.to_s, name) }
      define_method(mangle(e.to_s.plural)) { children(e.to_s) } }
  end

  # When there can only be one child instance 
  def amqp_single_child_reader(*element_names)
    element_names.each { |e|
      define_method(mangle(e)) { children(e.to_s)[0] } }
  end
end


# An AmqpElement contains an XML element and provides a convenient
# API to access AMQP data.
# 
# NB: AmqpElements cache values from XML, they assume that
# the XML model does not change after the AmqpElement has
# been created.
class AmqpElement

  def wrap(xml)
    return nil if ["assert","rule"].include? xml.name
    eval("Amqp"+xml.name.caps).new(xml, self) or raise "nil wrapper"
  end

  public
  
  def initialize(xml, parent)
    @xml, @parent=xml, parent
    @children=xml.elements.map { |e| wrap e }.compact
    @cache_child={}
    @cache_children={}
    @cache_children[nil]=@children
  end

  attr_reader :parent, :xml, :children, :doc
  amqp_attr_reader :name, :label

  # List of children of type elname, or all children if elname
  # not specified.
  def children(elname=nil)
    @cache_children[elname] ||= @children.select { |c| elname==c.xml.name }
  end

  # Look up child of type elname with attribute name.
  def child(elname, name)
    @cache_child[[elname,name]] ||= children(elname).find { |c| c.name==name }
  end

  # Fully qualified amqp dotted name of this element
  def dotted_name() (parent ? parent.dotted_name+"." : "") + name; end

  # The root <amqp> element.
  def root() @root ||=parent ? parent.root : self; end

  # Are we in preview or final 0-10
  # preview - used to make some classes behave differently for preview vs. final
  def final?() root().version == "0-10"; end 

  def to_s() "#<#{self.class}(#{name})>"; end
  def inspect() to_s; end

  # Text of doc child if there is one.
  def doc() d=xml.elements["doc"]; d and d.text; end

end

class AmqpResponse < AmqpElement
   def initialize(xml, parent) super; end
end

class AmqpDoc < AmqpElement
  def initialize(xml,parent) super; end
  def text() @xml.text end
end

class AmqpChoice < AmqpElement
  def initialize(xml,parent) super; end
  amqp_attr_reader :name, :value
end
  
class AmqpEnum < AmqpElement
  def initialize(xml,parent) super; end
  amqp_child_reader :choice
end

class AmqpDomain < AmqpElement
  def initialize(xml, parent) super; end
  amqp_attr_reader :type
  amqp_single_child_reader :struct # preview
  amqp_single_child_reader :enum

  def unalias()
    d=self
    while (d.type_ != d.name and root.domain(d.type_))
      d=root.domain(d.type_)
    end
    return d
  end
end

class AmqpException < AmqpElement
  def initialize(xml, amqp) super; end;
  amqp_attr_reader :error_code
end

class AmqpField < AmqpElement
  def initialize(xml, amqp) super; end;
  def domain() root.domain(xml.attributes["domain"]); end
  amqp_single_child_reader :struct # preview
  amqp_child_reader :exception
  amqp_attr_reader :type, :default, :code, :required
end

class AmqpChassis < AmqpElement # preview
  def initialize(xml, parent) super; end
  amqp_attr_reader :implement
end

class AmqpConstant < AmqpElement
  def initialize(xml, parent) super; end
  amqp_attr_reader :value, :class
end

class AmqpResult < AmqpElement
  def initialize(xml, parent) super; end
  amqp_single_child_reader :struct # preview
  amqp_attr_reader :type
end

class AmqpEntry < AmqpElement
  def initialize(xml,parent) super; end
  amqp_attr_reader :type
end

class AmqpHeader < AmqpElement
  def initialize(xml,parent) super; end
  amqp_child_reader :entry
  amqp_attr_reader :required
end

class AmqpBody < AmqpElement
  def initialize(xml,parent) super; end
  amqp_attr_reader :required  
end

class AmqpSegments < AmqpElement
  def initialize(xml,parent) super; end
  amqp_child_reader :header, :body
end

class AmqpStruct < AmqpElement
  def initialize(xml, parent) super; end
  amqp_attr_reader :type        # preview
  amqp_attr_reader :size, :code, :pack 
  amqp_child_reader :field

  # preview - preview code needs default "short" for pack.
  alias :raw_pack :pack
  def pack() raw_pack or (not parent.final? and "short"); end 
  def result?() parent.xml.name == "result"; end
  def domain?() parent.xml.name == "domain"; end
end

class AmqpMethod < AmqpElement
  def initialize(xml, parent) super; end

  amqp_attr_reader :content, :index, :synchronous
  amqp_child_reader :field, :chassis,:response
  amqp_single_child_reader :result

  def on_chassis?(chassis) child("chassis", chassis); end
  def on_client?() on_chassis? "client"; end
  def on_server?() on_chassis? "server"; end
end

class AmqpImplement < AmqpElement
  def initialize(xml,amqp) super; end
  amqp_attr_reader :handle, :send
end

class AmqpRole < AmqpElement
  def initialize(xml,amqp) super; end
  amqp_attr_reader :implement
end

class AmqpControl < AmqpElement
  def initialize(xml,amqp) super; end
  amqp_child_reader :implement, :field, :response
  amqp_attr_reader :code
end

class AmqpCommand < AmqpElement
  def initialize(xml,amqp) super; end
  amqp_child_reader :implement, :field, :exception, :response
  amqp_single_child_reader :result, :segments
  amqp_attr_reader :code
end

class AmqpClass < AmqpElement
  def initialize(xml,amqp) super; end

  amqp_attr_reader :index       # preview
  amqp_child_reader :method     # preview
  
  amqp_child_reader :struct, :domain, :control, :command, :role
  amqp_attr_reader :code

  # chassis should be "client" or "server"
  def methods_on(chassis)       # preview
    @methods_on ||= { }
    @methods_on[chassis] ||= methods_.select { |m| m.on_chassis? chassis }
  end

  def l4?()                     # preview
    !["connection", "session", "execution"].include?(name)
  end
end

class AmqpType < AmqpElement
  def initialize(xml,amqp) super; end
  amqp_attr_reader :code, :fixed_width, :variable_width
end

class AmqpXref < AmqpElement
  def initialize(xml,amqp) super; end
end

# AMQP root element.
class AmqpRoot < AmqpElement
  amqp_attr_reader :major, :minor, :port, :comment
  amqp_child_reader :doc, :type, :struct, :domain, :constant, :class

  def parse(filename) Document.new(File.new(filename)).root; end

  # Initialize with output directory and spec files from ARGV.
  def initialize(*specs)
    raise "No XML spec files." if specs.empty?
    xml=parse(specs.shift)
    specs.each { |s| xml_merge(xml, parse(s)) }
    super(xml, nil)
  end

  def version() major + "-" + minor; end

  # Find the element corresponding to an amqp dotted name.
  def lookup(dotted_name) xml.elements[dotted_name.gsub(/\./,"/")]; end
  
  # preview - only struct child reader remains for new mapping
  def domain_structs() domains.map{ |d| d.struct }.compact; end
  def result_structs()
    methods_.map { |m| m.result and m.result.struct }.compact
  end
  def structs() result_structs+domain_structs;  end
  
  def methods_() classes.map { |c| c.methods_ }.flatten; end

  # Return all methods on chassis for all classes.
  def methods_on(chassis)
    @methods_on ||= { }
    @methods_on[chassis] ||= classes.map { |c| c.methods_on(chassis) }.flatten
  end

  # TODO aconway 2008-02-21: methods by role.
  
  private
  
  # Merge contents of elements.
  def xml_merge(to,from)
    from.elements.each { |from_child|
      tag,name = from_child.name, from_child.attributes["name"]
      to_child=to.elements["./#{tag}[@name='#{name}']"]
      to_child ? xml_merge(to_child, from_child) : to.add(from_child.deep_clone) }
  end
end

# Collect information about generated files.
class GenFiles
  @@files =[]
  def GenFiles.add(f) @@files << f; end
  def GenFiles.get() @@files; end
end

# Base class for code generators.
# Supports setting a per-line prefix, useful for e.g. indenting code.
# 
class Generator
  # Takes directory for output or "-", meaning print file names that
  # would be generated.
  def initialize (outdir, amqp)
    @amqp=amqp
    @outdir=outdir
    @prefix=''                  # For indentation or comments.
    @indentstr='    '           # One indent level.
    @outdent=2
    Pathname.new(@outdir).mkpath unless @outdir=="-" or File.directory?(@outdir) 
  end

  # Create a new file, set @out. 
  def file(file)
    GenFiles.add file
    if (@outdir != "-")         
      path=Pathname.new "#{@outdir}/#{file}"
      path.parent.mkpath
      @out=String.new           # Generate in memory first
      yield                     # Generate to @out
      if path.exist? and path.read == @out  
        puts "Skipped #{path} - unchanged" # Dont generate if unchanged
      else
        path.open('w') { |f| f << @out }
        puts "Generated #{path}"
      end
    end
  end

  # Append multi-line string to generated code, prefixing each line.
  def gen (str)
    str.each_line { |line|
      @out << @prefix unless @midline
      @out << line
      @midline = nil
    }
    # Note if we stopped mid-line
    @midline = /[^\n]\z/ === str
  end

  # Append str + '\n' to generated code.
  def genl(str="")
    gen str
    gen "\n"
  end

  # Generate code with added prefix.
  def prefix(add)
    save=@prefix
    @prefix+=add
    yield
    @prefix=save
  end
  
  # Generate indented code
  def indent(n=1,&block) prefix(@indentstr * n,&block); end

  # Generate outdented code
  def outdent(&block)
    save=@prefix
    @prefix=@prefix[0...-2]
    yield
    @prefix=save
  end
  
  attr_accessor :out
end

