package org.openmrs.module.shrclient.util;


import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ScheduledTaskHistory {
    private final Logger logger = Logger.getLogger(ScheduledTaskHistory.class);

    public static final String QUERY_FORMAT_TO_GET_FEED_URI_FOR_LAST_READ_ENTRY = "select feed_uri_for_last_read_entry from markers where feed_uri = '%s'";
    public static final String QUERY_FORMAT_TO_SET_FEED_URI_FOR_LAST_READ_ENTRY = "update markers set feed_uri_for_last_read_entry = '%s' where feed_uri = '%s'";
    public static final String QUERY_FORMAT_TO_SET_LAST_READ_ENTRY_ID = "update markers set last_read_entry_id = %s where feed_uri = '%s'";
    private Database database;

    @Autowired
    public ScheduledTaskHistory(Database database) {
        this.database = database;
    }

    public String getFeedUriForLastReadEntryByFeedUri(String feedUri) {
        final String query = String.format(QUERY_FORMAT_TO_GET_FEED_URI_FOR_LAST_READ_ENTRY, feedUri);
        return database.executeInTransaction(new Database.TxWork<String>() {
            @Override
            public String execute(Connection connection) {
                ResultSet resultSet = null;
                PreparedStatement statement = null;
                try {
                    statement = connection.prepareStatement(query);
                    resultSet = statement.executeQuery();
                    return resultSet.next() ? resultSet.getString(1) : StringUtils.EMPTY;
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while executing " + query + " : ", e);
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or result set", e);
                    }
                }
            }
        });
    }

    public void setLastReadEntryId(String id, String feedUri) {
        String query = String.format(QUERY_FORMAT_TO_SET_LAST_READ_ENTRY_ID, id, feedUri);
        database.save(query);
    }

    public void setFeedUriForLastReadEntryByFeedUri(String feedUriForLastReadEntry, String feedUri) {
        String query = String.format(QUERY_FORMAT_TO_SET_FEED_URI_FOR_LAST_READ_ENTRY, feedUriForLastReadEntry, feedUri);
        database.save(query);
    }
}
