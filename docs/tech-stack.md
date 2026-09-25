# SERMS 统一技术栈（提案，待团队确认）

依据：`feature/wang-yuanmeng-sprint1-notifications`（01b5077）与 `feat/zhoufanhao-sprint1-data-foundation`（228d978）两个分支的现状，以及 main 上已有的 CI / 部署流程。

## 现状：两条分支的分歧

| 方面 | 王远蒙（通知 UC-05） | 周凡浩（数据层 / 预约） |
| --- | --- | --- |
| 语言 / 构建 | Java 17 + Maven（带 wrapper） | Java 17 + Maven |
| 框架 | Spring Boot 3.5（Web、Security、Data JPA、Thymeleaf） | `database/` 纯 JDBC；`integration/` 用 Spring Boot 3.5 |
| 数据库 | **MySQL 8.4**（本地和测试用 H2） | **PostgreSQL 17**（测试也用真库） |
| 表结构迁移 | Flyway V1–V3（独立序列） | 手写 SQL V001–V003（`serms` schema），另用 Flyway 跑 |
| 包名 | `sg.edu.nus.serms` | `edu.nus.serms`（database）/ `sg.edu.nus.serms`（integration） |
| CI | `notification-tests.yml`（MySQL service） | `database.yml`、`notification-contract.yml`（Postgres service） |
| 部署 | 未接入，线上仍是 `web/` 静态页 | 未接入 |

框架层面已经一致（Java 17 + Spring Boot 3.5 + Maven），真正要调和的是**数据库**、**迁移序列**、**包名**和**部署**。

## 决定

1. **一个应用**：保留 `app/` 作为唯一的 Spring Boot 模块化单体（王远蒙的骨架）。按功能分包：`reservation`、`loan`、`maintenance`、`notification`、`identity`、`shared`，每个包内再分 `controller / service / domain / repository`。
2. **数据库统一用 PostgreSQL 17**。理由：
   - 预约冲突是 Sprint 1 核心需求。周凡浩用的 `EXCLUDE USING gist` + `tstzrange` 能在数据库层保证同一设备的时间段不重叠，并发下也可靠。MySQL 没有排他约束，只能靠应用层加锁，重写成本高、正确性更难保证。
   - 周凡浩的表结构（角色、审批、借还、维修、审计、通知）已经覆盖整个 ER 图，有 27 + 12 个真库测试。王远蒙这边只有 3 张通知表，迁到 Postgres 的工作量小得多，而且周凡浩的 V003 已经做好了对应表和适配器。
   - 去掉 H2：H2 测不出真实数据库的锁和约束行为。测试统一跑真 Postgres（CI 用 service container，本地用 Docker 或 Testcontainers）。
   - ⚠️ 如果课程或项目计划书明确规定必须用 MySQL，这一条要重新讨论。
3. **一条迁移序列**：所有 SQL 迁移都放进 `app/src/main/resources/db/migration`，由 Flyway 统一执行（`V001`、`V002`、`V003`…，三位编号）。王远蒙的 V1–V3（MySQL）不再使用，由周凡浩的 V003 取代。以后谁要改表，只能新增迁移，不改旧迁移。
4. **包名统一为 `sg.edu.nus.serms`**。`edu.nus.serms` 下的代码迁过去。
5. **数据访问**：两种方式都保留，规则是：
   - 普通增删改查用 Spring Data JPA。启动时 `ddl-auto=validate`，只校验、不建表。
   - 需要锁或复杂 SQL 的地方（预约冲突、借还的锁顺序）用 `JdbcTemplate` 手写 SQL。周凡浩的 `ReservationRepository` 改成注入 Spring 的 `DataSource` 即可。
   - `integration/notifications` 这个适配模块合并后就不需要了。其中的 `PostgresLoanReminderGuard` 和 `PostgresNotificationRequestStore` 直接移进 `app` 的 `notification` 包，`upstream-app-dependency.patch` 删除。
6. **页面**：Thymeleaf 服务端渲染（王远蒙已有公共布局和样式），暂不引入前端框架。
7. **CI**：合成一个 `build-test` job：Postgres 17 service + `./mvnw -f app/pom.xml verify`。三个分支各自的 workflow 合并进 `ci.yml`，并把 `build-test` 加进 main 的 required checks。
8. **部署**：根目录 `Dockerfile` 改成多阶段构建（Maven 打包，再用 JRE 17 运行）。`compose.yaml` 增加 `db`（postgres:17，带数据卷）和 `app` 两个服务，app 继续映射到 `127.0.0.1:8789`。健康检查路径和 `.env` 里的 `HEALTH_PATH` 保持一致（加 Spring Actuator 后是 `/actuator/health`）。机密（数据库密码）只放服务器 `/opt/agfp/.env`。

## 落地顺序

1. 团队确认本文件（尤其是第 2 条：PostgreSQL）。
2. 先合并王远蒙的分支（应用骨架），同时把数据库换成 Postgres，删掉 H2 / MySQL 配置和 MySQL 迁移。
3. 再合并周凡浩的分支：把迁移移进 `app`，数据访问代码并进对应的功能包，删掉 `integration/` 适配模块和补丁。
4. 改 `Dockerfile` / `compose.yaml` / CI，让 main 能部署真正的 Java 应用。
5. 后续约定：登录身份（principal）统一用 `app_user` 的稳定标识，不再用临时的 `user` 账号。

## 其他待定事项

- 仓库名是 AGFP（Assignment Grading and Feedback Platform），而项目内容是 SERMS（共享设备预约系统），需要和老师确认题目。
- 周凡浩的文档里提到另一位成员（Shi Wenqi）负责应用入口，这位成员还不是仓库协作者，需要邀请。
