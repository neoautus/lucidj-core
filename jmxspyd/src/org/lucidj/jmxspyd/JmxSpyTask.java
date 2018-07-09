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

package org.lucidj.jmxspyd;

import org.lucidj.api.admind.Task;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class JmxSpyTask implements Task
{
    private final static Logger log = LoggerFactory.getLogger (JmxSpyTask.class);

    private BundleContext context;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;

    public JmxSpyTask (BundleContext context, InputStream in, OutputStream out, OutputStream err,
                       String name, String... options)
    {
        this.context = context;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    @Override // Task
    public boolean run ()
        throws Exception
    {
        new PrintStream (out).println ("Hello JMX world!");
        return (true);
    }
}

// EOF
