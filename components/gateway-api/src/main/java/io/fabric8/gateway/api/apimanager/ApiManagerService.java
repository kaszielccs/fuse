package io.fabric8.gateway.api.apimanager;

import java.util.Map;

import io.fabric8.gateway.api.handlers.http.HttpGatewayHandler;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;

/**
 * Fabric8's API Manager interface. The interface provides access to a 3rd party 'Engine',
 * as well as access to handler needed for the asynchronous nature of Fabric8's HTTPGateway.
 * The ApiManager 
 */
public interface ApiManagerService {

	public final static String VERTX        = "vertx";
	public final static String HTTP_GATEWAY = "httpGateway";
	public final static String PORT         = "port";
	
	public void init(Map<String,Object> config);
	
	/** Get a reference to a 3rd Party API Manager Engine */
	public Object getEngine();
	
	/**
     * Creates a Handler of type HttpClientResponse. The HttpGateway knows if it is configured 
     * to run with an API Manager or as a simple proxy. Therefore it can return the correct handler
     * to be registered with the vert.x framework. This handler is called to handle a response
     * from a fabric8 service.
     * 
     * @param httpClient - vert.x HttpClient used to call the fabric8 service
     * @param httpServerRequest - vert.x HttpServerRequest used to create a HttpServiceResponseHandler
     *  when no API Manager is used
     * @param apiManagementResponseHandler -  
     * @return
     * 
     * @See       HttpServiceResponseHandler
     * @See ApiManHttpServiceResponseHandler
     * 
     */
    public Handler<HttpClientResponse>  createServiceResponseHandler(HttpClient httpClient,
    		Object apiManagementResponseHandler);
	/**
	 * Creates an implementation of a Handler of type HttpServerRequest, and returns a reference.
	 * This handler is called by the vert.x framework, when when the gateway invoked by an external
	 * client application. 
	 * 
	
	 * @return a Handler of type HttpServerRequest
	 * 
	 * @see       HttpGatewayHandler
	 * @see ApiManHttpGatewayHandler
	 */
	public Handler<HttpServerRequest> createApiManagerHttpGatewayHandler();
		
	public void handleRestRequest(HttpServerRequest request);
}