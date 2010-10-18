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

package org.mifos.framework.hibernate.helper;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.mifos.framework.components.audit.util.helpers.AuditInterceptor;
import org.mifos.framework.exceptions.ConnectionNotFoundException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.orm.hibernate3.annotation.AnnotationSessionFactoryBean;

import java.sql.Connection;
import java.util.Properties;

public class HibernateUtil implements FactoryBean<HibernateUtil> {

    private static SessionFactory sessionFactory;
    private static final ThreadLocal<AuditInterceptor> interceptorTL = new ThreadLocal<AuditInterceptor>();
    private static final ThreadLocal<Session> sessionTL = new ThreadLocal<Session>();

    private static HibernateUtil hibernateUtil;

    protected HibernateUtil() {
    }

    public static HibernateUtil getInstance() {
        if(hibernateUtil == null) {
            hibernateUtil = new HibernateUtil();
        }
        return hibernateUtil;
    }

    public void shutdown() {
        try {
            sessionFactory.close();
        } catch (HibernateException e) {
           e.printStackTrace(System.out);
        }
        sessionFactory = null;
    }

    /**
     * @param sessionFactory the sessionFactory to set
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        HibernateUtil.sessionFactory = sessionFactory;
    }

    public Properties getHibernateProperties() {
        return ((AnnotationSessionFactoryBean) sessionFactory).getHibernateProperties();
    }

    /**
     * Return the hibernate session factory
     */
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * Return the current hibernate session for this thread. If this thread
     * doesn't have one, create a new one.
     */
    public Session getSessionTL() {
        try {
            getOrCreateSession();
        } catch (HibernateException he) {
            throw new ConnectionNotFoundException(he);
        }
        return sessionTL.get();

    }

    public AuditInterceptor getInterceptor() {
        return interceptorTL.get();
    }

    /**
     * Begin a transaction and store it in a thread-local variable, or return
     * the currently open transaction if there is one. The result is that the
     * transaction will remain open until a call to {@link #commitTransaction()}
     * or {@link #rollbackTransaction()}. Calling this method instead of just
     * creating a hibernate Transaction via the session is probably not a good
     * idea (see an example of the latter at
     * TestObjectPersistence#update(Session,
     * org.mifos.framework.business.PersistentObject)
     */
    public Transaction startTransaction() {
        Transaction transaction = getSessionTL().getTransaction();
        if (!transaction.isActive()) {
            transaction.begin();
        }
        return transaction;
    }

    public void closeSession() {
        Session session = sessionTL.get();
        if (session != null && session.isOpen()) {
            session.close();
        }
        sessionTL.set(null);
    }

    public void flushSession() {
        sessionTL.get().flush();
    }


    public void flushAndCloseSession() {
        Session session = sessionTL.get();        
        if (session != null && session.isOpen()) {
            session.flush();
            session.close();
        }
        sessionTL.set(null);

    }

    public void flushAndClearSession() {
        Session session = getSessionTL();
        if (session.isOpen()) {
            session.flush();
            session.clear();
        }
    }

    private Session getOrCreateSession() throws HibernateException {
        if (sessionTL.get() == null) {
            interceptorTL.set(new AuditInterceptor());
            Session session = sessionFactory.openSession(interceptorTL.get());
            sessionTL.set(session);
        }
        return sessionTL.get();
    }

    public void commitTransaction() {
        if (getSessionTL().getTransaction().isActive()) {
            getSessionTL().getTransaction().commit();
        }
    }

    public void rollbackTransaction() {
        if (getSessionTL().getTransaction().isActive()) {
            getSessionTL().getTransaction().rollback();
        }
    }

    @Override
    public HibernateUtil getObject() throws Exception {
        return getInstance();
    }

    @Override
    public Class<?> getObjectType() {
        return HibernateUtil.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void clearSession() {
        getSessionTL().clear();
    }

    public Connection getConnection() {
        return getSessionTL().connection();
    }
}
