# DO10 — Basic Kubernetes: Behind the Scenes

> An engineering journal of porting
> a seven-service Spring Boot booking app off Docker Swarm and onto a
> Kubernetes cluster, by hand, one manifest at a time.

---

## The setup

The application was already familiar: seven Spring Boot microservices
(`gateway`, `session`, `hotel`, `booking`, `payment`, `loyalty`, `report`)
backed by PostgreSQL and RabbitMQ. Up to this point it had lived inside
Docker Swarm — first as a working stack in the previous project, then
wrapped in Prometheus / Loki / Grafana / Alertmanager in DO9.

The new project moved the same workload onto **Kubernetes** running
locally in **minikube**. No Helm, no Kustomize, no operators — just
hand-rolled YAML applied with `kubectl`. The point wasn't to use the
fanciest tool; it was to internalize what Kubernetes is actually
managing under the surface, so that next time I reach for Helm I'll
know exactly which abstractions it's hiding.

The full task spec lives in [`./Task.md`](./Task.md). The implementation
write-up with `kubectl get/describe` output and every screenshot is in
[`./Report.md`](./Report.md). This document is the **story** — what I
planned, what surprised me, and how I got unstuck.

---

## The plan

Two halves:

1. **Part 1 — the warm-up.** Apply a pre-made manifest of four small
   demo services, open `minikube dashboard`, expose one of them through
   `minikube service`, and confirm I could read the cluster end-to-end.
2. **Part 2 — the real port.** Take my own seven-service app and
   express it as Kubernetes resources: a ConfigMap for the shared
   config, a Secret for the credentials and JWT material, and one
   Deployment + Service per workload. Then prove it works the same way
   it used to under Swarm.

The architectural call I made up front was to **numeric-prefix the
manifest filenames**: `00-configmap.yaml` → `11-gateway-service.yaml`.
This way a single `kubectl apply -f 01/k8s-manifests/` replays in
dependency order — config and secrets first, infra next (Postgres,
RabbitMQ), then the application tier. The ordering isn't strictly
necessary because Kubernetes will keep retrying until everything
settles, but it makes the apply log readable and dramatically shortens
the time-to-converge on a cold cluster.

---

## Part 1 — The warm-up

Quick, by design. Bring up minikube with `--memory=4096`, apply
[`example/microservices.yml`](./example/microservices.yml), and confirm
four Deployments + four Services are happy:

```
deployment.apps/apache created
deployment.apps/catalog created
deployment.apps/customer created
deployment.apps/order created
service/apache created
service/catalog created
service/customer created
service/order created
```

The interesting part of Part 1 was getting comfortable with the
inspection surface:

- `kubectl get all` for the bird's-eye view (`img/001.png`).
- `minikube dashboard` for the same view in a browser, with live
  refresh and pod logs one click away (`img/002.png`).
- `minikube service apache` to materialize a tunnel and verify the
  Apache test page in the browser (`img/003.png`).

That was enough to know I could trust the tooling before I started
writing my own manifests.

---

## Part 2 — The real port

This is where the work actually happened. I'll walk through it in
the same order I built it.

### Externalizing the secrets

The Spring services had been carrying their JWT keypair and a pair of
service UUIDs **inline in `application.properties`**, which was fine
on Swarm but uncomfortable on Kubernetes — `ConfigMap` is for
non-sensitive config and `Secret` is for everything that shouldn't show
up in plain text. So before I wrote a line of YAML, I went through
each service's `application.properties` and replaced the hardcoded
values with environment-variable references:

```properties
privateKey=${JWT_PRIVATE_KEY_VALUE}
publicKey=${JWT_PUBLIC_KEY_VALUE}
gateway-service.uuid=${GATEWAY_SERVICE_UUID}
booking-service.uuid=${BOOKING_SERVICE_UUID}
```

Now the application reads its secrets from the environment, and
Kubernetes can be the one to inject them.

### The awk dance

This is the part nobody warns you about. JWT keys in
`application.properties` aren't a single line — they're wrapped across
many lines with trailing backslashes, the way Java properties files do
multi-line values. `kubectl create secret --from-file` expects a clean
byte sequence on disk. So before I could load the keys into a Secret,
I had to flatten them:

```bash
awk '/^privateKey=/{flag=1; sub(/^privateKey=/,""); print; next}
     /^publicKey=/{flag=0}
     flag' "$ORIG" | tr -d '\\\n ' > ./01/tmp/private.txt

awk '/^publicKey=/{flag=1; sub(/^publicKey=/,""); print; next}
     flag' "$ORIG" | tr -d '\\\n ' > ./01/tmp/public.txt
```

