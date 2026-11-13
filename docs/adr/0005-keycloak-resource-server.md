# 0005: Keycloak for identity, resource server for the API

**Status:** accepted

## Context

Five roles need sign-in, and a school would bring its own identity provider. Storing passwords and building login screens adds risk and no learning value here.

## Decision

Keycloak holds users and the five realm roles. The web app signs in with the authorization code flow and PKCE. tutor-api is a stateless OAuth2 resource server: it validates the JWT, maps `realm_access.roles` to `ROLE_*`, and creates an `app_user` row on the first request so domain tables can reference users by a local id. Relationships (guardian, tutor) and all data ownership rules live in tutor-api, not in Keycloak.

## Consequences

- No credentials in the application; any OpenID Connect provider could replace Keycloak.
- A user exists in `app_user` only after signing in once. The demo seed signs every demo user in first.
- Role changes take effect when the next token is issued.
