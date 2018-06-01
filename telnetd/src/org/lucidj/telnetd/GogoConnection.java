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
import org.jline.builtins.telnet.*;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.SocketException;
import java.util.Map;

class GogoConnection extends Connection implements ConnectionListener, Shell.Context
{
    private final static String[] GOSH_ARGV =
    {
        "--login",          // Please?
        "--noshutdown"      // Do NOT shutdown framework when telnet connection is closed :O
    };

    private BundleContext context;
    private Terminal terminal;
    private TelnetStreams telnet_streams;

    public GogoConnection (BundleContext context, ThreadGroup threadGroup, ConnectionData connectionData)
    {
        super (threadGroup, connectionData);
        this.context = context;
    }

    private void run_gosh (Terminal terminal, Map<String, String> environment)
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
            environment.forEach(session::put);

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

    @Override // Connection
    protected void doRun ()
        throws Exception
    {
        telnet_streams = new TelnetStreams (this);

        if (!telnet_streams.open ())
        {
            // Abort due to some premature error
            return;
        }

        terminal = TerminalBuilder.builder ()
            .type (getConnectionData ().getNegotiatedTerminalType ().toLowerCase ())
            .streams (telnet_streams.getInputStream (), new PrintStream (telnet_streams.getOutputStream ()))
            .system (false)
            .name ("gosh")
            .build ();
        update_terminal_size ();
        addConnectionListener (this);

        try
        {
            // Enter the shell
            run_gosh (terminal, getConnectionData ().getEnvironment ());
        }
        finally
        {
            close ();
        }
    }

    @Override // Connection
    protected void doClose ()
    {
        telnet_streams.close ();
    }

    private void update_terminal_size ()
    {
        terminal.setSize (new Size (getConnectionData ().getTerminalColumns (),
                                    getConnectionData ().getTerminalRows ()));
    }

    @Override // ConnectionListener
    public void connectionTerminalGeometryChanged (ConnectionEvent ce)
    {
        update_terminal_size ();
        terminal.raise (Terminal.Signal.WINCH);
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

    class TelnetStreams
    {
        private TelnetIO telnet_io;
        private InputStream telnet_in;
        private OutputStream telnet_out;
        private Connection connection;

        public TelnetStreams (Connection connection)
        {
            this.connection = connection;
            telnet_io = new TelnetIO ();
            telnet_io.setConnection (connection);
        }

        public boolean open ()
        {
            try
            {
                telnet_io.initIO ();
            }
            catch (IOException e)
            {
                return (false);
            }

            // Wrap TelnetIO input with an InputStream
            telnet_in = new InputStream ()
            {
                @Override
                public int read ()
                    throws IOException
                {
                    try
                    {
                        // Read 1 byte or EOF (-1)
                        return (telnet_io.read ());
                    }
                    catch (SocketException e)
                    {
                        if (connection.getConnectionData ().getSocket ().isClosed ())
                        {
                            // Return nice EOF if socket closed exception
                            return (-1);
                        }
                        throw (e);
                    }
                    catch (EOFException e)
                    {
                        return (-1);
                    }
                }

                @Override
                public int read (byte[] b, int off, int len)
                    throws IOException
                {
                    // Satisfy JDK conditions
                    if (b == null)
                    {
                        throw new NullPointerException ();
                    }
                    else if (off < 0 || len < 0 || len > b.length - off)
                    {
                        throw new IndexOutOfBoundsException ();
                    }
                    else if (len == 0)
                    {
                        return 0;
                    }

                    // Read 1 byte or EOF (-1)
                    int c = read ();

                    if (c >= 0)
                    {
                        // We read only 1 byte a time
                        b[off] = (byte)c;
                        return (1);
                    }
                    return (-1);
                }
            };

            // Wrap TelnetIO output with an OutputStream
            telnet_out = new OutputStream ()
            {
                @Override
                public void write (int b)
                    throws IOException
                {
                    telnet_io.write (b);
                }

                @Override
                public void flush ()
                    throws IOException
                {
                    telnet_io.flush ();
                }
            };
            return (true);
        }

        public InputStream getInputStream ()
        {
            return (telnet_in);
        }

        public OutputStream getOutputStream ()
        {
            return (telnet_out);
        }

        public void close ()
        {
            try
            {
                telnet_in.close ();
            }
            catch (IOException ignore) {};

            try
            {
                telnet_out.close ();
            }
            catch (IOException ignore) {};

            telnet_io.closeOutput ();
            telnet_io.closeInput ();
        }
    }
}

// EOF
