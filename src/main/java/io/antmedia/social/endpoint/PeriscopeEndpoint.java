package io.antmedia.social.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.api.periscope.AuthorizationEndpoints;
import io.antmedia.api.periscope.BroadcastEndpoints;
import io.antmedia.api.periscope.PeriscopeEndpointFactory;
import io.antmedia.api.periscope.RegionEndpoints;
import io.antmedia.api.periscope.response.AuthorizationResponse;
import io.antmedia.api.periscope.response.CheckDeviceCodeResponse;
import io.antmedia.api.periscope.response.CreateBroadcastResponse;
import io.antmedia.api.periscope.response.CreateDeviceCodeResponse;
import io.antmedia.datastore.db.types.Endpoint;
import io.antmedia.datastore.preference.PreferenceStore;

public class PeriscopeEndpoint extends VideoServiceEndpoint {

	private static final String serviceName = "periscope";
	private AuthorizationEndpoints authorizationEndpoint;
	private String device_code;
	private PeriscopeEndpointFactory periscopeEndpointFactory;
	private BroadcastEndpoints broadcastEndpoint;
	private RegionEndpoints regionEndpoint;
	private String accessToken;
	private String region;
	private long expireTimeMS;

	protected static Logger logger = LoggerFactory.getLogger(PeriscopeEndpoint.class);

	public PeriscopeEndpoint(String clientId, String clientSecret, PreferenceStore dataStore) {
		super(clientId, clientSecret, dataStore);
	}


	@Override
	public String getName() {
		return serviceName;
	}

	@Override
	public DeviceAuthParameters askDeviceAuthParameters() throws Exception {
		AuthorizationEndpoints authorizationEndpoint = getAuthorizationEndpoint();
		CreateDeviceCodeResponse response = authorizationEndpoint.createDeviceCode(getClientId());
		DeviceAuthParameters authParameters = null;
		if (response != null) {
			authParameters = new DeviceAuthParameters();
			authParameters.device_code = response.device_code;
			this.device_code = response.device_code;
			authParameters.expires_in = response.expires_in;
			authParameters.interval = response.interval;
			authParameters.user_code = response.user_code;
			authParameters.verification_url = response.associate_url;
		}

		return authParameters;
	}

	private AuthorizationEndpoints getAuthorizationEndpoint() {
		if (authorizationEndpoint == null) {
			authorizationEndpoint = new AuthorizationEndpoints();
		}
		return authorizationEndpoint;
	}

	@Override
	public boolean askIfDeviceAuthenticated() throws Exception {
		AuthorizationEndpoints authorizationEndpoint = getAuthorizationEndpoint();

		CheckDeviceCodeResponse checkDeviceCode = authorizationEndpoint.checkDeviceCode(device_code, getClientId());

		logger.warn("State: " + checkDeviceCode.state);

		boolean result = false;
		if ( checkDeviceCode.state.equals("associated")) {
			saveCredentials(checkDeviceCode.access_token, checkDeviceCode.refresh_token, String.valueOf(checkDeviceCode.expires_in), checkDeviceCode.token_type);
			init(checkDeviceCode.access_token, checkDeviceCode.refresh_token, (long)checkDeviceCode.expires_in, checkDeviceCode.token_type, System.currentTimeMillis());
			result = true;
		}
		return result;
	}

	@Override
	public boolean isAuthenticated() {
		return periscopeEndpointFactory != null && accessToken != null && (accessToken.length() > 0);
	}

	@Override
	public void resetCredentials() {
		super.resetCredentials();
		accessToken = null;
		
	}
	
	@Override
	public Endpoint createBroadcast(String name, String description, boolean is360, boolean isPublic,
			int videoHeight) throws Exception {
		if (broadcastEndpoint == null) {
			throw new Exception("First authenticated the server");
		}

		updateTokenIfRequired();
		CreateBroadcastResponse createBroadcastResponse = broadcastEndpoint.createBroadcast(getRegion(), is360);

		String rtmpUrl = createBroadcastResponse.encoder.rtmp_url + "/" + createBroadcastResponse.encoder.stream_key;
		return new Endpoint(createBroadcastResponse.broadcast.id, null, name, rtmpUrl, getName());
	}

	private String getRegion() {
		if (region != null) {
			try {
				region = regionEndpoint.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return region;
	}


	@Override
	public void publishBroadcast(Endpoint endpoint) throws Exception {
		if (broadcastEndpoint == null) {
			throw new Exception("First authenticated the server");
		}
		if (endpoint.broadcastId == null) {
			throw new Exception("No broadcast is available, call createBroadcast function before calling publish broadcast");
		}
		updateTokenIfRequired();
		broadcastEndpoint.publishBroadcast(endpoint.broadcastId, endpoint.name, false, "en_US");
	}

	@Override
	public void stopBroadcast(Endpoint endpoint) throws Exception {
		if (broadcastEndpoint == null) {
			throw new Exception("First authenticated the server");
		}
		if (endpoint.broadcastId == null) {
			throw new Exception("No broadcast is available");
		}
		updateTokenIfRequired();
		broadcastEndpoint.stopBroadcast(endpoint.broadcastId);
	}

	private void updateTokenIfRequired() {
		if (expireTimeMS < (System.currentTimeMillis() + THREE_DAYS_IN_MS)) {
			AuthorizationResponse token = periscopeEndpointFactory.refreshToken(clientId, clientSecret);
			saveCredentials(token.access_token, token.refresh_token, String.valueOf(token.expires_in), token.token_type);
			init(token.access_token, token.refresh_token, Long.valueOf(token.expires_in), token.token_type, System.currentTimeMillis());
		}
	}

	@Override
	public void init(String accessToken, String refreshToken, long expireTime, String tokenType, long authTimeInMS) {
		this.accessToken = accessToken;
		periscopeEndpointFactory = new PeriscopeEndpointFactory(tokenType, accessToken, refreshToken);
		expireTimeMS = authTimeInMS + expireTime * 1000;
		broadcastEndpoint = periscopeEndpointFactory.getBroadcastEndpoints();
		regionEndpoint = periscopeEndpointFactory.getRegionEndpoints();
		

	}

}
