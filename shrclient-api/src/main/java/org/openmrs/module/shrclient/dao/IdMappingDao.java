package org.openmrs.module.shrclient.dao;


import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class IdMappingDao {

    protected Logger logger;
    protected Database database;

    public IdMappingDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public abstract String getMappingTable();

    public abstract String getFetchByExternalIdSql();

    public abstract String getFetchByInternalIdSql();

    public abstract String getFetchByHealthIdSql();

    public abstract PreparedStatement getInsertIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException;

    public abstract PreparedStatement getUpdateIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException;

    public abstract PreparedStatement getCheckMappingExistsStatement(Connection connection, IdMapping idMapping) throws SQLException;

    public abstract IdMapping buildIdMapping(ResultSet resultSet) throws SQLException;

    private boolean mappingExists(final IdMapping idMapping) {
        return database.executeInTransaction(new Database.TxWork<Boolean>() {
            @Override
            public Boolean execute(Connection connection) {
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                boolean result = false;
                try {
                    statement = getCheckMappingExistsStatement(connection, idMapping);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        if (null != resultSet.getObject(1)) {
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

    protected void saveOrUpdateIdMapping(final IdMapping idMapping) {
        database.executeInTransaction(new Database.TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                PreparedStatement statement = null;
                try {
                    if (!mappingExists(idMapping)) {
                        statement = getInsertIdMappingStatement(connection, idMapping);
                        statement.execute();
                    } else {
                        statement = getUpdateIdMappingStatement(connection, idMapping);
                        if (null != statement) {
                            statement.executeUpdate();
                        }
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while creating id mapping", e);
                } finally {
                    try {
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or resultset", e);
                    }
                }
                return null;
            }

        });
    }

    protected IdMapping findByExternalId(final String externalId) {
        List<IdMapping> idMappings = getIdMappings(externalId, getFetchByExternalIdSql());
        return idMappings.size() > 0 ? idMappings.get(0) : null;
    }

    protected List<IdMapping> findMappingsByExternalId(final String externalId) {
        return getIdMappings(externalId, getFetchByExternalIdSql());
    }

    protected IdMapping findByInternalId(final String internalId) {
        List<IdMapping> idMappings = getIdMappings(internalId, getFetchByInternalIdSql());
        return idMappings.size() > 0 ? idMappings.get(0) : null;
    }

    protected List<IdMapping> findByHealthId(String healthID) {
        String likeHealthId = "%" + healthID + "%";
        return getIdMappings(likeHealthId, getFetchByHealthIdSql());
    }

    protected PreparedStatement getBatchStatement(Connection connection, List<IdMapping> idMappings) throws SQLException {
        if (idMappings.size() == 0) {
            return null;
        }
        String updateURIByInternalIdSql = String.format("update %s set uri=?, last_sync_datetime=? where internal_id=?", getMappingTable());
        PreparedStatement preparedStatement = connection.prepareStatement(updateURIByInternalIdSql);
        for (IdMapping idMapping : idMappings) {
            preparedStatement.setString(1, idMapping.getUri());
            preparedStatement.setTimestamp(2, idMapping.getLastSyncTimestamp());
            preparedStatement.setString(3, idMapping.getInternalId());
            preparedStatement.addBatch();
        }
        return preparedStatement;
    }

    private List<IdMapping> getIdMappings(final String id, final String query) {
        return database.executeInTransaction(new Database.TxWork<List<IdMapping>>() {
            @Override
            public List<IdMapping> execute(Connection connection) {
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                List<IdMapping> idMappings = new ArrayList<>();
                try {
                    statement = connection.prepareStatement(query);
                    statement.setString(1, id);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        if (StringUtils.isNotBlank(resultSet.getString(1))) {
                            idMappings.add(buildIdMapping(resultSet));
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while querying id mapping", e);
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or idmapping set", e);
                    }
                }
                return idMappings;
            }
        });
    }
}
