package org.openmrs.module.shrclient.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;

import java.util.List;

public interface HIDCardUserService {
    @Authorized(value = {"Print HID Card"}, requireAll = true)
    public List<User> getAllUsers();

    @Authorized(value = {"Print HID Card"}, requireAll = true)
    public List<HealthIdCard> getAllCardsByUserWithinDateRange(int userId, String from, String to);

}
