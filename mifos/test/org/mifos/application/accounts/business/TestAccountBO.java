/**

 * TestAccountBO.java    version: 1.0

 

 * Copyright (c) 2005-2006 Grameen Foundation USA

 * 1029 Vermont Avenue, NW, Suite 400, Washington DC 20005

 * All rights reserved.

 

 * Apache License 
 * Copyright (c) 2005-2006 Grameen Foundation USA 
 * 

 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 
 *

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the 

 * License. 
 * 
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an explanation of the license 

 * and how it is applied.  

 *

 */
package org.mifos.application.accounts.business;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.mifos.application.accounts.TestAccount;
import org.mifos.application.accounts.exceptions.AccountException;
import org.mifos.application.accounts.financial.business.FinancialTransactionBO;
import org.mifos.application.accounts.loan.business.LoanBO;
import org.mifos.application.accounts.loan.business.LoanTrxnDetailEntity;
import org.mifos.application.accounts.util.helpers.AccountConstants;
import org.mifos.application.accounts.util.helpers.AccountState;
import org.mifos.application.accounts.util.helpers.PaymentData;
import org.mifos.application.accounts.util.helpers.PaymentStatus;
import org.mifos.application.customer.center.business.CenterBO;
import org.mifos.application.customer.group.business.GroupBO;
import org.mifos.application.fees.business.FeeBO;
import org.mifos.application.fees.util.helpers.FeeCategory;
import org.mifos.application.fees.util.helpers.FeePayment;
import org.mifos.application.meeting.business.MeetingBO;
import org.mifos.application.personnel.business.PersonnelBO;
import org.mifos.application.personnel.persistence.PersonnelPersistence;
import org.mifos.framework.components.configuration.business.Configuration;
import org.mifos.framework.exceptions.ApplicationException;
import org.mifos.framework.exceptions.PersistenceException;
import org.mifos.framework.exceptions.SystemException;
import org.mifos.framework.hibernate.helper.HibernateUtil;
import org.mifos.framework.security.util.UserContext;
import org.mifos.framework.util.helpers.DateUtils;
import org.mifos.framework.util.helpers.Money;
import org.mifos.framework.util.helpers.TestObjectFactory;

public class TestAccountBO extends TestAccount {
	public TestAccountBO() {
	}

	public static void addAccountFlag(AccountStateFlagEntity flagDetail,
			AccountBO account) {
		account.addAccountFlag(flagDetail);
	}

	public void testFailureRemoveFees() {
		try {
			HibernateUtil.getSessionTL();
			HibernateUtil.startTransaction();
			UserContext uc = TestObjectFactory.getUserContext();
			Set<AccountFeesEntity> accountFeesEntitySet = accountBO
					.getAccountFees();
			Iterator itr = accountFeesEntitySet.iterator();
			while (itr.hasNext())
				accountBO.removeFees(((AccountFeesEntity) itr.next()).getFees()
						.getFeeId(), uc.getId());
			HibernateUtil.getTransaction().commit();
			assert (false);
		} catch (Exception e) {
			assertTrue(true);
		}
	}

	public void testSuccessUpdateAccountActionDateEntity() {
		List<Short> installmentIdList;
		try {
			installmentIdList = getApplicableInstallmentIdsForRemoveFees(accountBO);
			Set<AccountFeesEntity> accountFeesEntitySet = accountBO
					.getAccountFees();
			Iterator itr = accountFeesEntitySet.iterator();
			while (itr.hasNext()) {
				accountBO.updateAccountActionDateEntity(installmentIdList,
						((AccountFeesEntity) itr.next()).getFees().getFeeId());
				assertTrue(true);
			}
		} catch (Exception e) {
			assertFalse(false);
		}
	}

	private List<Short> getApplicableInstallmentIdsForRemoveFees(
			AccountBO account) {
		List<Short> installmentIdList = new ArrayList<Short>();
		for (AccountActionDateEntity accountActionDateEntity : account
				.getApplicableIdsForFutureInstallments()) {
			installmentIdList.add(accountActionDateEntity.getInstallmentId());
		}
		installmentIdList.add(account.getDetailsOfNextInstallment()
				.getInstallmentId());
		return installmentIdList;
	}

