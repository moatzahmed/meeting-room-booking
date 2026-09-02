# Contributing

## Repository model

Application code uses one monorepo. Room Service, Booking Service, API Gateway,
Discovery Server, Config Server, local infrastructure, tests, and documentation
change through pull requests in this repository.

Runtime configuration remains in the separate `microservice-project-configs`
repository. Configuration changes use their own pull requests and must explain
which application-code versions they are compatible with.

## Workflow

1. Create a short-lived branch from `main`, using a name such as
   `feature/jwt-security` or `fix/booking-overlap`.
2. Keep commits focused and never commit secrets.
3. Run `mvn clean verify` before opening a pull request.
4. Open a pull request using the repository template.
5. Merge only after the required `Maven verify` check passes and the pull request
   has been reviewed.
6. Prefer squash merging so `main` retains one clear commit per change.

Do not push application changes directly to `main`. Avoid long-lived service
branches; service boundaries are directories and deployable Maven modules, not
branches.

## Coverage policy

JaCoCo enforces at least 70% line coverage independently in each Maven module.
The threshold is intentionally modest at first and should rise as security,
messaging, and observability behavior receives tests. Generated reports are in
`<module>/target/site/jacoco/index.html` and are uploaded by CI.

Coverage is a regression guard, not a substitute for meaningful assertions.
Do not add low-value tests solely to increase the percentage.

## GitHub protection for `main`

Configure a branch ruleset for the default branch with:

- pull requests required before merging;
- at least one approving review;
- stale approvals dismissed when new commits are pushed;
- conversation resolution required;
- the `Maven verify` status check required and branches required to be current;
- force pushes and branch deletion blocked;
- administrators included, with bypass limited to emergency maintainers;
- squash merge enabled and automatic branch deletion enabled.

Apply equivalent pull-request and required-check protection to the configuration
repository. That repository does not need the Java build; use a lightweight YAML
validation workflow as its required check.
