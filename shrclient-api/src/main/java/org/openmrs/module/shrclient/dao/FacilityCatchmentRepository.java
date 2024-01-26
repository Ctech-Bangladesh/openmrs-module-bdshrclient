package org.openmrs.module.shrclient.dao;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.model.FacilityCatchment;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.Database;
import org.openmrs.module.shrclient.util.Database.TxWork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component("bdShrClientFacilityCatchmentRepository")
public class FacilityCatchmentRepository {

    private Logger logger = Logger.getLogger(FacilityCatchmentRepository.class);

    @Autowired
    private Database database;

    public void saveMappings(final int locationId, final List<String> catchments) {
        final Set<String> uniqueCatchments = findUniqueCatchments(catchments);
        database.executeInTransaction(new TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                String deleteQuery = "delete from facility_catchment where location_id=?";
                String createQuery = "insert into facility_catchment (location_id, catchment) values (?,?)";
                PreparedStatement deleteStatement = null;
                PreparedStatement createStatement = null;
                try {
                    deleteStatement = connection.prepareStatement(deleteQuery);
                    deleteStatement.setInt(1, locationId);
                    deleteStatement.execute();

                    for (String catchment : uniqueCatchments) {
                        createStatement = connection.prepareStatement(createQuery);
                        createStatement.setInt(1, locationId);
                        createStatement.setString(2, catchment);
                        createStatement.execute();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while creating facility catchment mapping", e);
                } finally {
                    try {
                        if (deleteStatement != null) deleteStatement.close();
                        if (createStatement != null) createStatement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db createStatement, deleteStatement or resultset", e);
                    }
                }
                return null;
            }
        });
    }

    private Set<String> findUniqueCatchments(List<String> catchments) {
        Set<String> uniqueCatchments = new HashSet<>();
        uniqueCatchments.addAll(catchments);
        return uniqueCatchments;
    }


    public List<FacilityCatchment> findByCatchment(final String catchment) {
        return database.executeInTransaction(new TxWork<List<FacilityCatchment>>() {
            @Override
            public List<FacilityCatchment> execute(Connection connection) {
                List<FacilityCatchment> facilityCatchmentMappings = new ArrayList<FacilityCatchment>();
                String query = "select location_id, catchment  from facility_catchment where catchment=?";
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                try {
                    statement = connection.prepareStatement(query);
                    statement.setString(1, catchment);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        facilityCatchmentMappings.add(new FacilityCatchment(resultSet.getInt(1), catchment));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while querying id mapping", e);
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or result set", e);
                    }
                }
                return facilityCatchmentMappings;
            }
        });
    }

    public List<FacilityCatchment> findByFacilityLocationId(final int locationId) {
        return database.executeInTransaction(new TxWork<List<FacilityCatchment>>() {
            @Override
            public List<FacilityCatchment> execute(Connection connection) {
                List<FacilityCatchment> facilityCatchmentMappings = new ArrayList<FacilityCatchment>();
                String query = "select location_id, catchment  from facility_catchment where location_id=?";
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                IdMapping result = null;
                try {
                    statement = connection.prepareStatement(query);
                    statement.setInt(1, locationId);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        facilityCatchmentMappings.add(new FacilityCatchment(locationId, resultSet.getString(1)));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while querying id mapping", e);
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or result set", e);
                    }
                }
                return facilityCatchmentMappings;
            }
        });
    }
}
