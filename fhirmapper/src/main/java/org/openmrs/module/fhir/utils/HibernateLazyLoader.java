package org.openmrs.module.fhir.utils;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

public class HibernateLazyLoader {

    public <T> T load(T entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof HibernateProxy) {
            Hibernate.initialize(entity);
            entity = (T) ((HibernateProxy) entity).getHibernateLazyInitializer().getImplementation();
        }
        return entity;
    }
}
