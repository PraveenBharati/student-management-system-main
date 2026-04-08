# Student Management System — Helm Chart

## 📁 Folder Structure

```
helm/
└── student-management/
    ├── Chart.yaml                   # Chart metadata
    ├── values.yaml                  # All config lives here
    ├── argocd-application.yaml      # Apply this to ArgoCD
    └── templates/
        ├── _helpers.tpl             # Reusable template helpers
        ├── namespace.yaml
        ├── secret.yaml
        ├── configmap.yaml
        ├── mysql.yaml               # PVC + Deployment + Service
        ├── deployment.yaml          # Backend Deployment + Service
        └── frontend.yaml            # ConfigMap + Deployment + Service
```

## 🚀 How to Deploy

### Step 1 — Push Helm chart to your GitHub repo

```bash
# Copy the helm/ folder into your repo
cp -r helm/ /path/to/student-management-system/

git add helm/
git commit -m "feat: add production-ready Helm chart"
git push origin main
```

### Step 2 — Create the ECR pull secret (one-time setup)

```bash
kubectl create secret docker-registry ecr-secret \
  --docker-server=992382828560.dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region us-east-1) \
  --namespace=student-app
```

### Step 3 — Apply the ArgoCD Application

```bash
kubectl apply -f helm/student-management/argocd-application.yaml
```

ArgoCD will auto-sync and deploy everything from your Helm chart.

---

## 🔁 How Jenkins Updates the Image Tag

In your Jenkins pipeline, replace the `helm upgrade` command:

```groovy
// Jenkinsfile
stage('Deploy') {
    steps {
        sh """
        helm upgrade --install student-management ./helm/student-management \
          --namespace student-app \
          --set backend.image.tag=${BUILD_NUMBER} \
          --set secret.dbPassword=${DB_PASSWORD}
        """
    }
}
```

Or if using **ArgoCD + Git**: update the image tag in `values.yaml` and push:

```bash
sed -i "s/tag: .*/tag: \"${BUILD_NUMBER}\"/" helm/student-management/values.yaml
git commit -am "ci: update image tag to ${BUILD_NUMBER}"
git push origin main
# ArgoCD auto-syncs and deploys the new version
```

---

## 🔐 Production Secret Best Practices

**Never commit real passwords to Git.**

### Option A — Pass at deploy time (simple)
```bash
helm upgrade --install student-management ./helm/student-management \
  --set secret.dbPassword=<real-password>
```

### Option B — AWS Secrets Manager + External Secrets Operator (recommended)
Install the External Secrets Operator and create an ExternalSecret resource pointing to AWS Secrets Manager. This avoids secrets ever touching Git.

---

## 🛠️ Common Helm Commands

```bash
# Dry run — see what will be deployed without applying
helm template student-management ./helm/student-management

# Install for the first time
helm install student-management ./helm/student-management \
  --namespace student-app \
  --set secret.dbPassword=<real-password>

# Upgrade (e.g., new image tag)
helm upgrade student-management ./helm/student-management \
  --set backend.image.tag=7 \
  --set secret.dbPassword=<real-password>

# Check release status
helm status student-management -n student-app

# Rollback to previous version
helm rollback student-management 1 -n student-app

# Uninstall
helm uninstall student-management -n student-app
```

---

## 🌍 Next Step — Multiple Environments

When you're ready, add environment-specific value files:

```
helm/student-management/
├── values.yaml          # base defaults
├── values-dev.yaml      # dev overrides (1 replica, debug logs)
└── values-prod.yaml     # prod overrides (3 replicas, prod DB)
```

Deploy per environment:
```bash
helm upgrade --install student-management ./helm/student-management \
  -f values.yaml \
  -f values-prod.yaml \
  --set secret.dbPassword=<real-password>
```
