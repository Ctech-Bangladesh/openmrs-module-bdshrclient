package org.openmrs.module.shrclient.util;

import org.hibernate.SessionFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Component
public class Database {
    private PlatformTransactionManager getTransactionManager() {
        final List<PlatformTransactionManager> registeredComponents = Context.getRegisteredComponents(PlatformTransactionManager.class);
        return registeredComponents.get(0);
    }

    public interface TxWork<T> {
        T execute(Connection connection);
    }

    public <T> T executeInTransaction(final TxWork<T> work) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(getTransactionManager());
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate.execute(new TransactionCallback<T>() {
            @Override
            public T doInTransaction(TransactionStatus transactionStatus) {
                return work.execute(getConnection());
            }
        });
    }

    private Connection getConnection() {
        ServiceContext serviceContext = ServiceContext.getInstance();
        Class klass = serviceContext.getClass();
        try {
            Field field = klass.getDeclaredField("applicationContext");
            field.setAccessible(true);
            ApplicationContext applicationContext = (ApplicationContext) field.get(serviceContext);
            SessionFactory factory = (SessionFactory) applicationContext.getBean("sessionFactory");
            return factory.getCurrentSession().connection();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save(final String query) {
        executeInTransaction(new TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                try {
                    PreparedStatement statement = connection.prepareStatement(query);
                    return statement.execute();
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while executing " + query + " : ", e);
                }
            }
        });
    }
}
