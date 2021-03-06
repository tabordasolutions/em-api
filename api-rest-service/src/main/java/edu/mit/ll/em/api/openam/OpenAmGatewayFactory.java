/**
 * Copyright (c) 2008-2018, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.openam;

import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.APILogger;
import edu.mit.ll.nics.sso.util.SSOUtil;

public class OpenAmGatewayFactory {
    private static final APILogger log = APILogger.getInstance();

    public OpenAmGateway create() throws Exception {
        OpenAmGateway openAmGateway = null;
        String propPath = APIConfig.getInstance().getConfiguration()
                .getString("ssoToolsPropertyPath", null);

        log.i("UserServiceImpl:getOpenAmGatewayInstance", "Initializing SSOUtils with property path: " + propPath);
        if(propPath == null) {
            log.w("OpenAmGatewayFactory", "Got null SSO configuration, won't be able to make SSO calls!");
            throw new Exception("Failed to create identity. 'ssoToolsPropertyPath' not set, cannot make SSO related calls.");
        } else {
            System.setProperty("ssoToolsPropertyPath", propPath);
            System.setProperty("openamPropertiesPath", propPath);
            openAmGateway = new OpenAmGateway(new SSOUtil());
        }
        return openAmGateway;
    }
}
