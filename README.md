# Student Management System
Production-grade Spring Boot application deployed on KIND cluster using Jenkins CI and ArgoCD CD.

---

## Tech Stack
| Layer | Tool |
|---|---|
| Application | Java 17 + Spring Boot 3.2 |
| Build | Maven |
| CI | Jenkins |
| Code Quality | SonarQube |
| Security Scan | Trivy |
| Container Registry | AWS ECR |
| CD / GitOps | ArgoCD |
| Kubernetes | KIND (on EC2) |
| Database | MySQL 8 |

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/students | Get all students |
| GET | /api/students/{id} | Get student by ID |
| POST | /api/students | Create new student |
| PUT | /api/students/{id} | Update student |
| DELETE | /api/students/{id} | Delete student |
| GET | /api/students/search?name=john | Search by name |
| GET | /api/students/course/{course} | Get by course |
| GET | /api/students/health | API health check |
| GET | /actuator/health | K8s health probe |

---

## EC2 Instance Plan

| Instance | Purpose | Install |
|---|---|---|
| EC2 #1 | SonarQube | Docker, SonarQube, PostgreSQL |
| EC2 #2 | Jenkins CI | Jenkins, Java 17, Maven, Docker, Trivy, AWS CLI |
| EC2 #3 | KIND + ArgoCD | Docker, KIND, kubectl, Helm, ArgoCD |

All instances: t2.medium (2 vCPU / 4 GB RAM), Ubuntu 22.04, port 22 open.

---

## Phase 2: EC2 Setup Commands

### EC2 #2 — Jenkins Server

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java 17
sudo apt install -y openjdk-17-jdk

# Install Jenkins
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo tee \
  /usr/share/keyrings/jenkins-keyring.asc > /dev/null
echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update && sudo apt install -y jenkins
sudo systemctl start jenkins && sudo systemctl enable jenkins

# Install Maven
sudo apt install -y maven

# Install Docker
sudo apt install -y docker.io
sudo usermod -aG docker jenkins
sudo usermod -aG docker ubuntu
sudo systemctl start docker && sudo systemctl enable docker

# Install Trivy
sudo apt install -y wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main | \
  sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt update && sudo apt install -y trivy

# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt install -y unzip && unzip awscliv2.zip
sudo ./aws/install

# Get Jenkins initial password
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

### EC2 #1 — SonarQube Server

```bash
# Update and install Docker
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker ubuntu

# Run SonarQube using Docker Compose
mkdir sonarqube && cd sonarqube
cat > docker-compose.yml <<EOF
version: "3"
services:
  sonarqube:
    image: sonarqube:lts-community
    ports:
      - "9000:9000"
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://db:5432/sonar
      SONAR_JDBC_USERNAME: sonar
      SONAR_JDBC_PASSWORD: sonar
    volumes:
      - sonarqube_data:/opt/sonarqube/data
      - sonarqube_logs:/opt/sonarqube/logs
    depends_on:
      - db
  db:
    image: postgres:15
    environment:
      POSTGRES_USER: sonar
      POSTGRES_PASSWORD: sonar
      POSTGRES_DB: sonar
    volumes:
      - postgresql_data:/var/lib/postgresql/data
volumes:
  sonarqube_data:
  sonarqube_logs:
  postgresql_data:
EOF
sudo docker-compose up -d
# Access SonarQube at: http://<EC2-1-public-ip>:9000
# Default login: admin / admin
```

### EC2 #3 — KIND + ArgoCD Server

```bash
# Update and install Docker
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker ubuntu

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Install KIND
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Create KIND cluster using our config
kind create cluster --config kind-config.yaml
kubectl cluster-info

# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD pods to be ready
kubectl wait --for=condition=available --timeout=300s deployment/argocd-server -n argocd

# Expose ArgoCD UI via NodePort
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort"}}'

# Get ArgoCD initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

---

## Phase 3: AWS ECR Setup

```bash
# Create ECR repository (run from your local machine or EC2 #2)
aws ecr create-repository \
  --repository-name student-management \
  --region us-east-1

# Create ECR pull secret in Kubernetes (run on EC2 #3)
kubectl create secret docker-registry ecr-secret \
  --docker-server=<AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region us-east-1) \
  --namespace=student-app
```

---

## Phase 4: Deploy Application

```bash
# Apply all K8s manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/deployment.yaml

# Apply ArgoCD application (after replacing your GitHub URL in argocd-application.yaml)
kubectl apply -f k8s/argocd-application.yaml

# Check pods are running
kubectl get pods -n student-app

# Access the app
# http://<EC2-3-public-ip>:30080/api/students
```

---

## Sample API Request

```bash
# Create a student
curl -X POST http://<EC2-3-public-ip>:30080/api/students \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "course": "Computer Science",
    "grade": 10,
    "phoneNumber": "9876543210"
  }'

# Get all students
curl http://<EC2-3-public-ip>:30080/api/students
```
