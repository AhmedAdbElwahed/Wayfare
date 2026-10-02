package com.wayfare.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.wayfare.controller.DriverAdminController;
import com.wayfare.controller.DriverController;
import com.wayfare.domain.Driver;
import com.wayfare.service.DriverService;

@WebMvcTest({DriverController.class, DriverAdminController.class})
@Import(SecurityConfig.class)
class DriverRoleAuthorizationTest {

    private static final UUID ID = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean DriverService driverService;
    @MockitoBean com.wayfare.service.DocumentStorageService storageService;
    @MockitoBean JwtDecoder jwtDecoder; // real one is built from the JWKS URI; jwt() bypasses decoding

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(ID.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void driverCanReadOwnRecord() throws Exception {
        org.mockito.BDDMockito.given(driverService.get(ID)).willReturn(new Driver(ID, "d@example.com"));
        mvc.perform(get("/drivers/me").with(as("DRIVER"))).andExpect(status().isOk());
    }

    @Test
    void riderTokenIsForbiddenOnDriverEndpoints() throws Exception {
        mvc.perform(get("/drivers/me").with(as("RIDER"))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/drivers/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void driverCannotUseAdminEndpoints() throws Exception {
        mvc.perform(post("/drivers/admin/{id}/approve", ID).with(as("DRIVER")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
