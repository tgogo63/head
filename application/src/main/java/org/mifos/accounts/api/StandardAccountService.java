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

package org.mifos.accounts.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.joda.time.LocalDate;
import org.mifos.accounts.acceptedpaymenttype.persistence.AcceptedPaymentTypePersistence;
import org.mifos.accounts.business.AccountBO;
import org.mifos.accounts.business.AccountPaymentEntity;
import org.mifos.accounts.exceptions.AccountException;
import org.mifos.accounts.loan.business.LoanBO;
import org.mifos.accounts.loan.persistance.LoanPersistence;
import org.mifos.accounts.persistence.AccountPersistence;
import org.mifos.accounts.savings.business.SavingsBO;
import org.mifos.accounts.util.helpers.AccountState;
import org.mifos.accounts.util.helpers.AccountTypes;
import org.mifos.accounts.util.helpers.PaymentData;
import org.mifos.application.master.business.PaymentTypeEntity;
import org.mifos.application.master.persistence.MasterPersistence;
import org.mifos.application.util.helpers.TrxnTypes;
import org.mifos.config.ConfigurationManager;
import org.mifos.customers.personnel.business.PersonnelBO;
import org.mifos.customers.personnel.persistence.PersonnelDao;
import org.mifos.framework.exceptions.PersistenceException;
import org.mifos.framework.hibernate.helper.StaticHibernateUtil;
import org.mifos.framework.util.helpers.Money;

/**
 * A service class implementation to expose basic functions on loans. As an external API, this class should not expose
 * business objects, only DTOs.
 */
public class StandardAccountService implements AccountService {
    private AccountPersistence accountPersistence;
    private LoanPersistence loanPersistence;
    private AcceptedPaymentTypePersistence acceptedPaymentTypePersistence;
    private final PersonnelDao personnelDao;

    public StandardAccountService(AccountPersistence accountPersistence, LoanPersistence loanPersistence,
            AcceptedPaymentTypePersistence acceptedPaymentTypePersistence, PersonnelDao personnelDao) {
        this.accountPersistence = accountPersistence;
        this.loanPersistence = loanPersistence;
        this.acceptedPaymentTypePersistence = acceptedPaymentTypePersistence;
        this.personnelDao = personnelDao;
    }

    public LoanPersistence getLoanPersistence() {
        return this.loanPersistence;
    }

    public void setLoanPersistence(LoanPersistence loanPersistence) {
        this.loanPersistence = loanPersistence;
    }

    public AccountPersistence getAccountPersistence() {
        return this.accountPersistence;
    }

    public void setAccountPersistence(AccountPersistence accountPersistence) {
        this.accountPersistence = accountPersistence;
    }

    public AcceptedPaymentTypePersistence getAcceptedPaymentTypePersistence() {
        return this.acceptedPaymentTypePersistence;
    }

    public void setAcceptedPaymentTypePersistence(AcceptedPaymentTypePersistence acceptedPaymentTypePersistence) {
        this.acceptedPaymentTypePersistence = acceptedPaymentTypePersistence;
    }

    @Override
    public void makePayment(AccountPaymentParametersDto accountPaymentParametersDto) throws PersistenceException,
            AccountException {
        StaticHibernateUtil.startTransaction();
        makePaymentNoCommit(accountPaymentParametersDto);
        StaticHibernateUtil.commitTransaction();
    }

    @Override
    public void makePayments(List<AccountPaymentParametersDto> accountPaymentParametersDtoList)
            throws PersistenceException, AccountException {
        /*
         * We're counting on rollback on exception behavior in BaseAction. If we want to expose makePayments via a
         * non-Mifos-Web-UI service, we'll need to handle the rollback here.
         */
        StaticHibernateUtil.startTransaction();
        for (AccountPaymentParametersDto accountPaymentParametersDTO : accountPaymentParametersDtoList) {
            makePaymentNoCommit(accountPaymentParametersDTO);
        }
        StaticHibernateUtil.commitTransaction();
    }

    public void makePaymentNoCommit(AccountPaymentParametersDto accountPaymentParametersDto)
            throws PersistenceException, AccountException {
        final int accountId = accountPaymentParametersDto.getAccount().getAccountId();
        final AccountBO account = getAccountPersistence().getAccount(accountId);
        List<InvalidPaymentReason> validationErrors = validatePayment(accountPaymentParametersDto);
        if (validationErrors.contains(InvalidPaymentReason.INVALID_DATE)) {
            throw new AccountException("errors.invalidTxndate");
        }

        Money amount = new Money(account.getCurrency(), accountPaymentParametersDto.getPaymentAmount());

        Date receiptDate = null;
        if (accountPaymentParametersDto.getReceiptDate() != null) {
            receiptDate = accountPaymentParametersDto.getReceiptDate().toDateMidnight().toDate();
        }

        PaymentData paymentData = account.createPaymentData(accountPaymentParametersDto.getUserMakingPayment()
                .getUserId(), amount, accountPaymentParametersDto.getPaymentDate().toDateMidnight().toDate(),
                accountPaymentParametersDto.getReceiptId(), receiptDate, accountPaymentParametersDto.getPaymentType()
                        .getValue());
        paymentData.setComment(accountPaymentParametersDto.getComment());

        account.applyPayment(paymentData);

        getAccountPersistence().createOrUpdate(account);

    }

