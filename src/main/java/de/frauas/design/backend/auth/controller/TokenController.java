package de.frauas.design.backend.auth.controller;

import de.frauas.design.backend.auth.dto.GrantRequest;
import de.frauas.design.backend.auth.dto.TokenResponseDto;
import de.frauas.design.backend.auth.exception.TokenEndpoint;
import de.frauas.design.backend.auth.service.JwtUserResolver;
import de.frauas.design.backend.auth.service.TokenRevocationService;
import de.frauas.design.backend.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token issuance/revocation endpoints at {@code /oauth2/user/token} (POST) and
 * {@code /oauth2/user/logout} (POST). Handles the resource-owner password grant,
 * the refresh-token grant, and revoking a caller's own tokens on logout.
 *
 * <p>This controller is intentionally thin: it only parses the request and shapes
 * the response. All business logic — grant-type dispatch, credential verification,
 * account lockout, scope validation, and token issuance/rotation/revocation — lives in
 * {@link TokenService} so that it can be unit-tested without a Spring context.
 * Rejected grants are reported as {@code TokenGrantException} subclasses and mapped
 * to HTTP error responses by {@code TokenExceptionHandler}. Unlike {@code /token},
 * {@code /logout} requires an authenticated caller (an already-valid access token).</p>
 */
@RestController
@RequestMapping("/oauth2/user")
@RequiredArgsConstructor
@Slf4j
@TokenEndpoint
public class TokenController {

    private final TokenService tokenService;
    private final TokenRevocationService tokenRevocationService;
    private final JwtUserResolver jwtUserResolver;

    /**
     * Issues (or refreshes) an OAuth2 access/refresh token pair.
     *
     * @param grantType    {@code "password"} or {@code "refresh_token"}; any other
     *                     value results in an {@code unsupported_grant_type} error
     * @param username     resource-owner email; required for the password grant
     * @param password     resource-owner password; required for the password grant
     * @param refreshToken the refresh token to redeem; required for the refresh-token grant
     * @param scope        space-separated scopes requested by the client (password grant only);
     *                     defaults to the user's primary role scope when omitted
     * @return the issued token pair, serialized as the standard OAuth2 token response
     */
    @PostMapping(
            value = "/token",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<TokenResponseDto> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "scope", required = false, defaultValue = "") String scope) {

        log.info("POST /oauth2/user/token — grant_type={}", grantType);

        GrantRequest request = GrantRequest.of(grantType, username, password, refreshToken, scope);

        TokenResponseDto result = tokenService.grantToken(request);

        log.info("POST /oauth2/user/token — grant_type={} succeeded", grantType);

        return ResponseEntity.ok(result);
    }

    /**
     * Logs the caller out by revoking the access token used for this request and,
     * optionally, the given refresh token. Idempotent; a refresh token belonging to
     * another user is ignored.
     *
     * @param jwt          the caller's authenticated JWT, injected by Spring Security
     * @param refreshToken refresh token to revoke as well (optional)
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        Integer userId = jwtUserResolver.resolveFromAuthenticatedJwt(jwt);
        log.info("POST /oauth2/user/logout — userId={}", userId);
        tokenRevocationService.logout(userId, jwt.getId(), refreshToken);
        return ResponseEntity.noContent().build();
    }
}
