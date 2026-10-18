# Plan: Web DAW — DevOps portfolio project

## Context
This is a study/portfolio project for a DevOps course. The app is a browser-based Digital Audio Workstation (DAW): record, upload, arrange and mix audio tracks. The course requires at least 7 of its 10 topics, and Kubernetes is mandatory. The repo `GeorgiDimow/daw` is empty apart from the README.

Constraints from the user's answers:
- Backend in Java/Spring.
- AWS free tier only, so k3s on EC2 instead of EKS.
- CI on GitHub Actions, CD through Argo CD (GitOps).

Product scope stays small. Most of the effort should go into the DevOps side, because that is what gets graded.

## Course topic coverage (all 10)
| # | Topic | How it is covered |
|---|---|---|
| 3 | Version control | Monorepo, trunk-based flow, branch protection, Conventional Commits, PR templates, CODEOWNERS, semantic version tags |
| 4 | Microservices + Docker | 4 Spring Boot services and a React frontend, multi-stage Dockerfiles, docker-compose for local development |
| 5 | Kubernetes | k3s on EC2 (k3d locally), Helm charts, Traefik Ingress, probes, resource limits, HPA, NetworkPolicies |
| 6 | Pipelines | Reusable GitHub Actions workflows with path filters per service |
| 7 | CI | Build, unit tests, Testcontainers integration tests, lint, coverage gate, image build |
| 8 | CD | Argo CD app-of-apps. `dev` syncs automatically; `prod` is promoted by PR |
| 9 | DevSecOps | gitleaks, Semgrep/CodeQL, dependency scan, Trivy image and IaC scan, SBOM (Syft), cosign signing, OIDC to AWS, External Secrets |
| 10 | AWS | EC2, VPC, S3, RDS Postgres, SQS, IAM, SSM Parameter Store |
| 11 | Terraform | Modular IaC, remote state in S3, plan on PR and apply on main with approval |
| 12 | DB versioning | Flyway per service, migrations tested in CI, run as an Argo CD PreSync Job |

## Core product features (MVP)
1. Register and log in (JWT).
2. Projects: create, list and delete them; each has a BPM and time signature.
3. Tracks: add, rename, delete; volume, pan, mute and solo per track; a master volume.
4. Clips: upload WAV/MP3 or record from the microphone, then drag clips on a timeline.
5. Transport: play, stop, loop region, metronome.
6. Waveforms rendered from peaks the server generates.
7. Export the mixdown to WAV in the browser (OfflineAudioContext).
8. Autosave project state.

**Stretch goals:** per-track effects (EQ, reverb, delay), MIDI and a piano roll, a public share link, server-side mixdown, collaboration.

## Tech stack
**Frontend:** React, TypeScript and Vite; Web Audio API, with Tone.js for transport and scheduling; canvas waveform rendering; served by nginx in a container.

**Backend:** Java 21 and Spring Boot 3, Gradle multi-project build.
- `auth-service`: users, register/login, issues JWTs (RS256). Exposes JWKS.
- `project-service`: projects, tracks, clips, mixer state (REST). Validates JWTs as a resource server.
- `media-service`: issues S3 presigned upload/download URLs, stores audio metadata, and publishes an "uploaded" event to SQS.
- `processing-service`: SQS worker that uses ffmpeg to normalize/transcode audio, generates waveform peaks JSON to S3, and stores the duration.
- Shared: Spring Security resource server, Actuator health/readiness, OpenAPI (springdoc).

**Data:**
- One RDS Postgres instance (free tier `db.t4g.micro`) with one schema per service; services don't read each other's schemas.
- S3 for audio and peaks.
- SQS for events.
- Locally, Postgres, LocalStack (S3 and SQS) and docker-compose replace these.

**Kubernetes and CD:**
- k3s on a single EC2 node. Use an 8 GB free-plan-eligible type such as `m7i-flex.large`, and check eligibility on the account. `t3.micro` is too small for several JVMs plus Argo CD.
- Helm: one chart per service plus an umbrella chart.
- Argo CD (app-of-apps) and External Secrets Operator (backed by SSM Parameter Store).
- Traefik Ingress (built into k3s) and cert-manager with Let's Encrypt.
- JVMs tuned with `-XX:MaxRAMPercentage`, a 512Mi limit and one replica.

**Registry:** GHCR, which is free and avoids ECR storage limits.

