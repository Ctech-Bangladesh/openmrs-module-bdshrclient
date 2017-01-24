package org.openmrs.module.shrclient.feeds.openmrs;

import org.ict4h.atomfeed.client.domain.Marker;
import org.ict4h.atomfeed.client.repository.AllMarkers;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

public class AllMarkersInMemoryImpl implements AllMarkers {
    private final HashMap<URI, Marker> map;

    public AllMarkersInMemoryImpl() {
        map = new HashMap<>();
    }

    @Override
    public Marker get(URI feedUri) {
        return map.get(feedUri);
    }

    @Override
    public void put(URI feedUri, String entryId, URI entryFeedUri) {
        map.put(feedUri, new Marker(feedUri, entryId, entryFeedUri));
    }

    @Override
    public List<Marker> getMarkerList() {
        return (List) map.values();
    }
}