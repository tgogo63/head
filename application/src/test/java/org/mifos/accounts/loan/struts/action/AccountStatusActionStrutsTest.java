/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */

package org.mifos.accounts.loan.struts.action;

import junit.framework.Assert;
import org.mifos.accounts.business.AccountBO;
import org.mifos.accounts.loan.business.LoanBO;
import org.mifos.accounts.loan.util.helpers.LoanConstants;
import org.mifos.accounts.productdefinition.business.LoanOfferingBO;
import org.mifos.accounts.util.helpers.AccountState;
import org.mifos.application.meeting.business.MeetingBO;
import org.mifos.application.util.helpers.ActionForwards;
import org.mifos.application.util.helpers.Methods;
import org.mifos.customers.business.CustomerBO;
import org.mifos.customers.center.business.CenterBO;
import org.mifos.customers.client.business.ClientBO;
import org.mifos.customers.group.business.GroupBO;
import org.mifos.customers.personnel.business.PersonnelDto;
import org.mifos.customers.util.helpers.CustomerStatus;
import org.mifos.framework.MifosMockStrutsTestCase;
import org.mifos.framework.hibernate.helper.StaticHibernateUtil;
import org.mifos.framework.persistence.TestDatabase;
import org.mifos.framework.util.helpers.Constants;
import org.mifos.framework.util.helpers.SessionUtils;
import org.mifos.framework.util.helpers.TestObjectFactory;
import org.mifos.security.util.UserContext;

import java.util.Date;
import java.util.List;

public class AccountStatusActionStrutsTest extends MifosMockStrutsTestCase {

    public AccountStatusActionStrutsTest() throws Exception {
        super();
    }

    private UserContext userContext;

    protected AccountBO account;

    protected CenterBO center;

    protected GroupBO group;

    private ClientBO client;

    private MeetingBO meeting;

    private String flowKey;

    @Override
    protected void setStrutsConfig() {
        super.setStrutsConfig();
        setConfigFile("/WEB-INF/struts-config.xml,/WEB-INF/accounts-struts-config.xml");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        userContext = TestObjectFactory.getContext();
        request.getSession().setAttribute(Constants.USERCONTEXT, userContext);
        addRequestParameter("recordLoanOfficerId", "1");
        addRequestParameter("recordOfficeId", "1");
        request.getSession(false).setAttribute("ActivityContext", TestObjectFactory.getActivityContext());
        flowKey = createFlow(request, AccountStatusAction.class);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            account = null;
            client = null;
            group = null;
            center = null;
            account = null;
//            client = null;
//            group = null;
//            center = null;
        } catch (Exception e) {
            // TODO Whoops, cleanup didnt work, reset db
            TestDatabase.resetMySQLDatabase();
        }
        StaticHibernateUtil.flushSession();
        super.tearDown();
    }

    public void testLoad() {
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", Methods.load.toString());
        actionPerform();
        verifyForward(ActionForwards.changeAccountStatus_success.toString());
    }

    public void testSearchResults() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", Methods.searchResults.toString());
        addRequestParameter("officeId", account.getOffice().getOfficeId().toString());
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("personnelId", account.getPersonnel().getPersonnelId().toString());
        addRequestParameter("type", "loan");
        addRequestParameter("currentStatus", account.getAccountState().getId().toString());
        performNoErrors();
        verifyForward(ActionForwards.changeAccountStatusSearch_success.toString());
        Assert.assertNotNull(SessionUtils.getAttribute(LoanConstants.SEARCH_RESULTS, request));
       Assert.assertEquals(1, ((List<PersonnelDto>) SessionUtils.getAttribute(LoanConstants.SEARCH_RESULTS, request)).size());
    }

    public void testSearchResults_noresults_forvalidate() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", Methods.searchResults.toString());
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("personnelId", account.getPersonnel().getPersonnelId().toString());
        addRequestParameter("currentStatus", AccountState.LOAN_PARTIAL_APPLICATION.getValue().toString());
        actionPerform();
        verifyInputForward();
    }

    public void testSearchResults_noresults() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", Methods.searchResults.toString());
        addRequestParameter("officeId", account.getOffice().getOfficeId().toString());
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("personnelId", account.getPersonnel().getPersonnelId().toString());
        addRequestParameter("type", "loan");
        addRequestParameter("currentStatus", AccountState.LOAN_PARTIAL_APPLICATION.getValue().toString());
        actionPerform();
        verifyForward(ActionForwards.noresultfound.toString());
        Assert.assertNull(SessionUtils.getAttribute(LoanConstants.SEARCH_RESULTS, request));
    }

    public void testSearchResults_exception() {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("method", Methods.searchResults.toString());
        actionPerform();
        verifyInputForward();
    }

    public void testGetLoanOfficers() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("method", "getLoanOfficers");
        addRequestParameter("officeId", account.getOffice().getOfficeId().toString());
        actionPerform();
        verifyForward(ActionForwards.changeAccountStatus_success.toString());
        Assert.assertNotNull(SessionUtils.getAttribute(LoanConstants.LOAN_OFFICERS, request));
       Assert.assertEquals(1, ((List<PersonnelDto>) SessionUtils.getAttribute(LoanConstants.LOAN_OFFICERS, request)).size());
    }

    public void testUpdate() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", "update");
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        addRequestParameter("accountRecords[0]", account.getAccountId().toString());
        addRequestParameter("newStatus", "3");
        addRequestParameter("comments", "comments");
        actionPerform();
        verifyForward(ActionForwards.changeAccountStatusConfirmation_success.toString());
    }

    public void testUpdate_validation() throws Exception {
        createCustomers();
        account = getLoanAccount(group);
        setRequestPathInfo("/ChangeAccountStatus.do");
        addRequestParameter("method", "update");
        addRequestParameter(Constants.CURRENTFLOWKEY, flowKey);
        actionPerform();
        verifyInputForward();
    }

    private void createParentCustomer() {
        meeting = TestObjectFactory.createMeeting(TestObjectFactory.getTypicalMeeting());
        center = TestObjectFactory.createWeeklyFeeCenter("Center", meeting);

    }

    private void createCustomers() {
        createParentCustomer();
        group = TestObjectFactory.createWeeklyFeeGroupUnderCenter("group", CustomerStatus.GROUP_ACTIVE, center);
        client = TestObjectFactory.createClient("Client", CustomerStatus.CLIENT_ACTIVE, group);
        StaticHibernateUtil.flushSession();
    }

    private LoanBO getLoanAccount(CustomerBO customerBO) {
        Date startDate = new Date(System.currentTimeMillis());
        LoanOfferingBO loanOffering = TestObjectFactory.createLoanOffering(startDate, meeting);
        return TestObjectFactory.createLoanAccount("42423142341", customerBO, AccountState.LOAN_PENDING_APPROVAL,
                startDate, loanOffering);

    }

}
