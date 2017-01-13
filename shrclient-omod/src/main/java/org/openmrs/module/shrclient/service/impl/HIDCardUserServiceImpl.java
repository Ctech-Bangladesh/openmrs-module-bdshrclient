package org.openmrs.module.shrclient.service.impl;


import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.Role;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.module.shrclient.service.HIDCardUserService;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_FIELD_WORKER_ROLE_NAME;
import static org.openmrs.module.fhir.OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE_NAME;
import static org.openmrs.module.fhir.OpenMRSConstants.HID_CARD_ISSUED_ATTRIBUTE;
import static org.openmrs.module.fhir.utils.DateUtil.*;

@Component
public class HIDCardUserServiceImpl extends BaseOpenmrsService implements HIDCardUserService {
    private static final Logger logger = Logger.getLogger(HIDCardUserServiceImpl.class);

    private Database database;
    private GlobalPropertyLookUpService globalPropertyLookUpService;
    private UserService userService;

    @Autowired
    public HIDCardUserServiceImpl(Database database, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.database = database;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
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

    public List<HealthIdCard> getAllCardsByUserWithinDateRange(final int userId, final String from, final String to) {
        return database.executeInTransaction(new Database.TxWork<List<HealthIdCard>>() {
            @Override
            public List<HealthIdCard> execute(Connection connection) {
                List<HealthIdCard> healthIdCards = new ArrayList<>();
                try {
                    PreparedStatement statement = connection.prepareStatement(getQueryStatement());
                    statement.setString(1, HEALTH_ID_IDENTIFIER_TYPE_NAME);
                    statement.setInt(2, userId);
                    statement.setTimestamp(3, new Timestamp(parseDate(from, SIMPLE_DATE_WITH_SECS_FORMAT).getTime()));
                    statement.setTimestamp(4, new Timestamp(parseDate(to, SIMPLE_DATE_WITH_SECS_FORMAT).getTime()));
                    statement.setString(5, HID_CARD_ISSUED_ATTRIBUTE);
                    ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        healthIdCards.add(createHealthIdCard(resultSet));
                    }
                } catch (SQLException | ParseException e) {
                    logger.error("Error while fetching Health-Id Card details", e);
                }
                return healthIdCards;
            }
        });
    }

    private HealthIdCard createHealthIdCard(ResultSet resultSet) throws SQLException {
        HealthIdCard healthIdCard = new HealthIdCard();
        String givenName = resultSet.getString("given_name");
        String familyName = resultSet.getString("family_name");
        healthIdCard.setName(givenName + " " + familyName);
        healthIdCard.setDob(toDateString(resultSet.getDate("birthdate"), SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER));
        healthIdCard.setIssuedDate(toDateString(resultSet.getDate("date_created"), SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER));
        healthIdCard.setGender(getGender(resultSet));
        healthIdCard.setHid(resultSet.getString("identifier"));
        String address = createAddress(resultSet.getString("address1"),
                resultSet.getString("address2"),
                resultSet.getString("address3"),
                resultSet.getString("address4"),
                resultSet.getString("address5"),
                resultSet.getString("county_district"),
                resultSet.getString("state_province"));
        healthIdCard.setAddress(address);
        return healthIdCard;
    }

    private String getGender(ResultSet resultSet) throws SQLException {
        String gender = resultSet.getString("gender");
        if ("M".equals(gender)) {
            return "Male";
        }
        if ("F".equals(gender)) {
            return "Female";
        }
        if ("O".equals(gender)) {
            return "Transgender";
        }
        return gender;
    }

    private String createAddress(String address1, String address2, String address3, String address4,
                                 String address5, String countyDistrict, String stateProvince) {
        String delimiter = ", ";
        StringBuilder builder = new StringBuilder(address1);
        builder.append(delimiter);
        if (StringUtils.isNotBlank(address2)) {
            builder.append(address2).append(delimiter);
        }
        if (StringUtils.isNotBlank(address3)) {
            builder.append(address3).append(delimiter);
        }
        if (StringUtils.isNotBlank(address4)) {
            builder.append(address4).append(delimiter);
        }
        if (StringUtils.isNotBlank(address5)) {
            builder.append(address5).append(delimiter);
        }
        return builder.append(countyDistrict).append(delimiter).append(stateProvince).toString();
    }

    private String getQueryStatement() {
        return "SELECT pn.given_name, pn.family_name, p.gender, p.birthdate, p.date_created, pi.identifier, " +
                "pa.address1, pa.address2, pa.address3, pa.address4, pa.address5, pa.county_district, pa.state_province " +
                "FROM person_name pn, patient_identifier pi, person_address pa, person p WHERE p.person_id=pn.person_id AND " +
                "p.person_id=pa.person_id AND p.person_id=pi.patient_id AND p.voided = 0 AND pi.voided = 0 AND pi.identifier_type=" +
                "(SELECT patient_identifier_type_id FROM patient_identifier_type WHERE name=?) AND p.creator=? " +
                "AND p.date_created >= ? AND p.date_created <= ? AND p.person_id NOT IN " +
                "(SELECT person_id FROM person_attribute WHERE value='true' AND person_attribute_type_id=" +
                "(SELECT person_attribute_type_id FROM person_attribute_type WHERE name = ?));";
    }

    private UserService getUserService() {
        if (userService == null) {
            userService = Context.getUserService();
        }
        return userService;
    }

}
