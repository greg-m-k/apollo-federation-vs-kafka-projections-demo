package com.example.security.resource;

import com.example.security.dto.BadgeInput;
import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;
import com.example.security.service.BadgeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/badges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BadgeResource {

    @Inject
    BadgeService badgeService;

    @GET
    public List<BadgeHolder> getAllBadgeHolders() {
        return badgeService.getAllBadgeHolders();
    }

    @GET
    @Path("/{id}")
    public Response getBadgeHolder(@PathParam("id") String id) {
        BadgeHolder badge = badgeService.getBadgeHolder(id);
        if (badge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(badge).build();
    }

    @GET
    @Path("/by-person/{personId}")
    public Response getBadgeHolderByPersonId(@PathParam("personId") String personId) {
        BadgeHolder badge = badgeService.getBadgeHolderByPersonId(personId);
        if (badge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(badge).build();
    }

    @POST
    public Response provisionBadge(BadgeInput input) {
        BadgeHolder badge = badgeService.provisionBadge(input);
        if (badge == null) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Person already has a badge").build();
        }
        return Response.status(Response.Status.CREATED).entity(badge).build();
    }

    @PUT
    @Path("/{id}/access-level")
    public Response changeAccessLevel(
            @PathParam("id") String id,
            @QueryParam("level") AccessLevel newLevel) {

        BadgeHolder badge = badgeService.changeAccessLevel(id, newLevel);
        if (badge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(badge).build();
    }

    @PUT
    @Path("/{id}/clearance")
    public Response changeClearance(
            @PathParam("id") String id,
            @QueryParam("level") Clearance newLevel) {

        BadgeHolder badge = badgeService.changeClearance(id, newLevel);
        if (badge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(badge).build();
    }

    @POST
    @Path("/{id}/revoke")
    public Response revokeBadge(@PathParam("id") String id) {
        BadgeHolder badge = badgeService.revokeBadge(id);
        if (badge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(badge).build();
    }
}
