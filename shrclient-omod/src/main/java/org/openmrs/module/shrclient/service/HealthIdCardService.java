package org.openmrs.module.shrclient.service;


import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.fhir.OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE_NAME;
import static org.openmrs.module.fhir.utils.DateUtil.SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER;
import static org.openmrs.module.fhir.utils.DateUtil.parseDate;

@Component
public class HealthIdCardService {
    private Database database;

    @Autowired
    public HealthIdCardService(Database database) {
        this.database = database;
    }

    public List<HealthIdCard> getAllCardsByQuery(final int userId, final String from, final String to) {
        return database.executeInTransaction(new Database.TxWork<List<HealthIdCard>>() {
            @Override
            public List<HealthIdCard> execute(Connection connection) {
                List<HealthIdCard> healthIdCards = new ArrayList<>();
                try {
                    PreparedStatement statement = connection.prepareStatement(getQueryStatement());
                    statement.setString(1, HEALTH_ID_IDENTIFIER_TYPE_NAME);
                    statement.setInt(2, userId);
                    statement.setDate(3, new Date(parseDate(from, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER).getTime()));
                    statement.setDate(4, new Date(parseDate(to, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER).getTime()));
                    ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        healthIdCards.add(createHealthIdCard(resultSet));
                    }
                } catch (SQLException | ParseException e) {
                    e.printStackTrace();
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
        healthIdCard.setGender(resultSet.getString("gender"));
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

    private String createAddress(String address1, String address2, String address3, String address4,
                                 String address5, String countyDistrict, String stateProvince) {
        StringBuilder builder = new StringBuilder(address1);
        if (StringUtils.isNotBlank(address2)) {
            builder.append(address2);
        }
        if (StringUtils.isNotBlank(address3)) {
            builder.append(address3);
        }
        if (StringUtils.isNotBlank(address4)) {
            builder.append(address4);
        }
        if (StringUtils.isNotBlank(address5)) {
            builder.append(address5);
        }
        return builder.append(countyDistrict).append(stateProvince).toString();
    }

    private String getQueryStatement() {
        return "SELECT pn.given_name, pn.family_name, p.gender, pi.identifier, " +
                "pa.address1, pa.address2, pa.address3, pa.address4, pa.address5, pa.county_district, pa.state_province " +
                "FROM person_name pn, patient_identifier pi , person_address pa, person p WHERE p.person_id=pn.person_id AND " +
                "p.person_id=pa.person_id AND p.person_id=pi.patient_id AND pi.identifier_type=" +
                "(SELECT patient_identifier_type_id FROM patient_identifier_type WHERE name=?) AND " +
                "p.creator=? AND p.date_created BETWEEN ? AND ?;";
    }
}
