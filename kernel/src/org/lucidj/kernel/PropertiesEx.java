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

import java.util.Properties;

public class PropertiesEx extends Properties
{
    public PropertiesEx ()
    {
        super ();
    }

    public PropertiesEx (Properties defaults)
    {
        super (defaults);
    }

    public String getProperty (String key)
    {
        final int max_depth = 5;
        String value = super.getProperty (key);

        if (value != null)
        {
            for (int i = 0; value.contains ("${") && i < max_depth; i++)
            {
                // Very expensive, but will be done with limited amount of data
                // and probably no one will see this... In the future aliens will
                // think this was intended to run on a quantum computer :)
                for (String prop_key: stringPropertyNames ())
                {
                    value = value.replace ("${" + prop_key + "}", super.getProperty (prop_key));
                }

                // System properties are included but have less precedence
                for (String prop_key: System.getProperties ().stringPropertyNames ())
                {
                    value = value.replace ("${" + prop_key + "}", System.getProperty (prop_key));
                }
            }
        }
        return (value);
    }
}

// EOF