    @Override
    public void disburseLoans(List<AccountPaymentParametersDto> accountPaymentParametersDtoList) throws Exception {
        StaticHibernateUtil.startTransaction();
        for (AccountPaymentParametersDto accountPaymentParametersDto : accountPaymentParametersDtoList) {
            LoanBO loan = getLoanPersistence().getAccount(accountPaymentParametersDto.getAccount().getAccountId());

            PaymentTypeEntity paymentTypeEntity = (PaymentTypeEntity) new MasterPersistence().getMasterDataEntity(
                    PaymentTypeEntity.class, accountPaymentParametersDto.getPaymentType().getValue());
            Money amount = new Money(loan.getCurrency(), accountPaymentParametersDto.getPaymentAmount());
            Date receiptDate = null;
            if (null != accountPaymentParametersDto.getReceiptDate()) {
                receiptDate = accountPaymentParametersDto.getReceiptDate().toDateMidnight().toDate();
            }
            Date transactionDate = accountPaymentParametersDto.getPaymentDate().toDateMidnight().toDate();
            String receiptId = accountPaymentParametersDto.getReceiptId();

            AccountPaymentEntity disbursalPayment = new AccountPaymentEntity(loan, amount, receiptId, receiptDate,
                    paymentTypeEntity, transactionDate);
            PersonnelBO personnelBO = personnelDao.findPersonnelById(accountPaymentParametersDto.getUserMakingPayment()
                    .getUserId());
            disbursalPayment.setCreatedByUser(personnelBO);

            loan.disburseLoan(disbursalPayment);
        }
        StaticHibernateUtil.commitTransaction();
    }

    @Override
    public AccountReferenceDto lookupLoanAccountReferenceFromId(Integer id) throws PersistenceException {
        LoanBO loan = getLoanPersistence().getAccount(id);
        if (null == loan) {
            throw new PersistenceException("loan not found for id " + id);
        }
        return new AccountReferenceDto(loan.getAccountId());
    }

    @Override
    public AccountReferenceDto lookupLoanAccountReferenceFromExternalId(String externalId) throws PersistenceException {
        LoanBO loan = getLoanPersistence().findByExternalId(externalId);
        if (null == loan) {
            throw new PersistenceException("loan not found for external id " + externalId);
        }
        return new AccountReferenceDto(loan.getAccountId());
    }

    /**
     * Note that, since we don't store or otherwise utilize the amount disbursed (passed in
     * AccountPaymentParametersDto.paymentAmount) we <em>do not</em> validate that digits after decimal for the amount
     * disbursed fit in an allowed range. We <em>do</em> check that the amount disbursed matches the full amount of the
     * loan.
     */
    @Override
    public List<InvalidPaymentReason> validateLoanDisbursement(AccountPaymentParametersDto payment) throws Exception {
        List<InvalidPaymentReason> errors = new ArrayList<InvalidPaymentReason>();
        LoanBO loanAccount = getLoanPersistence().getAccount(payment.getAccount().getAccountId());
        if ((loanAccount.getState() != AccountState.LOAN_APPROVED)
                && (loanAccount.getState() != AccountState.LOAN_DISBURSED_TO_LOAN_OFFICER)) {
            errors.add(InvalidPaymentReason.INVALID_LOAN_STATE);
        }
        disbursalAmountMatchesFullLoanAmount(payment, errors, loanAccount);
        if (!loanAccount.isTrxnDateValid(payment.getPaymentDate().toDateMidnight().toDate())) {
            errors.add(InvalidPaymentReason.INVALID_DATE);
        }
        if (!getLoanDisbursementTypes().contains(payment.getPaymentType())) {
            errors.add(InvalidPaymentReason.UNSUPPORTED_PAYMENT_TYPE);
        }
        if (!loanAccount.paymentAmountIsValid(new Money(loanAccount.getCurrency(), payment.getPaymentAmount()))) {
            errors.add(InvalidPaymentReason.INVALID_PAYMENT_AMOUNT);
        }
        return errors;
    }

    void disbursalAmountMatchesFullLoanAmount(AccountPaymentParametersDto payment, List<InvalidPaymentReason> errors,
            LoanBO loanAccount) {
        /* BigDecimal.compareTo() ignores scale, .equals() was explicitly avoided */
        if (loanAccount.getLoanAmount().getAmount().compareTo(payment.getPaymentAmount()) != 0) {
            errors.add(InvalidPaymentReason.INVALID_LOAN_DISBURSAL_AMOUNT);
        }
    }

