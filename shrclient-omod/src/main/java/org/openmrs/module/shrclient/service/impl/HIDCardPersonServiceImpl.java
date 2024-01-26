package org.openmrs.module.shrclient.service.impl;


import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.dao.HIDCardDao;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.service.HIDCardPersonService;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HIDCardPersonServiceImpl extends BaseOpenmrsService implements HIDCardPersonService {

    private final HIDCardDao hidCardDao;


    @Autowired
    public HIDCardPersonServiceImpl(Database database, AddressHierarchyEntryTranslationRepository entryTranslationRepository) {
        this.hidCardDao = new HIDCardDao(database, entryTranslationRepository);
    }


    @Override
    public HealthIdCard getHIDCardForPerson(String personUUID) {
        return hidCardDao.getHIDCardForPerson(personUUID);
    }
}
