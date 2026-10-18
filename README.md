# daw

A browser-based Digital Audio Workstation, built as a DevOps portfolio project: Spring Boot microservices,
Docker, Kubernetes (k3s on AWS), GitHub Actions CI, Argo CD, Terraform and Flyway.

See [docs/PLAN.md](docs/PLAN.md) for the architecture, tech stack and milestones.

## Services

| Service | Port | Description |
|---|---|---|
| [auth-service](services/auth-service) | 8081 | Register/login, issues RS256 JWTs, publishes its JWKS |

## Run locally

Requires Java 21 and Docker.

```bash
cp .env.example .env          # then set POSTGRES_PASSWORD
docker compose up --build
```

Try it:

```bash
curl -i -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"s3cret-pass","displayName":"Ada"}'

TOKEN=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"s3cret-pass"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s localhost:8081/api/auth/me -H "Authorization: Bearer $TOKEN"
curl -s localhost:8081/.well-known/jwks.json
```

API docs: http://localhost:8081/swagger-ui.html

## Build and test

```bash
./gradlew check     # unit + Testcontainers integration tests (Docker must be running) + coverage gate
```

## CI/CD

- `ci-auth-service`: tests, Docker build, Trivy image scan, SBOM. On `main` it also pushes to GHCR and signs the image with cosign.
- `security`: gitleaks, dependency review, Trivy filesystem/config scan (PRs, `main`, weekly).
- Dependabot keeps Gradle, GitHub Actions and Docker base images up to date.

Contributions go through pull requests with [Conventional Commit](https://www.conventionalcommits.org) titles.
