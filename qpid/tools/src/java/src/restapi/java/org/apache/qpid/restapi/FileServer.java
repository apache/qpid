/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.restapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

/**
 * FileServer is a fairly simple HTTP File Server that can be used to serve files from the root directory specified
 * during construction.
 * <p>
 * Although this is a relatively simple File Server it is still able to serve large files as it uses streaming, in
 * addition it uses the HTTP Range/Content-Range/Content-Length Headers to allow resuming of partial downloads
 * from clients that support it.
 *
 * @author Fraser Adams
 */
public class FileServer implements Server
{
    /**
     * HashMap mapping file extension to MIME type for common types.
     * TODO make this set of mappings configurable via properties or similar mechanism.
     */
    private static Map<String, String> _mimeTypes = new HashMap<String, String>();
    static
    {
        _mimeTypes.put("htm", "text/html");
        _mimeTypes.put("html", "text/html");
        _mimeTypes.put("txt", "text/plain");
        _mimeTypes.put("asc", "text/plain");
        _mimeTypes.put("xml", "text/xml");
        _mimeTypes.put("css", "text/css");
        _mimeTypes.put("htc", "text/x-component");
        _mimeTypes.put("gif", "image/gif");
        _mimeTypes.put("jpg", "image/jpeg");
        _mimeTypes.put("jpeg", "image/jpeg");
        _mimeTypes.put("png", "image/png");
        _mimeTypes.put("ico", "image/x-icon");
        _mimeTypes.put("mp3", "audio/mpeg");
        _mimeTypes.put("m3u", "audio/mpeg-url");
        _mimeTypes.put("js", "application/x-javascript");
        _mimeTypes.put("pdf", "application/pdf");
        _mimeTypes.put("doc", "application/msword");
        _mimeTypes.put("ppt", "application/mspowerpoint");
        _mimeTypes.put("xls", "application/excel");
        _mimeTypes.put("ogg", "application/x-ogg");
        _mimeTypes.put("zip", "application/octet-stream");
        _mimeTypes.put("exe", "application/octet-stream");
        _mimeTypes.put("class", "application/octet-stream");
    }

    private final File _home;
    private final boolean _allowDirectoryListing;

    /**
     * URL-encodes everything between "/"-characters. Encodes spaces as '%20' instead of '+'.
     *
     * @param uri the uri to be encoded.
     * @return the encoded uri as a String.
     */
    private String encodeUri(final String uri)
    {
        StringBuilder encodedUri = new StringBuilder();
        StringTokenizer st = new StringTokenizer(uri, "/ ", true);
        while (st.hasMoreTokens())
        {
            String tok = st.nextToken();
            if (tok.equals("/"))
            {
                encodedUri.append("/");
            }
            else if (tok.equals(" "))
            {
                encodedUri.append("%20");
            }
            else
            {
                try
                {
                    encodedUri.append(URLEncoder.encode(tok, "UTF-8"));
                }
                catch (UnsupportedEncodingException uee)
                {
                }
            }
        }
        return encodedUri.toString();
    }

    /**
     * Renders a number in a more "human readable" format providing a bytes/KB/MB/GB format depending on the size.
     *
     * @param number the number to be rendered.
     * @return a String representation of the number in a more human readable form.
     */
    private String renderNumber(float number)
    {
        if (number < 1000)
        {
            return String.format("%.0f", number) + " bytes";
        }
        else if (number < 1000000)
        {
            number /= 1000.0f;
            return String.format("%.1f", number) + " KB";
        }
        else if (number < 1000000000)
        {
            number /= 1000000.0f;
            return String.format("%.1f", number) + " MB";
        }
        else 
        {
            number /= 1000000000.0f;
            return String.format("%.1f", number) + " GB";
        }
    }

    /**
     * Construct an instance of FileServer.
     *
     * @param home the path name of the root directory that we wish this FieServer to serve via HTTP.
     * @param allowDirectoryListing a flag that if set will serve a directory listing to the client and thus enable
     *        browsing to sub-directories of the home directory. N.B. protection has been put in place to mitigate
     *        against the possibility of serving directories that may be parents of the root directory.
     */
    public FileServer(final String home, final boolean allowDirectoryListing)
    {
        _home = new File(home);
        _allowDirectoryListing = allowDirectoryListing;
    }

