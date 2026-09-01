package com.match.resource.service;

import com.match.security.UserRole;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceAccessPolicyTest {
    private final ResourceAccessPolicy policy = new ResourceAccessPolicy();

    @Test
    public void courseLibraryIsAdminOnly() {
        assertTrue(policy.canList(ResourceSpaceType.COURSE, UserRole.ADMIN, 10, null));
        assertTrue(policy.canUpload(ResourceSpaceType.COURSE, UserRole.SUPER_ADMIN));
        assertFalse(policy.canList(ResourceSpaceType.COURSE, UserRole.USER, 20, 20));
    }

    @Test
    public void publicLibraryIsReadableByUsersButWritableOnlyByAdmins() {
        assertTrue(policy.canList(ResourceSpaceType.PUBLIC, UserRole.USER, 20, null));
        assertFalse(policy.canUpload(ResourceSpaceType.PUBLIC, UserRole.USER));
        assertTrue(policy.canUpload(ResourceSpaceType.PUBLIC, UserRole.ADMIN));
    }

    @Test
    public void exchangeDeleteUsesUploaderOwnership() {
        assertTrue(policy.canDelete(ResourceSpaceType.EXCHANGE, UserRole.ADMIN, 10, 20));
        assertTrue(policy.canDelete(ResourceSpaceType.EXCHANGE, UserRole.USER, 20, 20));
        assertFalse(policy.canDelete(ResourceSpaceType.EXCHANGE, UserRole.USER, 20, 21));
    }

    @Test
    public void homeworkIsVisibleOnlyToItsOwnerAndNeverToAdmins() {
        assertTrue(policy.canList(ResourceSpaceType.HOMEWORK, UserRole.USER, 20, 20));
        assertFalse(policy.canList(ResourceSpaceType.HOMEWORK, UserRole.USER, 20, 21));
        assertFalse(policy.canList(ResourceSpaceType.HOMEWORK, UserRole.ADMIN, 10, 20));
        assertFalse(policy.canList(ResourceSpaceType.HOMEWORK, UserRole.SUPER_ADMIN, 1, 20));
        assertTrue(policy.canDelete(ResourceSpaceType.HOMEWORK, UserRole.USER, 20, 20));
    }
}
