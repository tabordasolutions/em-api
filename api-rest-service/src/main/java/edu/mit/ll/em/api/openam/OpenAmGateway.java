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

import edu.mit.ll.em.api.rs.RegisterUser;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.APILogger;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.sso.util.SSOUtil;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * To instantiate OpenAmGateway, we need an instance of SSOUtil. Before instantiating SSOUtil please make sure
 * to add ssoToolsPropertyPath as System property. Please see UserServicesImpl.getOpenAmGatewayInstance()
 * for example.
 */
public class OpenAmGateway {

    private static final APILogger log = APILogger.getInstance();
    private SSOUtil ssoUtil;

    public OpenAmGateway(SSOUtil ssoUtil) {
        this.ssoUtil = ssoUtil;
    }

    /**
     * Uses SSOUtil to create an Identity user in OpenAM
     *
     * @param user User to register
     * @param registerUser The RegisterUser object resulting from a registration request
     * @return A JSON response in the form: {"status":"success/fail", "message":"MESSAGE"}
     */
    public JSONObject createIdentityUser(User user, RegisterUser registerUser) {
        boolean login = false;
        String creationResponse = "";
        JSONObject response = null;

        if (ssoUtil.loginAsAdmin()) {
            creationResponse = ssoUtil.createUser(user.getUsername(), registerUser.getPassword(),
                    user.getFirstname(), user.getLastname(), false);
            try {
                response = new JSONObject(creationResponse);
            } catch (JSONException e) {
                // can't read response, assume failure
                response = buildJSONResponse("fail", "JSON exception reading createUser response: " + e.getMessage());
            }
        } else {
            response = buildJSONResponse("fail", "Failed to log in as Administrator with SSOUtil. Cannot create Identity.");
        }
        return response;
    }

    /**
     * Deletes an identity user from OpenAM
     *
     * @param uid
     * @return A JSON response in the form: {"status":"success/fail", "message":"MESSAGE"}
     */
    public JSONObject deleteIdentityUser(String uid) {
        boolean login = false;
        String deletionResponse = "";
        JSONObject response = null;

        if(ssoUtil.loginAsAdmin()) {
            deletionResponse = ssoUtil.deleteUser(uid);
            try {
                response = new JSONObject(deletionResponse);
            } catch(JSONException e) {
                // can't read response, assume failure
                log.e("OpenAmGateway", "JSON exception reading delete identity response", e );
                response = buildJSONResponse("fail", "JSON exception reading delete identity response: " +
                        e.getMessage());
            }
        } else {
            log.e("OpenAmGateway", "Failed to log in as Administrator with SSOUtil. Cannot delete Identity with uid : " + uid);
            response = buildJSONResponse("fail",
                    "Failed to log in as Administrator with SSOUtil. Cannot delete Identity with uid : " + uid);
        }
        return response;
    }

    /**
     * Builds a JSON response object
     *
     * @param status Content of the status field
     * @param message A message explaining the status
     * @return A JSONObject with a 'status' and 'message' field populated if successful,
     * 			otherwise an empty JSONObject
     */
    private JSONObject buildJSONResponse(String status, String message) {
        JSONObject response = new JSONObject();

        try {
            response.put("status", status);
            response.put("message", message);
        } catch(JSONException e) {
            APILogger.getInstance().e("OpenAmGateway:buildJSONResponse", "JSONException building response: " +
                    e.getMessage());
        }
        return response;
    }
}