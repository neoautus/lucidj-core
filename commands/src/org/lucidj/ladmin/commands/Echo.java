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

package org.lucidj.ladmin.commands;

import org.lucidj.ext.admind.AdmindUtil;

public class Echo
{
    // This is an example of a supplementary command. The directory /commands inside kernel jar
    // is searched _before_ the ladmin.jar itself. This way, any command found inside kernel
    // _takes precedence_ over the commands found inside ladmin.jar. For instance, it is possible
    // to have a kernel where _start_ command differs from the original _start_ inside ladmin.jar.
    // This feature is intended to alleviate the need for constant updates on ladmin.jar inside
    // production environment, since any significative change can be carried inside the kernel
    // itself before it rolls down to ladmin.jar. It also allows custom kernels with specific
    // needs to be addressed using this feature, without disrupting the usual ladmin.jar workflow.
    //
    public static void main (String[] args)
    {
        String def_server_name = AdmindUtil.getServerName ();
        String admind = AdmindUtil.initAdmindDir ();

        if (admind == null)
        {
            System.out.println ("Unable to find '" + def_server_name + "'");
            System.exit (1);
        }

        String params = (args.length == 0)? "Hello world!": String.join (" ", args);

        String request = AdmindUtil.asyncInvoke ("echo", params);

        if (AdmindUtil.asyncWait (request))
        {
            String response = AdmindUtil.asyncResponse (request);
            System.out.println ("Echo '" + def_server_name + "': " + response.trim ());
        }
        else
        {
            String error = AdmindUtil.asyncError (request);
            System.out.println ("Error requesting echo for '" + def_server_name + "': " + error);
        }
    }
}

// EOF
