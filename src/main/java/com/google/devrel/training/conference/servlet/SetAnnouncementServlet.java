package com.google.devrel.training.conference.servlet;

import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.repackaged.com.google.common.base.Joiner;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Conference;

@SuppressWarnings("serial")
public class SetAnnouncementServlet extends HttpServlet {
	
	private static final Logger LOGGER = Logger.getLogger(SetAnnouncementServlet.class.getName());
	
	private MemcacheService memCacheService = MemcacheServiceFactory.getMemcacheService();
	
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOGGER.log(Level.INFO, "Get announcement of conference with less than five seats availables.");
		
		getAnnouncement(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOGGER.log(Level.INFO, "Get announcement of conference with less than five seats availables.");
		
		getAnnouncement(req, resp);
		
	}

	private void getAnnouncement(HttpServletRequest req,
			HttpServletResponse resp) {
		
        // Query for conferences with less than 5 seats left
        List<Conference> list = ofy().load().type(Conference.class)
        		.filter(Conference.SEATS_AVAILABLE + " <", 5)
        		.filter(Conference.SEATS_AVAILABLE + " >", 0)
        		.order(Conference.SEATS_AVAILABLE)
        		.list();

        // Iterate over the conferences with less than 5 seats less and get the name of each one
        List<String> conferenceNames = new ArrayList<>(0);
        for (Conference conference : list) {
            conferenceNames.add(conference.getName());
        }
        if (conferenceNames.size() > 0) {

            // Build a String that announces the nearly sold-out conferences
            StringBuilder announcementStringBuilder = new StringBuilder(
                    "Last chance to attend! The following conferences are nearly sold out: ");
            Joiner joiner = Joiner.on(", ").skipNulls();
            announcementStringBuilder.append(joiner.join(conferenceNames));

            // Put the announcement String in memcache, keyed by Constants.MEMCACHE_ANNOUNCEMENTS_KEY
            memCacheService.put(Constants.MEMCACHE_ANNOUNCEMENTS_KEY, announcementStringBuilder.toString());
        }

        // Set the response status to 204 which means the request was successful but there's no data to send back
        // Browser stays on the same page if the get came from the browser
        resp.setStatus(200);
	}
}