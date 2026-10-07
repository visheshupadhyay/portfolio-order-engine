# Local Kubernetes guide

This guide runs the stateless Order Engine application in Docker Desktop
Kubernetes while PostgreSQL, Redis, and Kafka remain in Docker Compose. It is
a learning setup, not a production topology.

```text
Kubernetes Deployment and Service
            |
            v
Docker Compose PostgreSQL + Redis + Kafka
```

The application Pods are disposable. PostgreSQL remains the durable source of
truth; its named Compose volume keeps data across ordinary container restarts.

## Prerequisites

- Docker Desktop with Kubernetes enabled.
- `kubectl` connected to the `docker-desktop` context.
- [kind](https://kind.sigs.k8s.io/) CLI, used to load a local image into
  Docker Desktop's Kubernetes node.
- A local `.env` file containing `ORDER_ENGINE_DB_PASSWORD` and
  `JWT_BASE64_SECRET`.

Confirm the cluster:

```powershell
kubectl config use-context docker-desktop
kubectl get nodes
kind get clusters
```

## Local-only configuration

The tracked files under `k8s/` do not contain passwords or machine-specific
Docker IP addresses.

- `order-engine-config.example.yaml` is a safe template.
- `local-order-engine-config.yaml` is ignored by Git and holds this machine's
  Compose connection addresses.
- `local-secret.yaml` is ignored by Git and holds the database password and
  JWT signing secret.

Copy the ConfigMap template once:

```powershell
Copy-Item k8s/order-engine-config.example.yaml k8s/local-order-engine-config.yaml
```

In `local-order-engine-config.yaml`, use the actual Compose-container IP
addresses for `DB_URL`, `REDIS_HOST`, and `KAFKA_BOOTSTRAP_SERVERS`. Keep
`ORDER_INPUT_FILE` set to `/app/orders.txt`; that is the file path inside the
application image, not the source-tree path.

Create `k8s/local-secret.yaml` without committing it:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: order-engine-secrets
  namespace: order-engine
type: Opaque
stringData:
  DB_PASSWORD: "replace-with-the-value-of-ORDER_ENGINE_DB_PASSWORD"
  JWT_BASE64_SECRET: "replace-with-the-value-of-JWT_BASE64_SECRET"
```

## Connect Docker Desktop Kubernetes to Compose dependencies

Start only the Compose dependencies. The Compose application container is
stopped because Kubernetes will run Order Engine instead:

```powershell
docker compose up -d postgres redis kafka
docker compose stop app
```

List the private Compose addresses:

```powershell
$composeNetwork = docker network inspect portfolio-order-engine_default | ConvertFrom-Json
$composeNetwork.Containers.psobject.Properties.Value |
    Select-Object Name, IPv4Address
```

Connect the Docker Desktop Kubernetes node to the Compose network:

```powershell
docker network connect portfolio-order-engine_default desktop-control-plane
```

Use the listed PostgreSQL, Redis, and Kafka addresses in the ignored local
ConfigMap. Those addresses can change when Compose containers are recreated,
so inspect and update the local file again if connectivity breaks.

## Build, load, and deploy

Build a local image and load it into the Kubernetes node. `imagePullPolicy:
Never` in the Deployment means Kubernetes uses only the image loaded this way.

```powershell
docker build -t portfolio-order-engine:1.0 .
kind load docker-image portfolio-order-engine:1.0 --name desktop
```

Apply the namespace, local settings, deployment, and Service:

```powershell
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/local-secret.yaml
kubectl apply -f k8s/local-order-engine-config.yaml
kubectl apply -f k8s/order-engine-deployment.yaml
kubectl apply -f k8s/order-engine-service.yaml

kubectl rollout status deployment/order-engine -n order-engine --timeout=180s
kubectl get pods -n order-engine -l app=order-engine
```

The Deployment keeps two healthy application Pods. It injects non-secret
values from the ConfigMap and sensitive values from the Secret. Startup,
liveness, and readiness probes use Spring Boot Actuator health endpoints.

## Verify the API

Expose the internal Kubernetes Service only to the local machine for testing:

```powershell
kubectl port-forward -n order-engine service/order-engine 8082:8080
```

Keep that terminal open, then use a second terminal:

```powershell
Invoke-RestMethod "http://localhost:8082/actuator/health" |
    ConvertTo-Json -Depth 6
```

Swagger UI is available at `http://localhost:8082/swagger-ui/index.html`.
Press `Ctrl + C` in the port-forward terminal when it is no longer needed; it
stops the tunnel only, not the application Pods.

## Rolling update and rollback

Build and load a new image tag, then change the Deployment image from `1.0`
to the new tag and apply the manifest. The Deployment strategy keeps two
available Pods and permits one temporary extra Pod during replacement.

```powershell
docker build -t portfolio-order-engine:1.1 .
kind load docker-image portfolio-order-engine:1.1 --name desktop

kubectl apply -f k8s/order-engine-deployment.yaml
kubectl rollout status deployment/order-engine -n order-engine --timeout=180s
```

For an emergency rollback:

```powershell
kubectl rollout undo deployment/order-engine -n order-engine
```

After a manual rollback, change the tracked Deployment YAML back to the same
image tag and run `kubectl apply -f k8s/order-engine-deployment.yaml`. This
keeps the source manifest and live cluster state aligned.

## Cleanup

Delete only the Kubernetes application resources:

```powershell
kubectl delete namespace order-engine
```

This does not delete the Compose PostgreSQL, Redis, Kafka containers, or the
PostgreSQL volume. To remove the temporary network connection too:

```powershell
docker network disconnect portfolio-order-engine_default desktop-control-plane
```

Use `docker compose down` to stop Compose services while preserving the
PostgreSQL volume. `docker compose down -v` deletes that volume and its data.