	public void testSuccessUpdateAccountFeesEntity() {
		Set<AccountFeesEntity> accountFeesEntitySet = accountBO
				.getAccountFees();
		Iterator itr = accountFeesEntitySet.iterator();
		while (itr.hasNext()) {
			AccountFeesEntity accountFeesEntity = (AccountFeesEntity) itr
					.next();
			accountBO.updateAccountFeesEntity(accountFeesEntity.getFees()
					.getFeeId());
			assertEquals(accountFeesEntity.getFeeStatus(),
					AccountConstants.INACTIVE_FEES);
		}
	}

	public void testGetLastLoanPmntAmnt() throws Exception {
		Date currentDate = new Date(System.currentTimeMillis());
		LoanBO loan = (LoanBO) accountBO;
		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		accntActionDates.addAll(loan.getAccountActionDates());
		PaymentData paymentData = TestObjectFactory.getLoanAccountPaymentData(
				accntActionDates,
				TestObjectFactory.getMoneyForMFICurrency(700), null, loan
						.getPersonnel(), "receiptNum", Short.valueOf("1"),
				currentDate, currentDate);
		loan.applyPayment(paymentData);

		TestObjectFactory.updateObject(loan);
		TestObjectFactory.flushandCloseSession();
		assertEquals(
				"The amount returned for the payment should have been 1272",
				700.0, loan.getLastPmntAmnt());
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				loan.getAccountId());
	}

	public void testLoanAdjustment() throws Exception {
		HibernateUtil.closeSession();
		Date currentDate = new Date(System.currentTimeMillis());
		LoanBO loan = (LoanBO) TestObjectFactory.getObject(LoanBO.class,
				accountBO.getAccountId());
		loan.setUserContext(TestObjectFactory.getUserContext());
		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		accntActionDates.add(loan.getAccountActionDate(Short.valueOf("1")));
		PaymentData accountPaymentDataView = TestObjectFactory
				.getLoanAccountPaymentData(accntActionDates, TestObjectFactory
						.getMoneyForMFICurrency(216), null,
						loan.getPersonnel(), "receiptNum", Short.valueOf("1"),
						currentDate, currentDate);
		loan.applyPayment(accountPaymentDataView);
		HibernateUtil.commitTransaction();
		HibernateUtil.closeSession();
		loan = (LoanBO) TestObjectFactory.getObject(LoanBO.class, loan
				.getAccountId());
		loan.setUserContext(TestObjectFactory.getUserContext());
		loan.applyPayment(TestObjectFactory.getLoanAccountPaymentData(null,
				TestObjectFactory.getMoneyForMFICurrency(600), null, loan
						.getPersonnel(), "receiptNum", Short.valueOf("1"),
				currentDate, currentDate));
		HibernateUtil.commitTransaction();
		HibernateUtil.closeSession();
		loan = (LoanBO) TestObjectFactory.getObject(LoanBO.class, loan
				.getAccountId());
		loan.setUserContext(TestObjectFactory.getUserContext());
		loan.adjustPmnt("loan account has been adjusted by test code");
		TestObjectFactory.updateObject(loan);
		assertEquals("The amount returned for the payment should have been 0",
				0.0, loan.getLastPmntAmnt());
		LoanTrxnDetailEntity lastLoanTrxn = null;
		for (AccountTrxnEntity accntTrxn : loan.getLastPmnt().getAccountTrxns()) {
			lastLoanTrxn = (LoanTrxnDetailEntity) accntTrxn;
			break;
		}
		AccountActionDateEntity installment = loan
				.getAccountActionDate(lastLoanTrxn.getInstallmentId());
		assertEquals(
				"The installment adjusted should now be marked unpaid(due).",
				installment.getPaymentStatus(), PaymentStatus.UNPAID.getValue());
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				loan.getAccountId());
	}

	public void testAdjustmentForClosedAccnt() throws Exception {
		Date currentDate = new Date(System.currentTimeMillis());
		LoanBO loan = (LoanBO) accountBO;

		loan.setAccountState(new AccountStateEntity(
				AccountState.LOANACC_OBLIGATIONSMET));
		loan.setUserContext(TestObjectFactory.getUserContext());
		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		accntActionDates.addAll(loan.getAccountActionDates());
		PaymentData accountPaymentDataView = TestObjectFactory
				.getLoanAccountPaymentData(accntActionDates, TestObjectFactory
						.getMoneyForMFICurrency(712), null,
						loan.getPersonnel(), "receiptNum", Short.valueOf("1"),
						currentDate, currentDate);
		loan.applyPayment(accountPaymentDataView);

		TestObjectFactory.updateObject(loan);
		try {
			loan.adjustPmnt("loan account has been adjusted by test code");
			assertTrue(false);
		} catch (Exception e) {
			assertTrue(true);
		}
	}

	public void testRetrievalOfNullMonetaryValue() throws Exception {
		Date currentDate = new Date(System.currentTimeMillis());
		LoanBO loan = (LoanBO) accountBO;
		loan.setUserContext(TestObjectFactory.getUserContext());
		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		accntActionDates.addAll(loan.getAccountActionDates());
		PaymentData accountPaymentDataView = TestObjectFactory
				.getLoanAccountPaymentData(accntActionDates, TestObjectFactory
						.getMoneyForMFICurrency(0), null, loan.getPersonnel(),
						"receiptNum", Short.valueOf("1"), currentDate,
						currentDate);

		loan.applyPayment(accountPaymentDataView);
		TestObjectFactory.updateObject(loan);
		TestObjectFactory.flushandCloseSession();
		loan = (LoanBO) TestObjectFactory.getObject(AccountBO.class, loan
				.getAccountId());

		Money pmntAmnt = null;
		for (AccountPaymentEntity accntPmnt : loan.getAccountPayments()) {
			pmntAmnt = accntPmnt.getAmount();
		}
		TestObjectFactory.flushandCloseSession();
		assertEquals(
				"Account payment retrieved should be zero with currency MFI currency",
				TestObjectFactory.getMoneyForMFICurrency(0), pmntAmnt);
	}

	public void testGetTransactionHistoryView() throws Exception {
		Date currentDate = new Date(System.currentTimeMillis());
		LoanBO loan = (LoanBO) accountBO;
		loan.setUserContext(TestObjectFactory.getUserContext());
		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		accntActionDates.addAll(loan.getAccountActionDates());
		PaymentData accountPaymentDataView = TestObjectFactory
				.getLoanAccountPaymentData(accntActionDates, TestObjectFactory
						.getMoneyForMFICurrency(100), null,
						loan.getPersonnel(), "receiptNum", Short.valueOf("1"),
						currentDate, currentDate);
		loan.applyPayment(accountPaymentDataView);
		TestObjectFactory.flushandCloseSession();
		loan = (LoanBO) TestObjectFactory.getObject(AccountBO.class, loan
				.getAccountId());
		List<Integer> ids = new ArrayList<Integer>();
		for (AccountPaymentEntity accountPaymentEntity : loan
				.getAccountPayments()) {
			for (AccountTrxnEntity accountTrxnEntity : accountPaymentEntity
					.getAccountTrxns()) {
				for (FinancialTransactionBO financialTransactionBO : accountTrxnEntity
						.getFinancialTransactions()) {
					ids.add(financialTransactionBO.getTrxnId());
				}
			}
		}
		loan.setUserContext(TestObjectFactory.getUserContext());
		List<TransactionHistoryView> trxnHistlist = loan
				.getTransactionHistoryView();
		assertNotNull("Account TrxnHistoryView list object should not be null",
				trxnHistlist);
		assertTrue(
				"Account TrxnHistoryView list object Size should be greater than zero",
				trxnHistlist.size() > 0);
		assertEquals(ids.size(), trxnHistlist.size());
		int i = 0;
		for (TransactionHistoryView transactionHistoryView : trxnHistlist) {
			assertEquals(ids.get(i), transactionHistoryView.getAccountTrxnId());
			i++;
		}
		TestObjectFactory.flushandCloseSession();
		accountBO = (LoanBO) TestObjectFactory.getObject(AccountBO.class, loan
				.getAccountId());
	}

	public void testGetPeriodicFeeList() throws PersistenceException {
		FeeBO oneTimeFee = TestObjectFactory.createOneTimeAmountFee(
				"One Time Fee", FeeCategory.LOAN, "20",
				FeePayment.TIME_OF_DISBURSMENT);
		AccountFeesEntity accountOneTimeFee = new AccountFeesEntity(accountBO,
				oneTimeFee, new Double("1.0"));
		accountBO.addAccountFees(accountOneTimeFee);
		accountPersistence.createOrUpdate(accountBO);
		TestObjectFactory.flushandCloseSession();
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				accountBO.getAccountId());
		assertEquals(1, accountBO.getPeriodicFeeList().size());
	}

	public void testIsTrxnDateValid() throws Exception {

		Calendar calendar = new GregorianCalendar();
		// Added by rajender on 24th July as test case was not passing
		calendar.add(Calendar.DAY_OF_MONTH, 10);
		java.util.Date trxnDate = new Date(calendar.getTimeInMillis());
		if (Configuration.getInstance().getAccountConfig(Short.valueOf("3"))
				.isBackDatedTxnAllowed())
			assertTrue(accountBO.isTrxnDateValid(trxnDate));
		else
			assertFalse(accountBO.isTrxnDateValid(trxnDate));

	}

	public void testHandleChangeInMeetingSchedule()
			throws ApplicationException, SystemException {
		TestObjectFactory.flushandCloseSession();
		center = (CenterBO) TestObjectFactory.getObject(CenterBO.class, center
				.getCustomerId());
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				accountBO.getAccountId());
		MeetingBO meeting = center.getCustomerMeeting().getMeeting();
		meeting.getMeetingDetails().setRecurAfter(Short.valueOf("2"));
		List<java.util.Date> meetingDates = meeting.getAllDates(accountBO
				.getApplicableIdsForFutureInstallments().size() + 1);
		TestObjectFactory.updateObject(center);
		center.getCustomerAccount().handleChangeInMeetingSchedule();
		accountBO.handleChangeInMeetingSchedule();
		HibernateUtil.getTransaction().commit();
		HibernateUtil.closeSession();
		center = (CenterBO) TestObjectFactory.getObject(CenterBO.class, center
				.getCustomerId());
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				accountBO.getAccountId());
		for (AccountActionDateEntity actionDateEntity : center
				.getCustomerAccount().getAccountActionDates()) {
			if (actionDateEntity.getInstallmentId().equals(Short.valueOf("2")))
				assertEquals(DateUtils.getDateWithoutTimeStamp(actionDateEntity
						.getActionDate().getTime()), DateUtils
						.getDateWithoutTimeStamp(meetingDates.get(1).getTime()));
			else if (actionDateEntity.getInstallmentId().equals(
					Short.valueOf("3")))
				assertEquals(DateUtils.getDateWithoutTimeStamp(actionDateEntity
						.getActionDate().getTime()), DateUtils
						.getDateWithoutTimeStamp(meetingDates.get(2).getTime()));
		}
		for (AccountActionDateEntity actionDateEntity : accountBO
				.getAccountActionDates()) {
			if (actionDateEntity.getInstallmentId().equals(Short.valueOf("2")))
				assertEquals(DateUtils.getDateWithoutTimeStamp(actionDateEntity
						.getActionDate().getTime()), DateUtils
						.getDateWithoutTimeStamp(meetingDates.get(1).getTime()));
			else if (actionDateEntity.getInstallmentId().equals(
					Short.valueOf("3")))
				assertEquals(DateUtils.getDateWithoutTimeStamp(actionDateEntity
						.getActionDate().getTime()), DateUtils
						.getDateWithoutTimeStamp(meetingDates.get(2).getTime()));
		}

	}

	public void testDeleteFutureInstallments() throws HibernateException,
			SystemException, AccountException {
		TestObjectFactory.flushandCloseSession();
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				accountBO.getAccountId());
		accountBO.deleteFutureInstallments();
		HibernateUtil.getTransaction().commit();
		HibernateUtil.closeSession();
		accountBO = (AccountBO) TestObjectFactory.getObject(AccountBO.class,
				accountBO.getAccountId());
		assertEquals(1, accountBO.getAccountActionDates().size());

	}

	public void testUpdate() throws Exception {
		TestObjectFactory.flushandCloseSession();
		accountBO = (AccountBO) HibernateUtil.getSessionTL().get(
				AccountBO.class, accountBO.getAccountId());
		accountBO.setUserContext(TestObjectFactory.getUserContext());

		java.sql.Date currentDate = new java.sql.Date(System
				.currentTimeMillis());
		PersonnelBO personnelBO = new PersonnelPersistence()
				.getPersonnel(TestObjectFactory.getUserContext().getId());
		AccountNotesEntity accountNotesEntity = new AccountNotesEntity(
				currentDate, "account updated", personnelBO, accountBO);
		accountBO.addAccountNotes(accountNotesEntity);
		TestObjectFactory.updateObject(accountBO);
		TestObjectFactory.flushandCloseSession();
		accountBO = (AccountBO) HibernateUtil.getSessionTL().get(
				AccountBO.class, accountBO.getAccountId());
		for (AccountNotesEntity accountNotes : accountBO
				.getRecentAccountNotes()) {
			assertEquals("Last note added is account updated",
					"account updated", accountNotes.getComment());
			break;
		}
	}

	public void testGetPastInstallments() {

		MeetingBO meeting = TestObjectFactory.createMeeting(TestObjectFactory
				.getMeetingHelper(1, 1, 4, 2));
		CenterBO centerBO = TestObjectFactory.createCenter("Center_Active",
				Short.valueOf("13"), "1.1", meeting, new java.util.Date());
		HibernateUtil.closeSession();
		centerBO = (CenterBO) TestObjectFactory.getObject(CenterBO.class,
				centerBO.getCustomerId());
		for (AccountActionDateEntity actionDate : centerBO.getCustomerAccount()
				.getAccountActionDates()) {
			actionDate.setActionDate(offSetCurrentDate(4));
			break;
		}
		List<AccountActionDateEntity> pastInstallments = centerBO
				.getCustomerAccount().getPastInstallments();
		assertNotNull(pastInstallments);
		assertEquals(1, pastInstallments.size());
		TestObjectFactory.cleanUp(centerBO);
	}

	public void testUpdatePerformanceHistoryOnAdjustment() throws Exception {
		Date currentDate = new Date(System.currentTimeMillis());
		HibernateUtil.closeSession();
		accountBO = (AccountBO) HibernateUtil.getSessionTL().get(
				AccountBO.class, accountBO.getAccountId());

		List<AccountActionDateEntity> accntActionDates = new ArrayList<AccountActionDateEntity>();
		PaymentData paymentData = TestObjectFactory.getLoanAccountPaymentData(
				accntActionDates,
				TestObjectFactory.getMoneyForMFICurrency(212), null, accountBO
						.getPersonnel(), "receiptNum", Short.valueOf("1"),
				currentDate, currentDate);
		accountBO.applyPayment(paymentData);

		HibernateUtil.commitTransaction();
		LoanBO loan = (LoanBO) TestObjectFactory.getObject(LoanBO.class,
				accountBO.getAccountId());

		loan.applyPayment(TestObjectFactory.getLoanAccountPaymentData(null,
				TestObjectFactory.getMoneyForMFICurrency(600), null, loan
						.getPersonnel(), "receiptNum", Short.valueOf("1"),
				currentDate, currentDate));
		HibernateUtil.commitTransaction();
		HibernateUtil.closeSession();
		accountBO = (AccountBO) HibernateUtil.getSessionTL().get(
				AccountBO.class, accountBO.getAccountId());
		accountBO.setUserContext(TestObjectFactory.getUserContext());
		accountBO.adjustPmnt("loan account has been adjusted by test code");
		HibernateUtil.commitTransaction();
		HibernateUtil.closeSession();

		accountBO = (AccountBO) HibernateUtil.getSessionTL().get(
				AccountBO.class, accountBO.getAccountId());
		center = (CenterBO) TestObjectFactory.getObject(CenterBO.class, center
				.getCustomerId());
		group = (GroupBO) TestObjectFactory.getObject(GroupBO.class, group
				.getCustomerId());
		assertEquals(0, ((LoanBO) accountBO).getPerformanceHistory()
				.getNoOfPayments().intValue());

	}
	
	public static void addToAccountStatusChangeHistory(LoanBO loan,
			AccountStatusChangeHistoryEntity accountStatusChangeHistoryEntity) {
		loan.addAccountStatusChangeHistory(accountStatusChangeHistoryEntity);
	}

	private java.sql.Date offSetCurrentDate(int noOfDays) {
		Calendar currentDateCalendar = new GregorianCalendar();
		int year = currentDateCalendar.get(Calendar.YEAR);
		int month = currentDateCalendar.get(Calendar.MONTH);
		int day = currentDateCalendar.get(Calendar.DAY_OF_MONTH);
		currentDateCalendar = new GregorianCalendar(year, month, day - noOfDays);
		return new java.sql.Date(currentDateCalendar.getTimeInMillis());
	}
}
