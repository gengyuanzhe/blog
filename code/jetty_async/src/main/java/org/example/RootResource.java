package org.example;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 * RootResource
 *
 * @author g00471473
 * @since 2024-03-01
 */
@Path("/")
@Produces({"application/xml", "text/xml", "text/html", "application/json"})
public class RootResource {
    @Path("/")
    public HelloWorldResource findObjectResource(@Context UriInfo info, @Context HttpServletRequest request){
        return new HelloWorldResource();
    }
}
