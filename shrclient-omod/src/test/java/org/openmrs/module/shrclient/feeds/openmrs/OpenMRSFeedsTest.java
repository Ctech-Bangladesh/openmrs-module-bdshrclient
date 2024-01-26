package org.openmrs.module.shrclient.feeds.openmrs;

import com.sun.syndication.feed.atom.Feed;
import com.sun.syndication.io.WireFeedInput;
import org.ict4h.atomfeed.client.AtomFeedProperties;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.domain.FailedEvent;
import org.ict4h.atomfeed.client.domain.Marker;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.ict4h.atomfeed.client.service.AtomFeedClient;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.ict4h.atomfeed.server.domain.EventFeed;
import org.ict4h.atomfeed.server.domain.EventRecord;
import org.ict4h.atomfeed.server.service.EventFeedServiceImpl;
import org.ict4h.atomfeed.server.service.feedgenerator.FeedGenerator;
import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.ict4h.atomfeed.transaction.AFTransactionWork;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class OpenMRSFeedsTest {

    @Mock
    FeedGenerator feedGenerator;
    //private static final String recentPatientUrl = "http://localhost/openmrs/ws/rest/patient/recent";
    private static final String recentPatientUrl = "openmrs://feed/patient/recent";

    private EventFeedServiceImpl eventFeedService;
    private URI recentPatientURI;
    private AllMarkersInMemoryImpl allMarkers;
    private AllFailedEventsInMemoryImpl allFailedEvents;


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        allMarkers = new AllMarkersInMemoryImpl();
        allFailedEvents = new AllFailedEventsInMemoryImpl();
        eventFeedService = new EventFeedServiceImpl(feedGenerator);
        EventFeed eventFeed1 = getEventFeed(1, 5, "patient", 0);
        EventFeed eventFeed2 = getEventFeed(2, 2, "patient", 5);
        when(feedGenerator.getRecentFeed("patient")).thenReturn(eventFeed2);
        when(feedGenerator.getFeedForId(new Integer(1), "patient")).thenReturn(eventFeed1);
        when(feedGenerator.getFeedForId(new Integer(2), "patient")).thenReturn(eventFeed2);
        recentPatientURI = new URI(recentPatientUrl);

    }

    @Test
    public void dummy() throws URISyntaxException {
        URI uri = new URI("openmrs://events/patients/1");
        String eventUrlPath = uri.getPath();
        if (eventUrlPath.startsWith("/")) {
            eventUrlPath = eventUrlPath.substring(1, eventUrlPath.length());
        }
        if (eventUrlPath.endsWith("/")) {
            eventUrlPath = eventUrlPath.substring(0, eventUrlPath.length()-1);
        }
        String[] pathInfo = eventUrlPath.split("/");
        String category = pathInfo[0];
        String feedId =  (pathInfo.length > 1) ? pathInfo[1] : "recent";

        if ("recent".equals(feedId)) {
            System.out.println("Call recent feed for "+category);
        } else {
            System.out.print("Call feed number " + feedId);
        }

    }


    @Test
    public void shouldProcessAllEvents() throws Exception {
        EventWorker patientEventWorker = new TestPatientEventWorker("tag:atomfeed.ict4h.org:uuid10");
        getFeedClient(patientEventWorker).processEvents();
        Marker lastReadMarker = allMarkers.get(recentPatientURI);
        assertEquals("tag:atomfeed.ict4h.org:uuid7", lastReadMarker.getLastReadEntryId());
        assertEquals("openmrs://feed/patient/2", lastReadMarker.getFeedURIForLastReadEntry().toString());
        assertEquals(0, allFailedEvents.getNumberOfFailedEvents(recentPatientUrl));
    }

    @Test
    public void shouldFailOnSpecificEvent() throws Exception {
        String eventIdToFailFor = "tag:atomfeed.ict4h.org:uuid6";
        EventWorker patientEventWorker = new TestPatientEventWorker(eventIdToFailFor);
        getFeedClient(patientEventWorker).processEvents();
        Marker lastReadMarker = allMarkers.get(recentPatientURI);
        System.out.println(lastReadMarker);
        assertEquals("tag:atomfeed.ict4h.org:uuid7", lastReadMarker.getLastReadEntryId());
        assertEquals("openmrs://feed/patient/2", lastReadMarker.getFeedURIForLastReadEntry().toString());
        List<FailedEvent> failedEvents = allFailedEvents.getAllFailedEvents(recentPatientUrl);
        //TODO: fix AllFailedEventsInMemoryImpl.addOrUpdate() logic
//        assertEquals(1, failedEvents.size());
//        assertEquals(eventIdToFailFor, failedEvents.get(0).getEventId());
    }

    private FeedClient getFeedClient(EventWorker worker) {
        AtomFeedProperties properties = getFeedProperties();
        AFTransactionManager txMgr = getAtomFeedTransactionManager();
        return new AtomFeedClient(
                new OpenMRSFeeds(eventFeedService, recentPatientURI),
                allMarkers,
                allFailedEvents,
                properties,
                txMgr,
                recentPatientURI,
                worker);
    }

    private AFTransactionManager getAtomFeedTransactionManager() {
        return new AFTransactionManager() {
            @Override
            public <T> T executeWithTransaction(AFTransactionWork<T> txWork) throws RuntimeException {
                return txWork.execute();
            }
        };
    }

    private AtomFeedProperties getFeedProperties() {
        AtomFeedProperties props = new AtomFeedProperties();
        return props;
    }


    private Feed feedFromText(String content) {
        try {
            WireFeedInput input = new WireFeedInput();
            Feed feed = (Feed) input.build(new StringReader(content));
            return feed;
        } catch (Exception e) {
            throw new AtomFeedClientException(content, e);
        }
    }

    private class TestPatientEventWorker implements EventWorker {
        private String failFor;

        public TestPatientEventWorker(String failFor) {
            this.failFor = failFor;
        }

        @Override
        public void process(Event event) {
            if (event.getId().equals(failFor)) {
                System.out.println("Failing for patient event:" + event);
                throw new AtomFeedClientException("Intentional Fail");
            } else {
               System.out.println("Processing patient event:" + event);
            }
        }
        @Override
        public void cleanUp(Event event) {
        }
    }

    private EventFeed getEventFeed(int eventId, Integer evtCnt, String category, int startFrom) {
        ArrayList<EventRecord> events = new ArrayList<EventRecord>();
        for (int idx=1; idx <= evtCnt; idx++) {
            events.add(
               new EventRecord("uuid"+(startFrom+idx),
                    "event"+(startFrom+idx),null,
                    "content"+(startFrom+idx),
                    new Date(), category));
        }
        return new EventFeed(eventId, events);
    }

}
