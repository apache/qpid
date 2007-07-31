/*
 * The Java Shell: jsh core -- RELEASE: alpha3
 * (C)1999 Osvaldo Pinali Doederlein.
 *
 * LICENSE
 * =======
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * CHANGES
 * =======
 * 1.0.2 - Added support for non-quoted '\' escape char (Not interpreted by the shell)
 * 1.0.1 - Added support for arguments in aliases
 * 1.0.0 - Initial release; split from Shell and enhanced a lot.
 *
 * LINKS
 * =====
 * Contact: mailto@osvaldo.visionnaire.com.br, mailto@g.collin@appliweb.net
 * Site #1: http://www.geocities.com/ResearchTriangle/Node/2005/
 * Site #2: http://www.appliweb.net/jsh
 */

package com.redhat.etp.qpid.utils;

import java.io.BufferedReader;
import java.util.Vector;

/**
 * The Java Shell.
 * <p>
 * Provides an environment for launching and controlling Java apps.
 * <p>
 * TODO: - Support for applets!
 *       - Support for variable replacement
 *
 * @author Osvaldo Pinali Doederlein.
 */
public class RSHCommandParser implements CommandParser
{
    /** Where commands come from. */
    protected BufferedReader reader;
    /** Continuation for command line. */
    protected String contLine = null;
    /** Run next command in background? */
    protected boolean background;
    protected String[] env; // Commands passed in args.

    public RSHCommandParser(BufferedReader reader)
    {
        this(reader, null);
    }

    public RSHCommandParser(BufferedReader reader, String env[])
    {
        this.reader = reader;
        this.env = env;
        if (env == null)
        {
            this.env = new String[0];
        }
    }


    public boolean more()
    {
        return contLine != null;
    }

    public boolean isBackground()
    {
        return background;
    }

    /**
     * Solves and expands aliases in an argument array.
     * This expansion affects only the first (if any) argument; the
     * typical thing to do, as we don't want to expand parameters.
     * We recursively parse the result to be sure we expand everything.
     * For example, an alias can be expanded to further aliases, or it can contains args.
     *
     * @param args Raw arguments.
     * @return <code>args</code> resolved and expanded.
     */
    public static String[] expand(String[] args)
    {
        if (args.length > 0)
        {
            String expanded = args[0];//Alias.resolve(args[0]);

            // Try to expand recursively the command line
            if (expanded != args[0])
            {
                RSHCommandParser recurse = new RSHCommandParser(new BufferedReader(
                        new java.io.StringReader(expanded)));

                try
                {
                    String cmdLine[] = recurse.parse();
                    cmdLine = recurse.expand(cmdLine);

                    // do we need to handle new arguments and insert them in the command array ?
                    if (cmdLine.length > 1)
                    {
                        String[] newArgs = new String[cmdLine.length + args.length - 1];
                        System.arraycopy(cmdLine, 0, newArgs, 0, cmdLine.length);

                        if (args.length > 1)
                        {
                            System.arraycopy(args, 1, newArgs, cmdLine.length, args.length - 1);
                        }

                        args = newArgs;
                    }
                    else if (cmdLine.length == 1)
                    {
                        args[0] = cmdLine[0];
                    }
                }
                catch (java.io.IOException e)
                {
                }
            }
        }

        return args;
    }

    public String[] parse() throws java.io.IOException
    {
        final int READ = 0, QUOTE = 1, SKIP = 2, ESCAPE = 3, NONQUOTEDESCAPE = 4, VARIABLE = 5;
        final int EOF = 0xFFFF;
        int mode = SKIP;
        Vector<String> args = new Vector<String>();
        StringBuffer current = new StringBuffer();
        StringBuffer varName = null;
        background = false;
        String line;

        if (contLine == null)
        {
            line = reader.readLine();
        }
        else
        {
            line = contLine;
            contLine = null;
        }

        if (line == null)
        {
            reader = null;
            return null;
        }

        for (int pos = 0; pos < line.length(); ++pos)
        {
            char c = line.charAt(pos);

            switch (mode)
            {
                case SKIP:
                    switch (c)
                    {
                        case' ':
                        case'\t':
                            break;
                        case'\"':
                            mode = QUOTE;
                            break;
                        case'&':
                            background = true;
                        case';':
                            contLine = line.substring(pos + 1);
                            pos = line.length();
                            break;
                        default:
                            mode = READ;
                            --pos;
                    }
                    break;

                case READ:
                    switch (c)
                    {
                        case'\"':
                            mode = QUOTE;
                            break;
                        case';':
                        case'&':
                            --pos;
                        case' ':
                        case'\t':
                            mode = SKIP;
                            break;
                        case'\\':
                            mode = NONQUOTEDESCAPE;
                            break;
                        case'$':
                            mode = VARIABLE;
                            varName = new StringBuffer();
                            break;
                        default:
                            current.append(c);
                    }
                    if ((mode != READ) && (mode != NONQUOTEDESCAPE))
                    {
                        args.addElement(current.toString());
                        current = new StringBuffer();
                    }
                    break;

                case QUOTE:
                    switch (c)
                    {
                        case'\"':
                            mode = READ;
                            break;
                        case'\\':
                            mode = ESCAPE;
                            break;
                        default:
                            current.append(c);
                    }
                    break;

                case ESCAPE:
                    switch (c)
                    {
                        case'n':
                            c = '\n';
                            break;
                        case'r':
                            c = '\r';
                            break;
                        case't':
                            c = '\t';
                            break;
                        case'b':
                            c = '\b';
                            break;
                        case'f':
                            c = '\f';
                            break;
                        default:
                            current.append('\\');
                            break;
                    }
                    mode = QUOTE;
                    current.append(c);
                    break;
                case NONQUOTEDESCAPE:
                    switch (c)
                    {
                        case';':
                            mode = READ;
                            current.append(c);
                            break;
                        default: // This is not a escaped char.
                            mode = READ;
                            current.append('\\');
                            current.append(c);
                            break;
                    }
                    break;
                case VARIABLE:
                    switch (c)
                    {
                        case'$':
                        {
//                            String val = Set.get(new String(varName));
//                            if (val != null)
//                            {
//                                current.append(val);
//                            }
                            mode = READ;
                            break;
                        }
                        case'@':
                        {
                            StringBuffer val = new StringBuffer();
                            int i;
                            for (i = 0; i < env.length; i++)
                            {
                                val.append(env[i]);
                                val.append(' ');
                            }
                            current.append(val);
                            mode = READ;
                            break;
                        }
                        case'0':
                        case'1':
                        case'2':
                        case'3':
                        case'4':
                        case'5':
                        case'6':
                        case'7':
                        case'8':
                        case'9':
                        {
                            if (varName.length() == 0)
                            {
                                int value = Integer.parseInt(new String(new char[]{c}));
                                if (env.length > value)
                                {
                                    current.append(env[value]);
                                }
                                mode = READ;
                                break;
                            }
                            // else fall back
                        }
                        default:
                            varName.append(c);
                            break;
                    }
                    break;
            }
        }

        if (current.length() > 0)
        {
            args.addElement(current.toString());
        }
        return expand(toArray(args));
    }

    private String[] toArray(Vector strings)
    {
        String[] arr = new String[strings.size()];

        for (int i = 0; i < strings.size(); ++i)
        {
            arr[i] = (String) strings.elementAt(i);
        }

        return arr;
    }

}
