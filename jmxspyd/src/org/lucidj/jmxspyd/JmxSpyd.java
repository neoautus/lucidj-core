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
import org.lucidj.api.admind.TaskProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Dictionary;
import java.util.Hashtable;

class JmxSpyd implements TaskProvider
{
    private final static Logger log = LoggerFactory.getLogger (JmxSpyd.class);

    private BundleContext context;

    private MBeanServer mbean_server;
    private ServiceRegistration<MBeanServer> mbean_server_reg;
    private ServiceRegistration<TaskProvider> task_provider_reg;

    public JmxSpyd (BundleContext context)
    {
        this.context = context;
    }

    @Override // TaskProvider
    public Task createTask (InputStream in, OutputStream out, OutputStream err, String locator, String... options)
    {
        return (new JmxSpyTask (context, in, out, err, locator, options));
    }

    public boolean start ()
    {
        // Get and register the MBean Server
        mbean_server = ManagementFactory.getPlatformMBeanServer ();
        mbean_server_reg = context.registerService (MBeanServer.class, mbean_server, null);
        // TODO: TEST FOR ALREADY REGISTERED SERVER (!?)

        // Register as TaskProvider for 'jmx'
        Dictionary<String, Object> props = new Hashtable<>();
        props.put (TaskProvider.NAME_FILTER, "jmx");
        task_provider_reg = context.registerService (TaskProvider.class, this, props);

        log.info ("JmxSpyD started ({})", mbean_server);
        return (true);
    }

    public void stop ()
    {
        task_provider_reg.unregister ();
        task_provider_reg = null;
        mbean_server_reg.unregister ();
        mbean_server_reg = null;
        mbean_server = null;
        context = null;
        log.info ("JmxSpyD stopped");
    }


}

// EOF
