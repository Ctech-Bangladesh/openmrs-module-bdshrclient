package org.openmrs.module.shrclient.advice;


import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * This class is listening to /ws/rest/v1/bahmnicore/bahmniencounter
 * i.e., bahmni endpoint for save/update encounter.
 * It clears the encounter event state after the encounter is finished processing.
 * This is done so that multiple events are not created.
 */
public class ShrEncounterAdviceFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //initalize filterConfig.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        EncounterAdviceState encounterAdviceState = new EncounterAdviceState();
        chain.doFilter(request, response);

        encounterAdviceState.reset();
    }

    @Override
    public void destroy() {
        //destroy filter.
    }
}
