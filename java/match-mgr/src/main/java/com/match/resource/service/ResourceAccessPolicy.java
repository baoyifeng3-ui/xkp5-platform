package com.match.resource.service;

import com.match.security.UserRole;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessPolicy {
    public boolean canList(ResourceSpaceType space, UserRole role, Integer actorId, Integer ownerId) {
        if (space == ResourceSpaceType.HOMEWORK) {
            return role == UserRole.USER && actorId != null && actorId.equals(ownerId);
        }
        if (space == ResourceSpaceType.COURSE) return isAdmin(role);
        return true;
    }

    public boolean canUpload(ResourceSpaceType space, UserRole role) {
        if (space == ResourceSpaceType.COURSE || space == ResourceSpaceType.PUBLIC) return isAdmin(role);
        return true;
    }

    public boolean canDelete(ResourceSpaceType space, UserRole role, Integer actorId,
                             Integer ownerOrUploaderId) {
        if (space == ResourceSpaceType.HOMEWORK) {
            return role == UserRole.USER && actorId != null && actorId.equals(ownerOrUploaderId);
        }
        if (space == ResourceSpaceType.EXCHANGE) {
            return isAdmin(role) || actorId != null && actorId.equals(ownerOrUploaderId);
        }
        return isAdmin(role);
    }

    public boolean isAdmin(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }
}
