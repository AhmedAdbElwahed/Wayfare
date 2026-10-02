package com.wayfare.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wayfare.controller.RiderController;
import com.wayfare.dto.RiderResponse;
import com.wayfare.service.ProfileService;
import com.wayfare.service.TripHistoryService;

@WebMvcTest(RiderController.class)
@Import(SecurityConfig.class)
class RiderRoleAuthorizationTest {

    private static final UUID ID = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileService profileService;

    // The real decoder is built from the JWKS URI; the jwt() post-processor
    // bypasses decoding, so the filter chain just needs a bean to exist.
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    TripHistoryService tripHistoryService;

    @MockitoBean
    com.wayfare.service.PhotoService photoService;

    @Test
    void riderTokenIsAllowed() throws Exception {
        given(profileService.getRider(ID)).willReturn(
                new RiderResponse(ID, "Ann", "+100", null, "en", null, Instant.now()));

        mvc.perform(get("/riders/me").with(jwt().jwt(j -> j.subject(ID.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_RIDER"))))
                .andExpect(status().isOk());
    }

    @Test
    void driverTokenIsForbidden() throws Exception {
        mvc.perform(get("/riders/me").with(jwt().jwt(j -> j.subject(ID.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/riders/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void roleClaimIsMappedToAuthority() {
        var converter = new SecurityConfig().jwtAuthenticationConverter();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                .header("alg", "none").subject(ID.toString()).claim("role", "DRIVER").build();
        org.assertj.core.api.Assertions.assertThat(converter.convert(jwt).getAuthorities())
                .extracting(Object::toString).contains("ROLE_DRIVER").doesNotContain("ROLE_RIDER");
    }
}
