The documentation in this directory is written in DocBook 4.5. The
original content was taken from the Apache Qpid Wiki. 

1. Getting DocBook

Docbook is documented here: 
http://docbook.org/tdg/

The Docbook DTDs and schemas are here:
http://www.docbook.org/schemas/

Stylesheets for creating PDF, HTML, and various other formats are here: 
http://sourceforge.net/projects/docbook/files/

DocBook packages exist for some Linux systems. For instance, on my
Fedora 11 system, I have installed these RPMs:

  docbook-dtds-0:1.0-47.fc11.noarch
  docbook-simple-0:1.1-5.fc11.noarch
  docbook-style-xsl-0:1.75.2-1.fc11.noarch
  

2. Editing Tools

For Emacs, I like nxml-mode, especially if you learn how to use tag
completion, outlining, etc.  This is described in some detail in
http://www.dpawson.co.uk/relaxng/nxml/info.html.

For vi, the macros described in this Linux Journal article may be
helpful: http://www.linuxjournal.com/article/7737.

Commercial XML editors provide good support for DocBook. On Windows, I
like Stylus Studio (http://www.stylusstudio.com/). On Linux, I like
Oxygen (http://www.oxygenxml.com/).

Here's a page on authoring tools for DocBook:
http://wiki.docbook.org/topic/DocBookAuthoringTools


3. Building the Documentation

I have checked in a shell script, build.sh, which builds a PDF. It
will soon be replaced by an ANT file.

In addition to DocBook, you need the following software:

- An XInclude processor. The shell script uses xmllint.
- An XSLT processor. The shell script uses xsltproc.
- An XSL:FO processor. The shell script uses Apache FOP (fop-0.95-3.noarch on Fedora).
  