    @Override
    public List<InvalidPaymentReason> validatePayment(AccountPaymentParametersDto payment) throws PersistenceException,
            AccountException {
        List<InvalidPaymentReason> errors = new ArrayList<InvalidPaymentReason>();
        AccountBO accountBo = getAccountPersistence().getAccount(payment.getAccount().getAccountId());
        if (!accountBo.isTrxnDateValid(payment.getPaymentDate().toDateMidnight().toDate())) {
            errors.add(InvalidPaymentReason.INVALID_DATE);
        }
        if (accountBo instanceof LoanBO) {
            if (!accountBo.getState().equals(AccountState.LOAN_ACTIVE_IN_GOOD_STANDING)
                    && !accountBo.getState().equals(AccountState.LOAN_ACTIVE_IN_BAD_STANDING)
                    && !accountBo.getState().equals(AccountState.CUSTOMER_ACCOUNT_ACTIVE)) {
                errors.add(InvalidPaymentReason.INVALID_LOAN_STATE);
            }
        }
        if (accountBo instanceof SavingsBO) {
            if (!accountBo.getState().equals(AccountState.SAVINGS_ACTIVE)) {
                errors.add(InvalidPaymentReason.INVALID_LOAN_STATE);
            }
        }
        if (AccountTypes.getAccountType(accountBo.getAccountType().getAccountTypeId()) == AccountTypes.LOAN_ACCOUNT) {
            if (!getLoanPaymentTypes().contains(payment.getPaymentType())) {
                errors.add(InvalidPaymentReason.UNSUPPORTED_PAYMENT_TYPE);
            }
        } else if (AccountTypes.getAccountType(accountBo.getAccountType().getAccountTypeId()) == AccountTypes.SAVINGS_ACCOUNT) {
            if (!getSavingsPaymentTypes().contains(payment.getPaymentType())) {
                errors.add(InvalidPaymentReason.UNSUPPORTED_PAYMENT_TYPE);
            }
        } else if (AccountTypes.getAccountType(accountBo.getAccountType().getAccountTypeId()) == AccountTypes.CUSTOMER_ACCOUNT) {
            if (!getFeePaymentTypes().contains(payment.getPaymentType())) {
                errors.add(InvalidPaymentReason.UNSUPPORTED_PAYMENT_TYPE);
            }
        }
        if (!accountBo.paymentAmountIsValid(new Money(accountBo.getCurrency(), payment.getPaymentAmount()))) {
            errors.add(InvalidPaymentReason.INVALID_PAYMENT_AMOUNT);
        }
        return errors;
    }

    @Override
    public List<AccountPaymentParametersDto> lookupPayments(AccountReferenceDto accountRef)
    throws PersistenceException {
        final int accountId = accountRef.getAccountId();
        final AccountBO account = getAccountPersistence().getAccount(accountId);
        List<AccountPaymentParametersDto> paymentDtos = new ArrayList<AccountPaymentParametersDto>();
        for (AccountPaymentEntity paymentEntity : account.getAccountPayments()) {
            paymentDtos.add(makePaymentDto(paymentEntity));
        }
        return paymentDtos;
    }

    public AccountPaymentParametersDto makePaymentDto(AccountPaymentEntity paymentEntity) {
        AccountPaymentParametersDto paymentDto = new AccountPaymentParametersDto(
                paymentEntity.getCreatedByUser() == null ?
                        new UserReferenceDto(paymentEntity.getAccountTrxns().iterator().next().
                                getPersonnel().getPersonnelId()) :
                                    new UserReferenceDto(paymentEntity.getCreatedByUser().getPersonnelId()),
                                    new AccountReferenceDto(paymentEntity.getAccount().getAccountId()),
                                    paymentEntity.getAmount().getAmount(),
                                    LocalDate.fromDateFields(paymentEntity.getPaymentDate()),
                                    new PaymentTypeDto(paymentEntity.getPaymentType().getId(),
                                            paymentEntity.getPaymentType().toString()),
                                            paymentEntity.getComment() == null ?
                                                    paymentEntity.toString() :
                                                        paymentEntity.getComment(),
                                                        paymentEntity.getReceiptDate() == null ? null :
                                                            LocalDate.fromDateFields(paymentEntity.getReceiptDate()),
                                                            paymentEntity.getReceiptNumber());
        return paymentDto;
    }


    public List<PaymentTypeDto> getSavingsPaymentTypes() throws PersistenceException {
        return getPaymentTypes(TrxnTypes.savings_deposit.getValue());
    }

    public List<PaymentTypeDto> getFeePaymentTypes() throws PersistenceException {
        return getPaymentTypes(TrxnTypes.fee.getValue());
    }