    /**
     * Called by the Web Server to allow a Server to handle a GET request.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doGet(final HttpTransaction tx) throws IOException
    {
        String user = tx.getPrincipal() != null ? tx.getPrincipal() : "none";
        String path = tx.getRequestURI();

        //System.out.println();
        //System.out.println("FileServer doGet " + path + ", user: " + user);
        //System.out.println("thread = " + Thread.currentThread().getId());
        //tx.logRequest();

        // If the _home filesystem that we use as a root to serve files from is not a directory then
        // we sent an error response and return.
        if (!_home.isDirectory())
        {
            tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain",
                             "500 Internal Server Error: given document root is not a directory.");
            return;
        }

        //String path = tx.getRequestURI();

        // Prohibit getting out of _home directory
        if (path.startsWith("..") || path.endsWith("..") || path.indexOf("../") >= 0)
        {
            tx.sendResponse(HTTP_FORBIDDEN, "text/plain", "403 Forbidden: Won't serve ../ for security reasons.");
            return;
        }

        File file = new File(_home, path);
        if (!file.exists())
        {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found: File " + path + " not found.");
            return;
        }

        // List the directory, if necessary
        if (file.isDirectory())
        {
            File directory = file;
            // Browsers get confused without '/' after the directory, send a redirect.
            if (!path.endsWith("/"))
            {
                path += "/";
                tx.setHeader("Location", path);
                tx.sendResponse(HTTP_MOVED_PERM, "text/html",
                                   "<html><body>Redirected: <a href=\"" + path + "\">" + path + "</a></body></html>");
                return;
            }

            // First try index.html and index.htm
            if (new File(directory, "index.html").exists())
            {
                file = new File(_home, path + "/index.html");
            }
            else if (new File(directory, "index.htm").exists())
            {
                file = new File(_home, path + "/index.htm");
            }
            else if (_allowDirectoryListing)
            { // No index file, list the directory
                StringBuilder response = new StringBuilder("<html><body><h1>Directory " + path + "</h1><br/>");

                if (path.length() > 1)
                {
                    String u = path.substring(0, path.length() - 1);
                    int slash = u.lastIndexOf('/');
                    if (slash >= 0 && slash < u.length())
                    {
                        response.append("<b><a href=\"" + path.substring(0, slash + 1) + "\">..</a></b><br/>");
                    }
                }

                String[] files = directory.list();
                for (String name : files)
                {
                    File current = new File(directory, name);
                    boolean isDir = current.isDirectory();
                    boolean isFile = current.isFile();
                    if (isDir)
                    {
                        response.append("<b>");
                        name += "/";
                    }

                    response.append("<a href=\"" + encodeUri(path + name) + "\">" + name + "</a>");

                    if (isFile)
                    { // If it's a file show the file size
                        response.append(" &nbsp;<font size=2>(" + renderNumber(current.length()) + ")</font>");
                    }
                    response.append("<br/>");
                    if (isDir)
                    {
                        response.append("</b>");
                    }
                }
                tx.sendResponse(HTTP_OK, "text/html", response.toString());
                return;
            }
            else
            {
                tx.sendResponse(HTTP_FORBIDDEN, "text/plain", "403 Forbidden: No directory listing.");
                return;
            }
        }

        try
        {
            // Get MIME type from file name extension, if possible
            String fileName = file.getCanonicalPath();
            String mime = null;
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0)
            {
                String fileExtension = fileName.substring(dot + 1).toLowerCase();
                mime = _mimeTypes.get(fileExtension);
            }

            if (mime == null)
            {
                mime = "application/octet-stream";
            }
        
            // Use Range header allow download resuming.
            long startFrom = 0;
            long length = file.length();

            String range = tx.getHeader("Range");
            if (range != null)
            {
                if (range.startsWith("bytes="))
                {
                    range = range.substring("bytes=".length());

                    int minus = range.indexOf('-');
                    if (minus > 0)
                    {
                        range = range.substring(0, minus);
                    }

                    try
                    {
                        startFrom = Long.parseLong(range);
                    }
                    catch (NumberFormatException nfe)
                    {
                    }
                }
            }

            FileInputStream is = new FileInputStream(file);
            is.skip(startFrom);

            int status = (startFrom == 0) ? HTTP_OK : HTTP_PARTIAL;
            tx.setHeader("Content-Length", "" + (length - startFrom));
            tx.setHeader("Content-Range", "" + startFrom + "-" + (length - 1) + "/" + length);

            tx.sendResponse(status, mime, is);
        }
        catch (IOException ioe)
        {
            tx.sendResponse(HTTP_FORBIDDEN, "text/plain", "403 Forbidden: Reading file failed.");
        }
    }

    /**
     * Called by the Web Server to allow a Server to handle a POST request.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doPost(final HttpTransaction tx) throws IOException
    {
        tx.sendResponse(HTTP_BAD_METHOD, "text/plain", "405 Bad Method.");
    }

    /**
     * Called by the Web Server to allow a Server to handle a PUT request.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doPut(final HttpTransaction tx) throws IOException
    {
        tx.sendResponse(HTTP_BAD_METHOD, "text/plain", "405 Bad Method.");
    }

    /**
     * Called by the Web Server to allow a Server to handle a DELETE request.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doDelete(final HttpTransaction tx) throws IOException
    {
        tx.sendResponse(HTTP_BAD_METHOD, "text/plain", "405 Bad Method.");
    }
}

