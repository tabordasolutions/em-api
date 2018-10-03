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
package edu.mit.ll.em.api.configuration;


import edu.mit.ll.em.api.notification.NotifyFailedUserRegistration;
import edu.mit.ll.em.api.notification.NotifySuccessfulUserRegistration;
import edu.mit.ll.em.api.openam.OpenAmGatewayFactory;
import edu.mit.ll.em.api.service.UserRegistrationService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.APILogger;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.DatalayerDAO;
import edu.mit.ll.nics.nicsdao.DocumentDAO;
import edu.mit.ll.nics.nicsdao.FolderDAO;
import edu.mit.ll.nics.nicsdao.impl.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;

@Configuration
public class SpringConfiguration {
    private org.apache.commons.configuration.Configuration emApiConfiguration = null;
    private Client jerseyClient = null;

    @Bean
    public org.apache.commons.configuration.Configuration emApiConfiguration() {
        if(this.emApiConfiguration == null) {
            this.emApiConfiguration = APIConfig.getInstance().getConfiguration();
        }
        return this.emApiConfiguration;
    }

    @Bean
    public Client jerseyClient() {
        if(jerseyClient == null) {
            this.jerseyClient = ClientBuilder.newClient();
        }
        return this.jerseyClient;
    }

    @Bean
    public DatalayerDAO datalayerDao() { return new DatalayerDAOImpl(); }

    @Bean
    public FolderDAO folderDao() { return new FolderDAOImpl(); }

    @Bean
    public DocumentDAO documentDao() { return new DocumentDAOImpl(); }

    @Bean
    public OrgDAOImpl orgDao() {
        return new OrgDAOImpl();
    }

    @Bean
    public UserDAOImpl userDao() {
        return new UserDAOImpl();
    }

    @Bean
    public UserOrgDAOImpl userOrgDao() {
        return new UserOrgDAOImpl();
    }

    @Bean
    public UserSessionDAOImpl userSessionDao() {
        return new UserSessionDAOImpl();
    }

    @Bean
    public OpenAmGatewayFactory openAmGatewayFactory() {
        return new OpenAmGatewayFactory();
    }

    @Bean
    public WorkspaceDAOImpl workspaceDao() {
        return new WorkspaceDAOImpl();
    }

    @Bean
    public UserRegistrationService registrationService() throws IOException {
        return new UserRegistrationService(logger(), userDao(), orgDao(), userOrgDao(), workspaceDao(), openAmGatewayFactory(), successfulUserRegistrationNotification(), failedUserRegistrationNotification());
    }

    @Bean
    public NotifySuccessfulUserRegistration successfulUserRegistrationNotification() throws IOException {
        return new NotifySuccessfulUserRegistration(orgDao(), emApiConfiguration());
    }

    @Bean
    public NotifyFailedUserRegistration failedUserRegistrationNotification() throws IOException {
        return new NotifyFailedUserRegistration(emApiConfiguration());
    }

    @Bean
    public APILogger logger() {
        return APILogger.getInstance();
    }

    @Bean
    public Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Bean
    RabbitPubSubProducer rabbitProducer() throws IOException {
        return RabbitFactory.makeRabbitPubSubProducer(
                    this.emApiConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
                    this.emApiConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
                    this.emApiConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
                    this.emApiConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
    }

    @Override
    public void finalize() {
        if(this.jerseyClient != null) {
            this.jerseyClient.close();
        }
    }
}
