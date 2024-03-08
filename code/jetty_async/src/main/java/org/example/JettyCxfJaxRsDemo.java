package org.example;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * JettyCxfJaxRsDemo
 *
 * @author g00471473
 * @since 2024-02-29
 */
public class JettyCxfJaxRsDemo {
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Configure CXF servlet
        ServletHolder servletHolder = new ServletHolder(new CXFNonSpringJaxrsServlet());
        servletHolder.setName("cxf");
        servletHolder.setForcedPath("/*");
        servletHolder.setAsyncSupported(true);
        context.addFilter(AccessLogFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        context.addServlet(servletHolder, "/*");

        // Register JAX-RS resource
        servletHolder.setInitParameter("javax.ws.rs.Application", "org.example.MyApp");

        server.start();
        server.join();
    }
}
