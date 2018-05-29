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

import org.jline.builtins.telnet.Connection;
import org.jline.builtins.telnet.ConnectionData;
import org.jline.builtins.telnet.ConnectionManager;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public class Telnetd implements Runnable
{
    private final static Logger log = LoggerFactory.getLogger (Telnetd.class);

    private final static int DEFAULT_LISTEN_PORT   = 6523;           // Very mnemonic if you remember telnet port :)
    private final static int MAX_CONNECTIONS       = 10;             // SIMULTANEOUS connections
    private final static int WARNING_TIMEOUT       = 5 * 60 * 1000;  // 5 minutes
    private final static int DISCONNECT_TIMEOUT    = 5 * 60 * 1000;  // 5 minutes
    private final static int HOUSEKEEPING_INTERVAL = 60 * 1000;      // 60 seconds

    private BundleContext context;
    private Thread accept_thread;
    private ConnectionManager connection_manager;
    private ServerSocket server_socket = null;

    public Telnetd (BundleContext context)
    {
        this.context = context;
    }

    public boolean start ()
    {
        try
        {
            server_socket = new ServerSocket (DEFAULT_LISTEN_PORT, 1, InetAddress.getLoopbackAddress());
        }
        catch (IOException e)
        {
            log.error ("Failed to listen port {}", DEFAULT_LISTEN_PORT, e);
            return (false);
        }

        connection_manager = new ConnectionManager (MAX_CONNECTIONS, WARNING_TIMEOUT, DISCONNECT_TIMEOUT,
                                                    HOUSEKEEPING_INTERVAL, null, null, false)
        {
            @Override
            // TODO: CHECK THREAD LEAKING
            protected Connection createConnection (ThreadGroup threadGroup, ConnectionData connectionData)
            {
                return (new GogoConnection (context, threadGroup, connectionData));
            }
        };

        // Start things
        accept_thread = new Thread (this);
        accept_thread.setName (context.getBundle().getSymbolicName() + "-srv-" + DEFAULT_LISTEN_PORT);
        accept_thread.start ();
        return (true);
    }

    public void run ()
    {
        log.info ("TelnedD listener started on localhost:{}", DEFAULT_LISTEN_PORT);

        while (!accept_thread.isInterrupted ()
               && !server_socket.isClosed ())
        {
            try
            {
                connection_manager.makeConnection (server_socket.accept ());
            }
            catch (IOException e)
            {
                if (!server_socket.isClosed ())
                {
                    // Warn only if we are on the fly, ignore when shutting down
                    log.warn ("Exception accepting connection", e);
                }
            }
        }
        log.info ("TelnedD listener thread stopped");
    }

    public synchronized void stop ()
    {
        // Stop accepting connections
        try
        {
            server_socket.close ();
        }
        catch (IOException e)
        {
            log.warn ("Exception closing listener socket", e);
        }

        // Stop listener thread, wait at most 10 secs for clean stop
        try
        {
            accept_thread.interrupt ();
            accept_thread.join (10000);
        }
        catch (InterruptedException ignore) {};
        log.info ("TelnedD stopped");
    }
}

// EOF
