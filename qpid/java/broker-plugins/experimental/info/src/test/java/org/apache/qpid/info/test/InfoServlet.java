package org.apache.qpid.info.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/*
 * This is a servlet used by the embedded Jetty to be able to receive http post
 * from the info plugin
 */

public class InfoServlet extends GenericServlet
{
    private static final long serialVersionUID = 1L;

    @Override
    public void service(ServletRequest request, ServletResponse response)
            throws ServletException, IOException
    {
        String line;
        BufferedReader in = request.getReader();
        while ((line = in.readLine()) != null)
        {
            System.out.println(line);
        }
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("OK <br>\n");
        System.out.println("ServletResponse: OK");
    }

}