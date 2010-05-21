This is the source directory for creating web pages for the Qpid web site.

The template used for all pages (template.html), stylesheet (style.css), and images are in the ./template directory. 

The tools directory contains a very simple Python script (wrap) that combines a template and content to create an output file. Content should be written in XHTML, with one <div/> element at the root - see ./content/home.html for an example. Use wrap like this:

$ tools/wrap templates/template.html content/home.html build/index.html

The example directory shows sample output. It includes the CSS stylesheet and images needed to view the output in a web browser. 

Images associated with the template are in template/images. Images associated with content are in content/images.