The `awk` finds the right block of lines; the `tr -d '\\\n '` strips
the line-continuation backslashes, the newlines they continued, and any
stray whitespace. The result is a single uninterrupted blob of key
material that the Secret can consume cleanly.

This is the kind of plumbing that doesn't make it into tutorials and
that you only learn by hitting it. **Banked lesson:** when you're
moving values between config formats, always check whether the source
format has structural characters the destination doesn't understand.

### ConfigMap + Secret + Deployment template

With keys flattened, the rest fell into place. The Secret was built
with `kubectl create secret generic app-secrets ... --dry-run=client
-o yaml`, which gives you back a manifest you can keep in the repo
without ever running the imperative command in anger again.

The ConfigMap held the boring stuff — hostnames, ports, queue names:

```yaml
POSTGRES_HOST: "postgres"
POSTGRES_PORT: "5432"
RABBIT_MQ_HOST: "rabbitmq"
SESSION_SERVICE_HOST: "session-service"
SESSION_SERVICE_PORT: "8081"
...
```

And then every app-service Deployment used the same pattern: pull
**everything** from the ConfigMap and Secret with `envFrom`, then
override only the per-service bits inline:

```yaml
envFrom:
  - configMapRef: { name: app-config }
  - secretRef:    { name: app-secrets }
env:
  - name: POSTGRES_DB
    value: "hotels_db"          # different per service
  - name: JAVA_TOOL_OPTIONS
    value: "-Xmx256m -Xms128m -XX:MaxMetaspaceSize=128m"
```

The `envFrom` block was the lever that made this whole thing
maintainable: one source of truth for the shared values, one secret
for the sensitive ones, and only what genuinely differs gets repeated.

After `kubectl apply -f 01/k8s-manifests/` and a `kubectl rollout
restart` to nudge any services that had raced their dependencies,
`kubectl get pods` showed everything `Running` (`img/005.png`).

### The `eval $(minikube docker-env)` moment

Long before the pods got to `Running` there was a stretch where
everything was `ImagePullBackOff`. I had built the seven images on the
host with `docker build -t session-service:v1 ./session-service` and
the rest, and from the host's docker CLI I could see them in
`docker images`. But minikube **runs its own docker daemon inside the
VM**, and that daemon couldn't see anything I had built on the host.
With no image registry pushed to and `imagePullPolicy: IfNotPresent`,
Kubernetes was looking for `session-service:v1` in a place that didn't
have it.

The fix is one line, and obvious only once you've been bitten by it:

```bash
eval $(minikube docker-env)
```

This re-points your current shell's `docker` CLI at the daemon
**inside** minikube. Run your `docker build` after that, and the
images materialize inside the cluster where it can actually use them
without ever needing a registry. Re-applying my Deployments after that
turned the pull errors into `ContainerCreating`, and a minute later
`Running` (`img/004.png`).

**Banked lesson:** when a local cluster can't pull a "local" image,
remember that "local" depends on whose docker daemon you mean.

### Memory pressure

Seven JVMs + Postgres + RabbitMQ in 4 GB was not going to fit. Pods
were going to `OOMKilled` faster than I could read their startup logs.
Three changes brought it back under control:

1. **Give minikube more headroom:** restart with
   `minikube start --memory=6144 --cpus=2`.
2. **Constrain each JVM** with `JAVA_TOOL_OPTIONS=-Xmx256m -Xms128m
   -XX:MaxMetaspaceSize=128m` so Spring couldn't decide to claim
   gigabytes by default.
3. **Tell Kubernetes** about it with per-container
   `resources.limits.memory: 512Mi` / `requests.memory: 256Mi` so the
   scheduler could plan honestly.

Even after all that, cold-start times were brutal — `hotel-service`
took 101 seconds to come up; `session-service` took 108. You can see
both in the log dumps quoted in [`Report.md`](./Report.md). On a real
deployment I'd be adding readiness probes and an init phase; in a
school cluster I just waited.

### Tunnels, decode, Postman

Exposing the gateway and session services for testing was the easy
part: NodePort Services + `minikube service`, and the Postman
collection talked to them over the assigned ports.

```bash
kubectl get secret app-secrets -o jsonpath='{.data.POSTGRES_PASSWORD}' \
  | base64 --decode
```

confirmed the Secret round-trip end-to-end (`img/006.png`,
`img/020.png`). The Postman collection at
[`application_tests.postman_collection.json`](./application_tests.postman_collection.json)
went all-green against the cluster (`img/007.png`).

And the dashboard showed it all in one place — pods, services,
secrets, configs, per-pod CPU/memory, live logs — at
`img/008.png`–`img/015.png`.

### Recreate vs Rolling

