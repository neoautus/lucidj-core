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

package org.lucidj.kernel;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

public class KernelLogReader implements LogReaderService
{
    private boolean fatal_to_stderr = true;     // We want to see basic config going wrong
    private BundleContext context;
    private String log_file_path = "./default_log_file.log";
    private FileWriter log_writer;
    private StringBuilder log_buffer;
    private StringWriter trace_buffer = new StringWriter (8192);
    private PrintWriter trace_writer = new PrintWriter (trace_buffer);

    public KernelLogReader (BundleContext context)
    {
        this.context = context;
    }

    private String get_stack_trace (Throwable throwable)
    {
        StringWriter sw = new StringWriter (4096);
        throwable.printStackTrace (new PrintWriter (sw));
        return (sw.toString ());
    }

    private void stderr_fatal (String message, Throwable throwable)
    {
        if (!fatal_to_stderr)
        {
            return;
        }

        System.err.println (message);

        if (throwable != null)
        {
            System.err.println (get_stack_trace (throwable));
        }
    }

    private void append_log_file (String log_line)
    {
        if (log_writer == null)
        {
            try
            {
                Path log_path = Paths.get (log_file_path);
                Files.createDirectories (log_path);
                log_writer = new FileWriter (log_path.toFile (), true);
            }
            catch (IOException e)
            {
                stderr_fatal ("Exception opening: " + log_file_path, e);
            }
        }

        if (log_writer == null)
        {
            // No way to log, fail silently
            return;
        }

        try
        {
            log_writer.write (log_line);
            log_writer.flush ();
        }
        catch (IOException ignore) {};
    }

    public void logged (LogEntry log)
    {
        if ((log.getBundle() == null) || (log.getBundle().getSymbolicName() == null))
        {
            // if there is no name, it's probably the framework emitting a log
            // This should not happen and we don't want to log something anonymous
            return;
        }

        String msg = log.getMessage();

        append_log_file (msg);

        if (log.getException () != null)
        {
            append_log_file (get_stack_trace (log.getException ()));
        }
    }

    @Override // LogReaderService
    public void addLogListener(LogListener logListener)
    {

    }

    @Override // LogReaderService
    public void removeLogListener(LogListener logListener)
    {

    }

    @Override // LogReaderService
    public Enumeration getLog()
    {
        return null;
    }
}

// EOF
