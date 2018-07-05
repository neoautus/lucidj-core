/*
 * Copyright 2018 NEOautus Ltd. (http://neoautus.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.lucidj.telnetd;

import org.apache.felix.gogo.jline.Shell;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucidj.api.admind.Task;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class GogoTask implements Task, Shell.Context
{
    private final static Logger log = LoggerFactory.getLogger (GogoTask.class);

    private final static int TERMINFO_TYPE = 0;
    private final static int TERMINFO_SIZE = 1;

    private final static String[] GOSH_ARGV =
    {
        "--login",          // Please?
        "--noshutdown"      // Do NOT shutdown framework when telnet connection is closed :O
    };

    private BundleContext context;
    private Terminal terminal;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;

    private int terminfo_index = -1;
    private StringBuffer terminfo_buf;
    private String terminfo_type = "dumb";
    private int terminfo_width = 80;
    private int terminfo_height = 25;

    public GogoTask (BundleContext context, InputStream in, OutputStream out, OutputStream err,
                     String name, String[] options)
    {
        this.context = context;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    private void run_gosh (Terminal terminal)
    {
        ServiceReference<CommandProcessor> cp_ref = null;

        try
        {
            if ((cp_ref = context.getServiceReference (CommandProcessor.class)) == null)
            {
                terminal.writer ().println ("Command Processor not available");
                return;
            }

            CommandProcessor cp = context.getService (cp_ref);
            CommandSession session = cp.createSession (terminal.input (), terminal.output (), terminal.output ());
            session.put (Shell.VAR_TERMINAL, terminal);
            //environment.forEach(session::put);

            new Shell (this, cp).gosh (session, GOSH_ARGV);
        }
        catch (Exception e)
        {
            terminal.writer ().println ("Error running Command Processor: " + e.toString());
        }
        finally
        {
            if (cp_ref != null)
            {
                context.ungetService (cp_ref);
            }
        }
    }

    private void terminfo_event (int index, String value)
    {
        log.debug ("terminfo_event: index={} value='{}'", index, value);

        switch (index)
        {
            case TERMINFO_TYPE:
            {
                log.info ("Terminal type: {}", value);
                terminfo_type = value;
                break;
            }
            case TERMINFO_SIZE:
            {
                String[] args = value.split (";");
                terminfo_width = Integer.parseInt (args [0]);
                terminfo_height = Integer.parseInt (args [1]);
                log.info ("Terminal size: {} x {}", terminfo_width, terminfo_height);

                if (terminal != null)
                {
                    terminal.setSize (new Size (terminfo_width, terminfo_height));
                    terminal.raise (Terminal.Signal.WINCH);
                }
                break;
            }
        }
    }

    private int filter_terminfo_sequences (int ch)
    {
        log.info ("filter: {} {} -> {}", ch, Integer.toHexString(ch), (char)ch);
        if (ch >= 0xf0 && ch <= 0xff)
        {
            if (terminfo_buf != null)
            {
                terminfo_event (terminfo_index, terminfo_buf.toString ());
                terminfo_index = -1;
                terminfo_buf = null;
            }
            if (ch != 0xff)
            {
                terminfo_index = ch & 0x0f;
                terminfo_buf = new StringBuffer ();
            }
        }
        else if (terminfo_buf != null)
        {
            terminfo_buf.append ((char)ch);
        }
        else
        {
            return (ch);
        }
        return (-1); // Skip this char
    }

    @Override // Task
    public boolean run ()
        throws Exception
    {
        // Wrap TelnetIO input with an InputStream
        InputStream soft_in = new InputStream ()
        {
            @Override
            public int read ()
                throws IOException
            {
                try
                {
                    int ch;

                    // Do NOT run into EOF and filter out terminfo sequences
                    while (in.available() == 0
                           || (ch = filter_terminfo_sequences (in.read () & 0xff)) == -1)
                    {
                        try
                        {
                            Thread.sleep (20);
                        }
                        catch (InterruptedException e) {};
                    }
                    return (ch);
                }
                catch (IOException e)
                {
                    // We assume the connection has closed
                    return (-1);
                }
            }
        };

        // Synchronize terminfo parameters
        while (soft_in.read () != '\r');  // todo: timeout

        terminal = TerminalBuilder.builder ()
            .type (terminfo_type)
            .streams (soft_in, out)
            .system (false)
            .name ("gosh")
            .size (new Size (terminfo_width, terminfo_height))
            .build ();

        // Enter the shell
        run_gosh (terminal);

        log.info ("Connection closed");
        soft_in.close ();
        return (true);
    }

    @Override // Shell.Context
    public String getProperty (String s)
    {
        return (System.getProperty (s));
    }

    @Override // Shell.Context
    public void exit ()
    {
        try
        {
            terminal.close ();
        }
        catch (IOException ignore) {};
    }
}

// EOF
