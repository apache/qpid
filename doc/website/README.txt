This is the source directory for creating web pages for the Qpid web
site.

The template used for all pages (template.html), stylesheet
(style.css), and images are in the ./template directory.

The tools directory contains a very simple Python script (wrap) that
combines a template and content to create static html pages. Content
should be written in XHTML, with one <div/> element at the root - see
./content/home.html for an example.

Use wrap like this:

$ tools/wrap template/template.html content/<filename> build/<filename>

Content for the main pages should be check into the content
directory. Content for documentation is created in the ../book
directory.

To publish generated content, check out the website repo:

$ svn co https://svn.apache.org/repos/asf/qpid/site/docs

Copy generated content (NOT the source!) into the website repo, add it
using $ svn add, and commit it. When it is committed, it appears on
the website.
