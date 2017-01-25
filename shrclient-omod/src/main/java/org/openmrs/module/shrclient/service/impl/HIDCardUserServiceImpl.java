package org.openmrs.module.shrclient.service.impl;


import org.apache.log4j.Logger;
import org.openmrs.Role;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.dao.HIDCardDao;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.module.shrclient.service.HIDCardUserService;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_FIELD_WORKER_ROLE_NAME;
import static org.openmrs.module.fhir.utils.DateUtil.SIMPLE_DATE_WITH_SECS_FORMAT;
import static org.openmrs.module.fhir.utils.DateUtil.parseDate;

@Component
public class HIDCardUserServiceImpl extends BaseOpenmrsService implements HIDCardUserService {
    private static final Logger logger = Logger.getLogger(HIDCardUserServiceImpl.class);

    private UserService userService;
    private final HIDCardDao hidCardDao;
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    public HIDCardUserServiceImpl(Database database, GlobalPropertyLookUpService globalPropertyLookUpService,
                                  AddressHierarchyEntryTranslationRepository entryTranslationRepository) {
        this.globalPropertyLookUpService = globalPropertyLookUpService;
        this.hidCardDao = new HIDCardDao(database, entryTranslationRepository);
    }

    public List<User> getAllUsersWithFieldRole() {
        String value = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_FIELD_WORKER_ROLE_NAME);
        logger.debug("Fetching all users for role:- " + value);
        Role role = getUserService().getRole(value);
        if (null == role) return Collections.emptyList();
        ArrayList<User> users = new ArrayList<>();
        List<org.openmrs.User> allUsers = userService.getAllUsers();
        for (org.openmrs.User user : allUsers) {
            if (user.getAllRoles().contains(role)) {
                users.add(new User(user.getId(), user.getDisplayString()));
            }
        }
        return users;
    }

    @Override
    public List<HealthIdCard> getAllCardsByUserWithinDateRange(final int userId, final String from, final String to) throws IOException {
        try {
            return hidCardDao.getAllCardsByUserWithinDateRange(userId, parseDate(from, SIMPLE_DATE_WITH_SECS_FORMAT), parseDate(to, SIMPLE_DATE_WITH_SECS_FORMAT));
        } catch (ParseException e) {
            logger.error("Invalid Date Format", e);
            throw new IOException("Invalid Date Format");
        }
    }

    private UserService getUserService() {
        if (userService == null) {
            userService = Context.getUserService();
        }
        return userService;
    }
}
