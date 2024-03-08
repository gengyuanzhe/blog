package org.example;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.eclipse.jetty.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * HelloWorldResource
 *
 * @author g00471473
 * @since 2024-02-29
 */

@Slf4j
public class HelloWorldResource {
    @Path("/hello")
    @PUT
    public void sayHello(@Context final HttpServletRequest request, @Suspended AsyncResponse response) {
        new Thread(()-> {
            log.warn("process sayHello start 1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.warn("process sayHello start 2");
            response.resume(Response.status(HttpStatus.CONFLICT_409).entity("hello").build());
            log.warn("process sayHello end");
        }).start();
    }
}
