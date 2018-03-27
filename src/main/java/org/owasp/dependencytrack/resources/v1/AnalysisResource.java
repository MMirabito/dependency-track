/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.owasp.dependencytrack.resources.v1;

import alpine.auth.PermissionRequired;
import alpine.model.ApiKey;
import alpine.model.UserPrincipal;
import alpine.resources.AlpineResource;
import alpine.validation.RegexSequence;
import alpine.validation.ValidationTask;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.owasp.dependencytrack.auth.Permissions;
import org.owasp.dependencytrack.model.Analysis;
import org.owasp.dependencytrack.model.Component;
import org.owasp.dependencytrack.model.Project;
import org.owasp.dependencytrack.model.Vulnerability;
import org.owasp.dependencytrack.persistence.QueryManager;
import org.owasp.dependencytrack.resources.v1.vo.AnalysisRequest;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * JAX-RS resources for processing analysis decisions.
 *
 * @author Steve Springett
 * @since 3.1.0
 */
@Path("/v1/analysis")
@Api(value = "analysis", authorizations = @Authorization(value = "X-Api-Key"))
public class AnalysisResource extends AlpineResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieves an analysis trail",
            response = Analysis.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The project, component, or vulnerability could not be found")
    })
    @PermissionRequired(Permissions.Constants.VULNERABILITY_ANALYSIS)
    public Response retrieveAnalysis(@ApiParam(value = "The UUID of the project", required = true)
                                     @QueryParam("project") String projectUuid,
                                     @ApiParam(value = "The UUID of the component", required = true)
                                     @QueryParam("component") String componentUuid,
                                     @ApiParam(value = "The UUID of the vulnerability", required = true)
                                     @QueryParam("vulnerability") String vulnerabilityUuid) {
        failOnValidationError(
                new ValidationTask(RegexSequence.Pattern.UUID, projectUuid, "Project is not a valid UUID"),
                new ValidationTask(RegexSequence.Pattern.UUID, componentUuid, "Component is not a valid UUID"),
                new ValidationTask(RegexSequence.Pattern.UUID, vulnerabilityUuid, "Vulnerability is not a valid UUID")
        );
        try (QueryManager qm = new QueryManager()) {
            final Project project = qm.getObjectByUuid(Project.class, projectUuid);
            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
            final Component component = qm.getObjectByUuid(Component.class, componentUuid);
            if (component == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The component could not be found.").build();
            }
            final Vulnerability vulnerability = qm.getObjectByUuid(Vulnerability.class, vulnerabilityUuid);
            if (vulnerability == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The vulnerability could not be found.").build();
            }

            Analysis analysis = qm.getAnalysis(project, component, vulnerability);
            return Response.ok(analysis).build();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Records an analysis decision",
            response = Analysis.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The project, component, or vulnerability could not be found")
    })
    @PermissionRequired(Permissions.Constants.VULNERABILITY_ANALYSIS)
    public Response updateAnalysis(AnalysisRequest request) {
        final Validator validator = getValidator();
        failOnValidationError(
                validator.validateProperty(request, "project"),
                validator.validateProperty(request, "component"),
                validator.validateProperty(request, "vulnerability"),
                validator.validateProperty(request, "analysisState"),
                validator.validateProperty(request, "comment")
        );
        try (QueryManager qm = new QueryManager()) {
            final Project project = qm.getObjectByUuid(Project.class, request.getProject());
            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
            final Component component = qm.getObjectByUuid(Component.class, request.getComponent());
            if (component == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The component could not be found.").build();
            }
            final Vulnerability vulnerability = qm.getObjectByUuid(Vulnerability.class, request.getVulnerability());
            if (vulnerability == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("The vulnerability could not be found.").build();
            }

            Analysis existing = qm.getAnalysis(project, component, vulnerability);
            if (existing != null) {
                if (existing.getAnalysisState() != request.getAnalysisState()) {
                    // The analysis state has changed. Add an additional comment to the trail.
                    final String message;
                    if (getPrincipal() instanceof ApiKey) {
                        message = "An API key changed analysis from " + existing.getAnalysisState() + " to " + request.getAnalysisState();
                    } else {
                        message = "User " + ((UserPrincipal)getPrincipal()).getUsername() + "changed analysis from " + existing.getAnalysisState() + " to " + request.getAnalysisState();
                    }
                    qm.makeAnalysisComment(existing, message);
                }
            }

            Analysis analysis = qm.makeAnalysis(project, component, vulnerability, request.getAnalysisState(), request.getComment());
            return Response.ok(analysis).build();
        }
    }

}
