package org.openmrs.module.shrclient.dao;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.Database;
import org.openmrs.module.shrclient.util.Database.TxWork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component("bdShrClientIdMappingRepository")
public class IdMappingsRepository {

    private Logger logger = Logger.getLogger(IdMappingsRepository.class);

    @Autowired
    private Database database;

    public void saveMapping(final IdMapping idMapping) {
        database.executeInTransaction(new TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                if (!mappingExists(idMapping)) {
                    String query = "insert into shr_id_mapping (internal_id, external_id, type, uri) values (?,?,?,?)";
                    PreparedStatement statement = null;
                    try {
                        statement = connection.prepareStatement(query);
                        statement.setString(1, idMapping.getInternalId());
                        statement.setString(2, idMapping.getExternalId());
                        statement.setString(3, idMapping.getType());
                        statement.setString(4, idMapping.getUri());
                        statement.execute();
                    } catch (Exception e) {
                        throw new RuntimeException("Error occurred while creating id mapping", e);
                    } finally {
                        try {
                            if (statement != null) statement.close();
                        } catch (SQLException e) {
                            logger.warn("Could not close db statement or resultset", e);
                        }
                    }
                }
                return null;
            }
        });
    }


    private boolean mappingExists(final IdMapping idMapping) {
        return database.executeInTransaction(new TxWork<Boolean>() {
            @Override
            public Boolean execute(Connection connection) {
                String query = "select distinct map.internal_id from shr_id_mapping map where map.internal_id=? and map.external_id=?";
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                boolean result = false;
                try {
                    statement = connection.prepareStatement(query);
                    statement.setString(1, idMapping.getInternalId());
                    statement.setString(2, idMapping.getExternalId());
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        if (StringUtils.isNotBlank(resultSet.getString(1))) {
                            result = true;
                            break;
                        }
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
                return result;
            }
        });
    }

    public IdMapping findByExternalId(final String uuid) {
        return database.executeInTransaction(new TxWork<IdMapping>() {
            @Override
            public IdMapping execute(Connection connection) {
                String query = "select distinct map.internal_id, map.type, map.uri from shr_id_mapping map where map.external_id=?";
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                IdMapping result = null;
                try {
                    statement = connection.prepareStatement(query);
                    statement.setString(1, uuid);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        if (StringUtils.isNotBlank(resultSet.getString(1))) {
                            result = new IdMapping(resultSet.getString(1), uuid, resultSet.getString(2), resultSet.getString(3));
                            break;
                        }
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
                return result;
            }
        });
    }

    public IdMapping findByInternalId(final String uuid) {
        return database.executeInTransaction(new TxWork<IdMapping>() {
            @Override
            public IdMapping execute(Connection connection) {
                String query = "select distinct map.external_id, map.type, map.uri from shr_id_mapping map where map.internal_id=?";
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                IdMapping result = null;
                try {
                    statement = connection.prepareStatement(query);
                    statement.setString(1, uuid);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        if (StringUtils.isNotBlank(resultSet.getString(1))) {
                            result = new IdMapping(uuid, resultSet.getString(1), resultSet.getString(2), resultSet.getString(3));
                            break;
                        }
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
                return result;
            }
        });
    }

}
