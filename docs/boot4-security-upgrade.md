# Spring Boot 4 Security Upgrade

Issue #10 / PR #13. The user explicitly authorized Boot 4, updated technical conventions and full regression on 2026-10-13 (Asia/Singapore); see [D-006](collaboration/decisions.md#d-006-spring-boot-4-security-migration). This is Liu Tongyao's agent-assisted implementation, without changing team ownership.

## Trigger and Selected Versions

The first built-image scan on `795d68e8ab9604b0d11341527b376392dc93606e` failed with 10 findings: five Jackson HIGH, three Tomcat CRITICAL and two Spring CRITICAL according to Trivy. The source scan had passed. Keep this original failure visible; no ignore entries or lowered severity thresholds are used.

The vendor rates the two Spring advisories differently and describes feature-specific conditions: [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) concerns XsltView with implicit view rendering, and [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) concerns SSE view fragments. SERMS uses JSON APIs and does not intentionally enable those features. The corresponding OSS fixed Framework is 7.0.9; 6.2.20 is enterprise-only. The user chose an upgrade, not a suppression or paid artifact.

Maven Central's stable metadata selected Boot **4.0.8**, which manages Framework **7.0.9**; Springdoc **3.0.3** is the compatible 3.0 line. Override Jackson 3 to **3.1.7**, Jackson 2 compatibility dependencies to **2.21.7**, and Tomcat to **11.0.27**. Boot's original 3.1.5 / 2.21.5 / 11.0.24 defaults were still below fixed versions in the image findings. Vendor references: [Jackson advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89), [Tomcat security](https://tomcat.apache.org/security-11.html).

## Compatibility Work

Follow the [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide): use modular webmvc/flyway and matching webmvc/security test starters, relocated MVC test imports, Jackson 3 imports and strict custom integer deserializers, and Testcontainers 2 PostgreSQL artifact/imports. Enable web security explicitly so controller slices receive the real security configuration. Existing assertions, endpoint methods, request/response DTOs and authorization policies remain intact.

`spring.jackson.use-jackson2-defaults=true` retains established JSON defaults; API type generation and real flows verify compatibility. Java 17 remains the release target. Existing `database/` and pinned notification-adapter contract dependencies remain transitional, with their own JUnit 5 benches; the Boot 4 application uses JUnit Jupiter 6. Immutable Flyway V001–V004 and SQL history remain unchanged.

The candidate integrates the verified PR #11/#14 dependency chain. Merge order is #11 → #14 → #13, updating targets and rerunning applicable checks as dependencies enter main. No main merge or cloud action occurs in this session. Local tests, hosted builds/scans, team acceptance and eventual production handover remain distinct.
