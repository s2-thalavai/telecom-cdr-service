#!/usr/bin/env bash
# Build image, make it available to the local cluster (minikube / kind / Docker Desktop), deploy.
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE="${IMAGE:-telecom-cdr-service:1.0.0}"
NS="telecom-cdr"
CONTEXT="$(kubectl config current-context)"
echo "Kubernetes context: $CONTEXT"
docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" .
case "$CONTEXT" in
  minikube*)      minikube image load "$IMAGE" ;;
  kind-*)         kind load docker-image "$IMAGE" --name "${CONTEXT#kind-}" ;;
  docker-desktop) echo "Docker Desktop shares the local image store." ;;
  *)              echo "Remote cluster: push $IMAGE to a registry and update k8s/kustomization.yaml (images:)." ;;
esac
kubectl apply -k k8s/
kubectl -n "$NS" rollout status statefulset/mysql --timeout=300s
kubectl -n "$NS" rollout status deployment/telecom-cdr-service --timeout=300s
kubectl -n "$NS" get pods,svc,ingress,hpa
echo; echo "Access: make k8s-port-forward   then open http://localhost:8080"
