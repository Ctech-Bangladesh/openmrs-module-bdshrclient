package org.openmrs.module.shrclient.feeds.openmrs;

import org.ict4h.atomfeed.client.AtomFeedProperties;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.ict4h.atomfeed.client.repository.AllFeeds;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.ict4h.atomfeed.client.repository.jdbc.AllFailedEventsJdbcImpl;
import org.ict4h.atomfeed.client.repository.jdbc.AllMarkersJdbcImpl;
import org.ict4h.atomfeed.client.service.AtomFeedClient;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.ict4h.atomfeed.jdbc.JdbcConnectionProvider;
import org.ict4h.atomfeed.server.repository.jdbc.AllEventRecordsJdbcImpl;
import org.ict4h.atomfeed.server.repository.jdbc.AllEventRecordsOffsetMarkersJdbcImpl;
import org.ict4h.atomfeed.server.repository.jdbc.ChunkingEntriesJdbcImpl;
import org.ict4h.atomfeed.server.service.EventFeedService;
import org.ict4h.atomfeed.server.service.EventFeedServiceImpl;
import org.ict4h.atomfeed.server.service.feedgenerator.FeedGeneratorFactory;
import org.ict4h.atomfeed.server.service.helper.ResourceHelper;
import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.openmrs.api.context.Context;
import org.openmrs.module.atomfeed.transaction.support.AtomFeedSpringTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class OpenMRSFeedClientFactory {

    public FeedClient getFeedClient(URI feedURI, EventWorker eventWorker, int maxFailedEvents) {
        AFTransactionManager txMgr = getAtomFeedTransactionManager();
        JdbcConnectionProvider connectionProvider = getConnectionProvider(txMgr);

        return new AtomFeedClient(
                getAllFeeds(feedURI, connectionProvider),
                getAllMarkers(connectionProvider),
                getAllFailedEvent(connectionProvider),
                getFeedProperties(maxFailedEvents),
                txMgr,
                feedURI,
                eventWorker);
    }

    public FeedClient getFeedClient(String feedURI, EventWorker eventWorker, int maxFailedEvents) throws URISyntaxException {
        URI uri = new URI(feedURI);
        return this.getFeedClient(uri,eventWorker, maxFailedEvents);
    }

    private AllFailedEvents getAllFailedEvent(JdbcConnectionProvider connectionProvider) {
        return new AllFailedEventsJdbcImpl(connectionProvider);
    }

    private AllMarkers getAllMarkers(JdbcConnectionProvider connectionProvider) {
        return new AllMarkersJdbcImpl(connectionProvider);
    }

    private AllFeeds getAllFeeds(URI feedURI, JdbcConnectionProvider connectionProvider) {
        return new OpenMRSFeeds(getEventFeedService(connectionProvider), feedURI);
    }


    private EventFeedService getEventFeedService(JdbcConnectionProvider connectionProvider) {
        return new EventFeedServiceImpl(new FeedGeneratorFactory().getFeedGenerator(
                new AllEventRecordsJdbcImpl(connectionProvider),
                new AllEventRecordsOffsetMarkersJdbcImpl(connectionProvider),
                new ChunkingEntriesJdbcImpl(connectionProvider),
                new ResourceHelper()));
    }

    private AtomFeedProperties getFeedProperties(int maxFailedEvents) {
        AtomFeedProperties props = new AtomFeedProperties();
        props.setMaxFailedEvents(maxFailedEvents);
        props.setHandleRedirection(true);
        return props;
    }

    private JdbcConnectionProvider getConnectionProvider(AFTransactionManager txMgr) {
        if (txMgr instanceof AtomFeedSpringTransactionManager) {
            return (AtomFeedSpringTransactionManager) txMgr;
        }
        throw new RuntimeException("Atom Feed TransactionManager should provide a connection provider.");
    }

    private AFTransactionManager getAtomFeedTransactionManager() {
        return new AtomFeedSpringTransactionManager(getSpringPlatformTransactionManager());
    }

    private PlatformTransactionManager getSpringPlatformTransactionManager() {
        List<PlatformTransactionManager> platformTransactionManagers = Context.getRegisteredComponents(PlatformTransactionManager.class);
        return platformTransactionManagers.get(0);
    }


}
