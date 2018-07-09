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

import org.lucidj.api.admind.TaskProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Dictionary;
import java.util.Hashtable;

public class JmxSpydActivator implements BundleActivator
{
    private BundleContext context;
    private JmxSpyd jmxspyd;
    private ServiceRegistration<TaskProvider> provider_registration;

    @Override // BundleActivator
    public void start (BundleContext bundleContext)
            throws Exception
    {
        context = bundleContext;

        if ((jmxspyd = new JmxSpyd (context)).start ())
        {
        }
        else
        {
            jmxspyd = null;
        }
    }

    @Override // BundleActivator
    public void stop (BundleContext bundleContext)
            throws Exception
    {
        if (jmxspyd != null)
        {
            jmxspyd.stop ();
            jmxspyd = null;
        }
    }
}

// EOF
