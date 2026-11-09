# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a
Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `DELETE /user/{id}`, `DELETE /admin/{id}` and `DELETE /user_group/{id}` (admin only). Deleted users and admins get an
  "account deleted" e-mail. Admins cannot delete themselves or the last admin (`409`).
- Permission management API (admin only): endpoints, endpoint groups and allow/deny rules of all four kinds, so the
  permission check can be configured without editing the database.
- `GET /user/{id}` and `GET /admin/{id}`, `DELETE /desktop/{id}` and `DELETE /desktop_group/{id}` (admin only).

### Changed

- The permission check always allows admins, so switching it on in design-daas cannot lock them out.
- V5 migration: at most one permission rule per kind, subject and target. Existing duplicates are removed, keeping the
  rule the check already used (deny before allow, otherwise the oldest).

## [2.0.0]

Spring Boot backend for DESIGN DaaS that replaces the earlier PHP backend: authentication with JWT access and refresh
tokens, user, admin and user group management, desktops and desktop groups, and the permission check used by the DaaS
platform. Java 21, Spring Boot 4.1 (Spring Framework 7, Spring Security 7), MariaDB with Flyway, 314 tests, 99 % line coverage.