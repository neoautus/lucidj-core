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

import org.lucidj.api.admind.Task;
import org.lucidj.api.admind.TaskProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Hashtable;

public class TelnetdActivator implements TaskProvider, BundleActivator
{
    private BundleContext context;
    private Telnetd telnetd;
    private ServiceRegistration<TaskProvider> provider_registration;

    @Override // TaskProvider
    public Task createTask (InputStream in, OutputStream out, OutputStream err, String locator, String... options)
    {
        return (new GogoTask (context, in, out, err, locator, options));
    }

    @Override // BundleActivator
    public void start (BundleContext bundleContext)
        throws Exception
    {
        context = bundleContext;
        telnetd = new Telnetd (context);

        if (!telnetd.start ())
        {
            telnetd = null;
        }

        // Register this class as TaskProvider for 'shell'
        Dictionary<String, Object> props = new Hashtable<>();
        props.put (TaskProvider.NAME_FILTER, "shell");
        provider_registration = bundleContext.registerService (TaskProvider.class, this, props);
    }

    @Override // BundleActivator
    public void stop (BundleContext bundleContext)
        throws Exception
    {
        provider_registration.unregister ();

        if (telnetd != null)
        {
            telnetd.stop ();
        }
    }
}

// EOF
