package org.openmrs.module.shrclient.util;

import org.openmrs.api.context.Context;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

public class TransactionHelper {
    public interface TxWork<T> {
        T execute();
    }

    public static <T> T executeInTransaction(final TxWork<T> work) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(getTransactionManager());
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate.execute(new TransactionCallback<T>() {
            @Override
            public T doInTransaction(TransactionStatus transactionStatus) {
                return work.execute();
            }
        });
    }

    private static PlatformTransactionManager getTransactionManager() {
        final List<PlatformTransactionManager> registeredComponents = Context.getRegisteredComponents(PlatformTransactionManager.class);
        return registeredComponents.get(0);
    }
}
