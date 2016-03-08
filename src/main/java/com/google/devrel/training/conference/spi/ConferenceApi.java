package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = {
        Constants.WEB_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID }, description = "API for the Conference Central Backend application.")
public class ConferenceApi {
	
	private static final Logger LOGGER = Logger.getLogger(ConferenceApi.class.getName());

    /*
     * Get the display name from the user's email. For example, if the email is
     * lemoncake@example.com, then the display name becomes "lemoncake."
     */
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    /**
     * Creates or updates a Profile object associated with the given user
     * object.
     *
     * @param user -User.
     * @param profil - ProfileForm.
     * @return Profile.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    public Profile saveProfile(final User user, ProfileForm profileForm) throws UnauthorizedException {
    	
    	LOGGER.log(Level.INFO, "Save new profile");
    	
        String userId = null;
        String mainEmail = null;
        String displayName = null;
        TeeShirtSize teeShirtSize = TeeShirtSize.NOT_SPECIFIED;

        // If the user is not logged in, throw an UnauthorizedException
        if(null == user) {
        	throw new UnauthorizedException("Usuario no logado");
        }
        LOGGER.log(Level.INFO, String.format("Save new profile by user '%s'", user.getEmail()));

        // Set the teeShirtSize to the value sent by the ProfileForm, if sent otherwise leave it as the default value
        if(null != profileForm.getTeeShirtSize()) {
        	teeShirtSize = profileForm.getTeeShirtSize();
        }
        
        // Set the displayName to the value sent by the ProfileForm, if sent otherwise set it to null
        displayName = profileForm.getDisplayName();

        // Get the userId and mainEmail
        userId = user.getUserId();
        mainEmail = user.getEmail();
        if(null == displayName) {
        	displayName = extractDefaultDisplayNameFromEmail(mainEmail);
        }
        
       Profile profile = getProfile(user);
       if(null == profile) { 
    	   // Create a new Profile entity from the userId, displayName, mainEmail and teeShirtSize.
    	   profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
       } else {
    	   profile.update(displayName, teeShirtSize);
       }

        // Save the Profile entity in the datastore
        Key<Profile> keySave = ofy().save().entity(profile).now();
        
        LOGGER.log(Level.INFO, String.format("OK. Save new profile: '%s'", keySave.toString()));
        return profile;
    }

    /**
     *
     * @param user User
     * @return Profile object.
     * @throws UnauthorizedException when the User object is null.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        LOGGER.log(Level.INFO, String.format("Get profile from user '%s'", user.getEmail()));
        // load the Profile Entity
        String userId = user.getUserId();
		Key key = Key.create(Profile.class, userId); 
        Profile profile = (Profile) ofy().load().key(key).now();
        
        LOGGER.log(Level.INFO, "OK; get profile.");
        return profile;
    }
    
    /**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        LOGGER.log(Level.INFO, String.format("Saving new conference by user '%s'", user.getEmail()));
        
        // Get the userId of the logged in User
        String userId = user.getUserId();

        // Get the key for the User's Profile
        Key<Profile> profileKey = Key.create(Profile.class, userId); 

        // Allocate a key for the conference -- let App Engine allocate the ID
        // Don't forget to include the parent Profile in the allocated ID
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);

        // Get the Conference Id from the Key
        final long conferenceId = conferenceKey.getId();

        // Get the existing Profile entity for the current user if there is one
        // Otherwise create a new Profile entity with default values
        Profile profile = getProfile(user);
        if(profile == null) {
        	profile = saveProfile(user, new ProfileForm(null, null));
        }

        // Create a new Conference Entity, specifying the user's Profile entity
        // as the parent of the conference
        Conference conference = new Conference(conferenceId, userId, conferenceForm);

        // Save Conference and Profile Entities
        ofy().save().entity(conference).now();

        
        Queue queue = QueueFactory.getQueue("sendEmails");
        TaskOptions param = TaskOptions.Builder.withUrl("/task/sendConfirmation")
        		.param("email", profile.getMainEmail())
        		.param("info", conference.toString());
        queue.add(param);
      
        LOGGER.log(Level.INFO, String.format("OK, new conference create '%s'", conferenceKey));
        return conference;
     }
    
    
    @ApiMethod( name = "getConferencesCreated", path = "getConferencesCreated", httpMethod = HttpMethod.POST)
    public List<Conference> getConferenceCreated(final User user) throws UnauthorizedException {
    	if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
    	LOGGER.log(Level.INFO, String.format("Get conference created by user loggin"));
    	
    	Key<Profile> profileKey = Key.create(Profile.class, user.getUserId());
    	
    	Query<Conference> query = ofy().load().type(Conference.class).ancestor(profileKey).order(Conference.NAME);
    	return query.list();
    }
    
    @ApiMethod( name = "getConferenceByFilters", path="getConferenceByFilters", httpMethod = HttpMethod.POST)
    public List<Conference> qetConferenceByFilters(final User user) throws UnauthorizedException {
    	if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
    	LOGGER.log(Level.INFO, "Get conference with some filters");
    	
    	 Query<Conference> query = ofy().load().type(Conference.class).order(Conference.NAME);
    	 query = query.filter(Conference.CITY + " =", "London");
    	 query = query.filter(Conference.TOPICS + " in", Arrays.asList("Programming Languages"));
    	 return query.list();
    }
    
    @ApiMethod( name = "queryConferences", path = "queryConferences", httpMethod = HttpMethod.POST)
    public List<Conference> queryConferenceByFilters(final User user, ConferenceQueryForm conferenceQueryForm) throws UnauthorizedException {
    	if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
    	LOGGER.log(Level.INFO, "Query conference with filter sending by client");
    	
    	Query<Conference> query = conferenceQueryForm.getQuery();
    	List<Conference> result = query.list();
    	
    	LOGGER.log(Level.INFO, "Results: ");
    	for(Conference conference : result) {
    		LOGGER.log(Level.INFO, String.format("\t Conference: '%s'", conference.getName()));
    	}
    	
    	return result;
    }
    
    
    /**
     * Returns a Conference object with the given conferenceId.
     *
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return a Conference object with the given conferenceId.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(name = "getConference", path = "conference/{websafeConferenceKey}", httpMethod = HttpMethod.GET)
    public Conference getConference( @Named("websafeConferenceKey") final String websafeConferenceKey) throws NotFoundException {
        
    	Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(conferenceKey).now();
        if (conference == null) {
            throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
        }
        return conference;
    }


  /**
     * Register to attend the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return Boolean true when success, otherwise false
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod( name = "registerForConference", path = "conference/{websafeConferenceKey}/registration", httpMethod = HttpMethod.POST)
    public WrappedBoolean registerForConference_SKELETON(final User user, @Named("websafeConferenceKey") final String websafeConferenceKey)
            	throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {

    	if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Start transaction
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
        	@Override
			public WrappedBoolean run() {
				
                try {
	                Conference conference = getConference(websafeConferenceKey);
	                if (conference == null) {
	                    return new WrappedBoolean (false, "No Conference found with key: " + websafeConferenceKey);
	                }
	
	                // Get the user's Profile entity
	                Profile profile = getProfile(user);
	                if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
	                	return new WrappedBoolean (false, "Already registered");
	                	
	                } else if (conference.getSeatsAvailable() <= 0) {
	                	return new WrappedBoolean (false, "No seats available");
	                	
	                } else {
	                    // Add the websafeConferenceKey to the profile's conferencesToAttend property
	                    profile.addToConferenceKeysToAttend(websafeConferenceKey);
	                    
	                    // Decrease the conference's seatsAvailable
	                    conference.bookSeats(1);
	 
	                    // Save the Conference and Profile entities
	                    ofy().save().entity(conference).now();
	                    
	                    return new WrappedBoolean(true, "Registration successful");
	                }
                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");
                }
            }
        });
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException (result.getReason());
            }
            else if (result.getReason() == "Already registered") {
                throw new ConflictException("You have already registered");
            }
            else if (result.getReason() == "No seats available") {
                throw new ConflictException("There are no seats available");
            }
            else {
                throw new ForbiddenException("Unknown exception");
            }
        }
        return result;
    }


 /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
	@ApiMethod(name = "getConferencesToAttend", path = "getConferencesToAttend", httpMethod = HttpMethod.GET)
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        // Get the Profile entity for the user
        Profile profile = getProfile(user); // Change this;
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }

        // Get the value of the profile's conferenceKeysToAttend property
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();

        // Iterate over keyStringsToAttend, and return a Collection of the
        // Conference entities that the user has registered to atend
        List<Conference> conferences = new ArrayList<Conference>();
        for(String keyConference: keyStringsToAttend) {
        	conferences.add(getConference(keyConference));
        }
        return conferences;
    }
	
	
	public WrappedBoolean unregisterFromConference(final User user,
			@Named("websafeConferenceKey") final String websafeConferenceKey)
			throws UnauthorizedException, NotFoundException,
			ForbiddenException, ConflictException {
		// If not signed in, throw a 401 error.
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}

		WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
			@Override
			public WrappedBoolean run() {
				Key<Conference> conferenceKey = Key
						.create(websafeConferenceKey);
				Conference conference = ofy().load().key(conferenceKey).now();
				// 404 when there is no Conference with the given conferenceId.
				if (conference == null) {
					return new WrappedBoolean(false,
							"No Conference found with key: "
									+ websafeConferenceKey);
				}

				// Un-registering from the Conference.
				Profile profile = null;
				try {
					profile = getProfile(user);
				} catch (UnauthorizedException e) {
					e.printStackTrace();
				}
				if (profile.getConferenceKeysToAttend().contains(
						websafeConferenceKey)) {
					profile.unregisterFromConference(websafeConferenceKey);
					conference.giveBackSeats(1);
					ofy().save().entities(profile, conference).now();
					return new WrappedBoolean(true);
				} else {
					return new WrappedBoolean(false,
							"You are not registered for this conference");
				}
			}
		});
		// if result is false
		if (!result.getResult()) {
			if (result.getReason().contains("No Conference found with key")) {
				throw new NotFoundException(result.getReason());
			} else {
				throw new ForbiddenException(result.getReason());
			}
		}
		// NotFoundException is actually thrown here.
		return new WrappedBoolean(result.getResult());
	}
    
	
	@ApiMethod(name = "getAnnouncement", path = "announcement", httpMethod = HttpMethod.GET)
    public Announcement getAnnouncement() {
		LOGGER.log(Level.INFO, "Get annocencement of conference with less than five seats availables");
		
		MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object message = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
        if (message != null) {
            return new Announcement(message.toString());
        }
        return null;
    }

	
    
    /**
     * Just a wrapper for Boolean.
     * We need this wrapped Boolean because endpoints functions must return
     * an object instance, they can't return a Type class such as
     * String or Integer or Boolean
     */
    public static class WrappedBoolean {

        private final Boolean result;
        private final String reason;

        public WrappedBoolean(Boolean result) {
            this.result = result;
            this.reason = "";
        }

        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public Boolean getResult() {
            return result;
        }

        public String getReason() {
            return reason;
        }
    }
}