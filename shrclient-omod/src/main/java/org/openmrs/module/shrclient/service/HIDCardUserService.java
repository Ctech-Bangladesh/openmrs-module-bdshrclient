package org.openmrs.module.shrclient.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Transactional
public interface HIDCardUserService extends OpenmrsService {
    @Authorized(value = {"Print HID Card"}, requireAll = true)
    public List<User> getAllUsersWithFieldRole();

    @Authorized(value = {"Print HID Card"}, requireAll = true)
    public List<HealthIdCard> getAllCardsByUserWithinDateRange(int userId, String from, String to) throws IOException;
}