    @Override
    public List<PaymentTypeDto> getLoanPaymentTypes() throws PersistenceException {
        return getPaymentTypes(TrxnTypes.loan_repayment.getValue());
    }

    @Override
    public List<PaymentTypeDto> getLoanDisbursementTypes() throws PersistenceException {
        return getPaymentTypes(TrxnTypes.loan_disbursement.getValue());
    }

    private List<PaymentTypeDto> getPaymentTypes(short transactionType) throws PersistenceException {
        final Short IGNORED_LOCALE_ID = 1;
        List<PaymentTypeEntity> paymentTypeEntities = getAcceptedPaymentTypePersistence()
                .getAcceptedPaymentTypesForATransaction(IGNORED_LOCALE_ID, transactionType);
        List<PaymentTypeDto> paymentTypeDtos = new ArrayList<PaymentTypeDto>();
        for (PaymentTypeEntity paymentTypeEntity : paymentTypeEntities) {
            paymentTypeDtos.add(new PaymentTypeDto(paymentTypeEntity.getId(), paymentTypeEntity.getName()));
        }
        return paymentTypeDtos;
    }

    @Override
    public AccountReferenceDto lookupLoanAccountReferenceFromGlobalAccountNumber(String globalAccountNumber)
            throws PersistenceException {
        AccountBO accountBo = getAccountPersistence().findBySystemId(globalAccountNumber);
        if (null == accountBo) {
            throw new PersistenceException("loan not found for global account number " + globalAccountNumber);
        }
        return new AccountReferenceDto(accountBo.getAccountId());
    }

    @Override
    public AccountReferenceDto lookupLoanAccountReferenceFromClientGovernmentIdAndLoanProductShortName(
            String clientGovernmentId, String loanProductShortName) throws Exception {
        AccountBO accountBo = getAccountPersistence().findLoanByClientGovernmentIdAndProductShortName(
                clientGovernmentId, loanProductShortName);
        if (null == accountBo) {
            throw new PersistenceException("loan not found for client government id " + clientGovernmentId
                    + " and loan product short name " + loanProductShortName);
        }
        return new AccountReferenceDto(accountBo.getAccountId());
    }

    @Override
    public AccountReferenceDto lookupSavingsAccountReferenceFromClientGovernmentIdAndSavingsProductShortName(
            String clientGovernmentId, String savingsProductShortName) throws Exception {
        AccountBO accountBo = getAccountPersistence().findSavingsByClientGovernmentIdAndProductShortName(
                clientGovernmentId, savingsProductShortName);
        if (null == accountBo) {
            throw new PersistenceException("savings not found for client government id " + clientGovernmentId
                    + " and savings product short name " + savingsProductShortName);
        }
        return new AccountReferenceDto(accountBo.getAccountId());
    }

    @Override
    public AccountReferenceDto lookupLoanAccountReferenceFromClientPhoneNumberAndLoanProductShortName(
            String phoneNumber, String loanProductShortName) throws Exception {
        AccountBO accountBo = getAccountPersistence().findLoanByClientPhoneNumberAndProductShortName(
                phoneNumber, loanProductShortName);
        if (null == accountBo) {
            throw new PersistenceException("loan not found for client phone number " + phoneNumber
                    + " and loan product short name " + loanProductShortName);
        }
        return new AccountReferenceDto(accountBo.getAccountId());
    }

    @Override
    public AccountReferenceDto lookupSavingsAccountReferenceFromClientPhoneNumberAndSavingsProductShortName(
            String phoneNumber, String savingsProductShortName) throws Exception {
        AccountBO accountBo = getAccountPersistence().findSavingsByClientPhoneNumberAndProductShortName(
                phoneNumber, savingsProductShortName);
        if (null == accountBo) {
            throw new PersistenceException("savings not found for client phone number " + phoneNumber
                    + " and savings product short name " + savingsProductShortName);
        }
        return new AccountReferenceDto(accountBo.getAccountId());
    }

    @Override
    public BigDecimal getTotalPaymentDueAmount(AccountReferenceDto account) throws Exception {
        AccountBO accountBo = getAccountPersistence().getAccount(account.getAccountId());
        return accountBo.getTotalAmountDue().getAmount();
    }

    @Override
    public Object getMifosConfiguration(String propertyKey) {
        ConfigurationManager cfgMng = ConfigurationManager.getInstance();
        return cfgMng.getProperty(propertyKey);
    }

	@Override
	public boolean receiptExists(String receiptNumber) throws Exception {
		List<AccountPaymentEntity> existentPaymentsWIthGivenReceiptNumber = getAccountPersistence().findAccountPaymentsByReceiptNumber(receiptNumber);
		return existentPaymentsWIthGivenReceiptNumber != null && !existentPaymentsWIthGivenReceiptNumber.isEmpty();
	}
}