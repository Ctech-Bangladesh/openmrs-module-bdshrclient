package org.openmrs.module.shrclient.feeds;


import org.ict4h.atomfeed.client.AtomFeedProperties;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.ict4h.atomfeed.client.repository.AllFeeds;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.ict4h.atomfeed.client.repository.jdbc.AllFailedEventsJdbcImpl;
import org.ict4h.atomfeed.client.repository.jdbc.AllMarkersJdbcImpl;
import org.ict4h.atomfeed.client.service.AtomFeedClient;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.jdbc.JdbcConnectionProvider;
import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.openmrs.api.context.Context;
import org.openmrs.module.atomfeed.transaction.support.AtomFeedSpringTransactionManager;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class CatchmentFeedProcessor {

    private String feedUrl;
    private Map<String, String> requestHeaders;
    private ClientRegistry clientRegistry;

    public CatchmentFeedProcessor(String feedUrl,
                                  Map<String, String> requestHeaders,
                                  ClientRegistry clientRegistry) {
        this.feedUrl = feedUrl;
        this.requestHeaders = requestHeaders;
        this.clientRegistry = clientRegistry;
    }

    public void process(EventWorker feedEventWorker, int maxFailedEvent) throws URISyntaxException {
        atomFeedClient(new URI(this.feedUrl), feedEventWorker,
                maxFailedEvent).processEvents();
    }

    public void processFailedEvents(EventWorker feedEventWorker, int maxFailedEvent) throws URISyntaxException {
        atomFeedClient(new URI(this.feedUrl), feedEventWorker,
                maxFailedEvent).processFailedEvents();
    }

    private AtomFeedProperties getAtomFeedProperties(int maxFailedEvents) {
        AtomFeedProperties atomProperties = new AtomFeedProperties();
        atomProperties.setMaxFailedEvents(maxFailedEvents);
        atomProperties.setHandleRedirection(true);
        return atomProperties;
    }

    private AtomFeedClient atomFeedClient(URI feedUri, EventWorker worker, int maxFailedEvents) {
        AFTransactionManager txManager = getAtomFeedTransactionManager();
        JdbcConnectionProvider connectionProvider = getConnectionProvider(txManager);
        AtomFeedProperties atomProperties = getAtomFeedProperties(maxFailedEvents);
        return new AtomFeedClient(
                getAllFeeds(clientRegistry),
                getAllMarkers(connectionProvider),
                getAllFailedEvent(connectionProvider),
                atomProperties,
                txManager,
                feedUri,
                worker);
    }

    private AllFailedEvents getAllFailedEvent(JdbcConnectionProvider connectionProvider) {
        return new AllFailedEventsJdbcImpl(connectionProvider);
    }

    private AllMarkers getAllMarkers(JdbcConnectionProvider connectionProvider) {
        return new AllMarkersJdbcImpl(connectionProvider);
    }

    private AllFeeds getAllFeeds(ClientRegistry clientRegistry) {
        return new CatchmentFeeds(requestHeaders, clientRegistry);
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
        List<PlatformTransactionManager> platformTransactionManagers = Context.getRegisteredComponents
                (PlatformTransactionManager.class);
        return platformTransactionManagers.get(0);
    }
}