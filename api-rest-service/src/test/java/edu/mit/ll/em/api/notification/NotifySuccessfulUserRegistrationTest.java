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
package edu.mit.ll.em.api.notification;

import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.email.exception.JsonEmailException;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class NotifySuccessfulUserRegistrationTest {

    private OrgDAOImpl orgDao = mock(OrgDAOImpl.class);
    private Configuration emApiConfiguration = mock(Configuration.class);
    private RabbitPubSubProducer rabbitProducer = mock(RabbitPubSubProducer.class);
    private User user = mock(User.class);
    private Org org = mock(Org.class);
    private static final int orgId = 1;
    private static final String fromEmailAddress = "from@happytest.com";
    private static final String newRegisteredEmailAddress = "newregistered@email.address";
    private static final String adminEmailAddress = "testadmin@happy.com";
    private static List<String> orgAdminList = Arrays.asList(adminEmailAddress);
    private static final String alertTopic = "alertTopic";
    private static String hostname;
    private static final String userFirstName = "first";
    private static final String userLastName = "last";
    private static final String userEmailAddress = "useremail@happy.com";
    private static final String orgName = "orgName";
    private NotifySuccessfulUserRegistration notifySuccessfulUserRegistration;

    @Before
    public void setup() throws IOException {
        when(emApiConfiguration.getString(APIConfig.EMAIL_ALERT_TOPIC, "iweb.nics.email.alert")).thenReturn(alertTopic);
        when(emApiConfiguration.getString(APIConfig.NEW_USER_ALERT_EMAIL)).thenReturn(fromEmailAddress);
        when(emApiConfiguration.getString(APIConfig.NEW_REGISTERED_USER_EMAIL)).thenReturn(newRegisteredEmailAddress);
        when(emApiConfiguration.getString(APIConfig.RABBIT_HOSTNAME_KEY)).thenReturn("rabbitHost");
        when(emApiConfiguration.getString(APIConfig.RABBIT_EXCHANGENAME_KEY)).thenReturn("rabbitExchangeKey");
        when(emApiConfiguration.getString(APIConfig.RABBIT_USERNAME_KEY)).thenReturn("rabbitUsernameKey");
        when(emApiConfiguration.getString(APIConfig.RABBIT_USERPWD_KEY)).thenReturn("rabbitUserPwdKey");
        when(user.getFirstname()).thenReturn(userFirstName);
        when(user.getLastname()).thenReturn(userLastName);
        when(user.getUsername()).thenReturn(userEmailAddress);
        when(org.getOrgId()).thenReturn(orgId);
        when(org.getName()).thenReturn(orgName);
        hostname = InetAddress.getLocalHost().getHostName();
        DateTimeUtils.setCurrentMillisFixed(DateTime.now().getMillis());
        notifySuccessfulUserRegistration = new NotifySuccessfulUserRegistration(orgDao, emApiConfiguration, rabbitProducer);
    }

    @Test
    public void verifyFailedRegistrationSendsNotificationToOrgAdmins() throws IOException, JsonEmailException {
        when(orgDao.getOrgAdmins(orgId)).thenReturn(orgAdminList);
        notifySuccessfulUserRegistration.notify(user, org);
        verify(rabbitProducer).produce(alertTopic, this.getJsonEmail(orgAdminList).toJsonObject().toString());
    }

    @Test
    public void verifyFailedRegistrationSendsNotificationToConfiguredUsersInCaseOfZeroOrgAdmins() throws IOException, JsonEmailException {
        when(orgDao.getOrgAdmins(orgId)).thenReturn(Collections.EMPTY_LIST);
        notifySuccessfulUserRegistration.notify(user, org);
        verify(rabbitProducer).produce(alertTopic, this.getJsonEmail(Collections.EMPTY_LIST).toJsonObject().toString());
    }

    private JsonEmail getJsonEmail(List<String> orgAdminList) throws IOException {
        String toEmailAddresses = CollectionUtils.isEmpty(orgAdminList) ? newRegisteredEmailAddress : (StringUtils.collectionToCommaDelimitedString(orgAdminList) + ", " + newRegisteredEmailAddress);
        JsonEmail email = new JsonEmail(fromEmailAddress, toEmailAddresses, "SCOUT User Registration Request from RegisterAccount@" + hostname);
        email.setBody(getEmailBody(user, org, hostname));
        return email;
    }

    private String getEmailBody(User user, Org org, String hostname) throws UnknownHostException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body>");
        String date = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy").format(new Date());
        builder.append(date);
        builder.append("<br><br>");
        builder.append("A new user has registered: " + user.getUsername());
        builder.append("<br>");
        builder.append("Name: " + user.getFirstname() + " " + user.getLastname());
        builder.append("<br>");
        builder.append("Organization: " + org.getName());
        builder.append("<br>");
        builder.append("Email: " + user.getUsername());
        builder.append("<br><br>");
        builder.append("Please review their registration request and, if approved, enable their account in SCOUT.");
        builder.append("<br><br>");
        builder.append("The user will receive a Welcome email upon activation.");
        builder.append("</body></html>");
        System.out.println(builder.toString());
        return builder.toString();
    }
}