**IaC:** Terraform ≥1.10 with an S3 backend (`use_lockfile = true`).
- Modules: `network`, `k3s-node` (EC2, cloud-init installs k3s, instance profile), `rds`, `storage` (S3 and SQS), `iam` (GitHub OIDC role), `params` (SSM).
- Environments: `infra/envs/dev`. `terraform destroy` when it isn't needed.

## Repository layout
```
daw/
  frontend/
  services/{auth,project,media,processing}-service/
  libs/common-security/            # shared JWT/resource-server config
  deploy/helm/{service charts, daw umbrella}/
  deploy/gitops/{apps, envs/dev, envs/prod}/   # Argo CD Applications + values (image tags)
  infra/terraform/{modules/*, envs/dev, bootstrap/}  # bootstrap = state bucket
  .github/workflows/  {ci-service.yml (reusable), ci-frontend.yml, security.yml, terraform.yml, helm.yml, release.yml}
  docker-compose.yml
  docs/ (architecture diagram, ADRs, runbook)
```

## Pipelines
- **Service CI** (on PRs and main, path-filtered):
  1. Gradle build and unit tests.
  2. Testcontainers integration tests, including Flyway migrations against real Postgres.
  3. JaCoCo coverage gate.
  4. Docker build.
  5. Trivy image scan (fail on HIGH/CRITICAL).
  6. Syft SBOM.
  7. On main: push to GHCR and sign keyless with cosign.
  8. On main: a bot PR or commit bumps the image tag in `deploy/gitops/envs/dev`.
- **Security** (PRs plus weekly): gitleaks, Semgrep or CodeQL, dependency review, Trivy filesystem scan.
- **Helm:** `helm lint` and kubeconform.
- **Terraform:**
  - On PRs: fmt, validate, tflint, Trivy/Checkov IaC scan, and `plan` posted as a PR comment.
  - On main: `apply` behind a GitHub Environment approval. AWS access goes through OIDC, with no static keys.
- **Release:** a version tag opens the promotion PR that copies dev image tags into `envs/prod`. Merging it lets Argo sync prod.
- **CD:** Argo CD watches `deploy/gitops`. `dev` auto-syncs with self-heal. `prod` has manual sync or sync on merge. Flyway runs as a PreSync hook Job.

## Build order (milestones)
1. **Repo hygiene:** branch protection, PR template, `.editorconfig`, Conventional Commits, and docker-compose with Postgres and LocalStack.
2. **auth-service and project-service:** Flyway migrations, tests, Dockerfiles, CI workflow.
3. **Frontend MVP:** login, project list, timeline, transport, mixer, playback of uploaded clips.
4. **media-service and processing-service:** presigned uploads, SQS, ffmpeg, peaks. Then recording and export in the frontend.
5. **Helm charts and a local k3d cluster with Argo CD:** GitOps working locally first.
6. **Terraform for AWS:** VPC, EC2 k3s, RDS, S3, SQS, IAM OIDC, SSM. Then bootstrap Argo CD on the node.
7. **DevSecOps hardening:** scans as gates, cosign, NetworkPolicies, External Secrets, non-root containers.
8. **CD promotion dev→prod, then docs:** architecture diagram, ADRs, runbook, demo script.

## Risks and notes
- **Memory:** two namespaces each running 4 JVMs, plus Argo CD, fits in 8 GB only with tight limits. If it doesn't, keep `prod` on AWS and treat local k3d as `dev`.
- **AWS free plan:** accounts created after mid-2025 get credits rather than the old 12-month free tier. Confirm the eligible instance types and set a billing alarm (Terraform `aws_budgets_budget`).
- **Pods on k3s:** they reach AWS through the EC2 instance profile (set the IMDS hop limit to 2), because IRSA isn't available.

## Verification
- **Local:** `docker compose up` runs the full app at localhost. Register, create a project, upload a WAV, see the waveform, play, export.
- **CI:** open a PR. Tests and scans must pass, and an intentionally vulnerable dependency or a planted secret must fail the build.
- **Local k3d:** Argo CD shows all apps Healthy/Synced. Bump an image tag and the cluster updates automatically.
- **AWS:** `terraform apply`, then the app is reachable over HTTPS at the EC2 domain and uploads land in S3. Then `terraform destroy` cleans up.
- **DB versioning:** add a V2 migration. CI tests it, and the Argo PreSync job applies it before the new pods start.