The closing exercise was the most interesting piece of Kubernetes
pedagogy in the whole project. The task: add a dummy dependency
(`commons-lang3`) to `hotel-service/pom.xml`, build it as
`hotel-service:v2`, and roll it out twice — once with
`strategy.type: Recreate`, once with the default `RollingUpdate` —
and time both.

```bash
kubectl patch deployment hotel-service \
  -p '{"spec":{"strategy":{"type":"Recreate","rollingUpdate":null}}}'

time (kubectl set image deployment/hotel-service hotel-service=hotel-service:v2 \
      && kubectl rollout status deployment/hotel-service)
```

Then switch to `RollingUpdate` and repeat. The numbers themselves are
captured in `img/018.png` and `img/019.png`, but the **shape** of the
result is the takeaway:

- **Recreate** kills all old pods first, then brings up the new ones.
  Wall-clock is short, but there's a real downtime window where the
  service is genuinely unavailable.
- **Rolling** brings up new pods alongside the old ones and only
  terminates an old pod once a new one is ready. Wall-clock is longer
  on a single-replica deployment because the new pod has to fully
  start before the old can leave — but there's no downtime window for
  callers.

On a single replica the tradeoff is starkest: Recreate is faster but
brutal, Rolling is slower but seamless. With three or more replicas
Rolling stops looking slow and starts looking like the obvious
default. Worth remembering: deployment strategy is a property of the
service's tolerance for unavailability, not a one-size answer.

---

## The final shape

```
                        ┌──────────────────────────────┐
                        │      minikube dashboard      │
                        │     (web UI for the same     │
                        │      cluster, on demand)     │
                        └──────────────┬───────────────┘
                                       │
                                       ▼
   ┌───────────────────────────────────────────────────────────────┐
   │                     minikube node (6 GB)                      │
   │                                                               │
   │   ┌───────────────┐   ┌───────────────┐                       │
   │   │  app-config   │   │  app-secrets  │ (DB pw, RMQ pw,       │
   │   │  (ConfigMap)  │   │   (Secret)    │  UUIDs, JWT keypair)  │
   │   └──────┬────────┘   └──────┬────────┘                       │
   │          │  envFrom           │  envFrom                      │
   │          └───────────┬────────┘                               │
   │                      ▼                                        │
   │   ┌──────────────────────────────────────────────────────┐    │
   │   │   Deployments                                        │    │
   │   │   ├─ postgres   (with postgres-init ConfigMap)       │    │
   │   │   ├─ rabbitmq                                        │    │
   │   │   └─ session / hotel / booking / payment /           │    │
   │   │      loyalty / report / gateway                      │    │
   │   └──────────────────────────────────────────────────────┘    │
   │                      │                                        │
   │                      ▼                                        │
   │   ┌──────────────────────────────────────────────────────┐    │
   │   │   ClusterIP Services (one per Deployment) +          │    │
   │   │   NodePort tunnels for gateway-service & session     │    │
   │   └──────────────────────────────────────────────────────┘    │
   └───────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Postman    │
                    │  test suite │
                    └─────────────┘
```

Everything lives in [`src/01/k8s-manifests/`](./01/k8s-manifests/):

- `00-configmap.yaml` — non-sensitive shared config
- `01-secret.yaml` — credentials + UUIDs + JWT keypair
- `02-postgres-init-cm.yaml` — per-service database creation script
- `03-postgres.yaml` / `04-rabbitmq.yaml` — infrastructure tier
- `05-…-11-gateway-service.yaml` — the seven application services

---

## What I'd do differently next time

- **Move from raw YAML to Kustomize overlays.** As soon as a second
  environment shows up (dev vs prod, or a staging cluster with
  different memory limits), editing the base manifests starts to
  feel wrong. A `base/` + `overlays/dev/` + `overlays/prod/` layout
  with image tags and resource limits patched per-environment is
  the natural next step.
- **Add a readiness probe to every service.** Today I lean on
  `kubectl rollout restart` to recover from startup races between
  the app tier and its dependencies. With a proper
  `readinessProbe` hitting `/actuator/health`, Kubernetes can
  sequence things itself and stop sending traffic to a pod whose
  database connection hasn't finished initializing.
- **Don't bake images locally.** `eval $(minikube docker-env)`
  works, but it means the deployment is reproducible **only** on
  this exact minikube. Pushing to a registry — even a local
  in-cluster one — makes the manifests portable to any cluster,
  which is what Kubernetes is supposed to give you in the first
  place.

---

## Stack

`Kubernetes` · `minikube` · `kubectl` · `ConfigMaps` · `Secrets` ·
`Deployments` · `Services` · `Spring Boot` · `PostgreSQL` ·
`RabbitMQ` · `Postman`
