package org.openmrs.module.shrclient.feeds.openmrs;

import com.sun.syndication.feed.atom.Feed;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.repository.AllFeeds;
import org.ict4h.atomfeed.server.service.EventFeedService;

import java.net.URI;

public class OpenMRSFeeds extends AllFeeds {
    private static final Logger log = Logger.getLogger(OpenMRSFeeds.class);

    private EventFeedService eventFeedService;
    private URI emrPatientUpdateUri;

    public OpenMRSFeeds(EventFeedService eventFeedService, URI emrPatientUpdateUri) {
        this.eventFeedService = eventFeedService;
        this.emrPatientUpdateUri = emrPatientUpdateUri;
    }

    @Override
    public Feed getFor(URI uri) {
        log.debug("Fetching feed for:" + uri);

        String eventUrlPath = uri.getPath();
        if (eventUrlPath.startsWith("/")) {
            eventUrlPath = eventUrlPath.substring(1, eventUrlPath.length());
        }
        if (eventUrlPath.endsWith("/")) {
            eventUrlPath = eventUrlPath.substring(0, eventUrlPath.length() - 1);
        }
        String[] pathInfo = eventUrlPath.split("/");
        String category = pathInfo[0];
        String feedId = (pathInfo.length > 1) ? pathInfo[1] : "recent";

        Feed responseFeed;
        if ("recent".equals(feedId)) {
            responseFeed = eventFeedService.getRecentFeed(uri, category);
        } else {
            responseFeed = eventFeedService.getEventFeed(uri, category, Integer.valueOf(feedId));
        }
        responseFeed.setOtherLinks(responseFeed.getAlternateLinks());
        return responseFeed;

    }


}
