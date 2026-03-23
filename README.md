# Student Management System — Complete DevOps Project

A production-grade Java Spring Boot application deployed on a KIND Kubernetes cluster using Jenkins CI and ArgoCD GitOps CD pipeline.

---

## Architecture Overview

```
Developer → GitHub → Jenkins CI → AWS ECR → ArgoCD → KIND Cluster (EC2)
                         ↓
                    SonarQube + Trivy
```

## Tech Stack

| Layer | Tool | Purpose |
|---|---|---|
| Application | Java 17 + Spring Boot 3.2 | REST API |
| Build | Maven | Compile, test, package |
| CI | Jenkins | Automated pipeline |
| Code Quality | SonarQube | Static code analysis |
| Security Scan | Trivy | Container vulnerability scan |
| Registry | AWS ECR | Docker image storage |
| CD / GitOps | ArgoCD | Auto deployment |
| Kubernetes | KIND on EC2 | Container orchestration |
| Database | MySQL 8 | Persistent storage |

---

## EC2 Instance Plan

| Instance | Purpose | What to Install |
|---|---|---|
| EC2 #1 | SonarQube | Docker, Docker Compose, SonarQube |
| EC2 #2 | Jenkins CI | Jenkins, Java 17, Maven, Docker, Trivy, AWS CLI |
| EC2 #3 | KIND + ArgoCD | Docker, KIND, kubectl, Helm, ArgoCD |

**All instances:** Ubuntu 22.04 LTS, t2.medium (2 vCPU / 4 GB RAM), 20 GB storage

### Security Group Rules

**EC2 #1 — SonarQube**
```
Port 22    SSH        My IP only
Port 9000  SonarQube  0.0.0.0/0
```

**EC2 #2 — Jenkins**
```
Port 22    SSH        My IP only
Port 8080  Jenkins    0.0.0.0/0
```

**EC2 #3 — KIND + ArgoCD**
```
Port 22    SSH        My IP only
Port 30080 App        0.0.0.0/0
Port 30081 ArgoCD UI  0.0.0.0/0
```

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
| GET | /actuator/health | Kubernetes health probe |

---

## Phase 1 — GitHub Repository Setup

### Step 1 — Create the repository

Go to github.com → New repository
- Name: `student-management-system`
- Visibility: Public
- Click Create repository

### Step 2 — Update files before pushing

Open `k8s/deployment.yaml` and replace `<AWS_ACCOUNT_ID>` with your real AWS account ID.

Open `k8s/argocd-application.yaml` and replace `<YOUR_GITHUB_USERNAME>` with your GitHub username.

### Step 3 — Push all project files

```bash
cd student-management-system
git init
git remote add origin https://github.com/YOUR_USERNAME/student-management-system.git
git add .
git commit -m "Initial commit: Student Management System"
git push -u origin main
```

---

## Phase 2 — EC2 #1 Setup (SonarQube)

SSH into EC2 #1:
```bash
ssh -i devops-key.pem ubuntu@<EC2-1-PUBLIC-IP>
```

### Step 1 — Install Docker

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker ubuntu
exit
```

SSH back in, then continue.

### Step 2 — Install Docker Compose

```bash
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose --version
```

### Step 3 — Create SonarQube docker-compose file

```bash
mkdir sonarqube && cd sonarqube
nano docker-compose.yml
```

Paste exactly this content:

```yaml
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
```

Press `Ctrl+X` → `Y` → `Enter` to save.

### Step 4 — Start SonarQube

```bash
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
docker-compose up -d
docker-compose ps
```

Wait 3 minutes then open: `http://<EC2-1-PUBLIC-IP>:9000`

Login: `admin` / `admin` → change password → note it down

### Step 5 — Configure SonarQube project

- Click `Create Project` → `Manually`
- Project key: `student-management`
- Click `Set up`

### Step 6 — Generate token for Jenkins

- Top right → profile icon → `My Account` → `Security`
- Token name: `jenkins-token`
- Type: `Global Analysis Token`
- Expiry: `No expiration`
- Click `Generate` → **copy and save this token immediately**

### Step 7 — Create SonarQube webhook

- `Administration` → `Configuration` → `Webhooks` → `Create`
- Name: `Jenkins`
- URL: `http://<EC2-2-JENKINS-IP>:8080/sonarqube-webhook/`
- Click `Create`

> This webhook is critical — without it Jenkins will wait forever for the quality gate result.

---

## Phase 3 — EC2 #2 Setup (Jenkins)

SSH into EC2 #2:
```bash
ssh -i devops-key.pem ubuntu@<EC2-2-PUBLIC-IP>
```

### Step 1 — Install Java 17

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk
java -version
```

### Step 2 — Install Jenkins

```bash
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | \
  sudo gpg --dearmor -o /usr/share/keyrings/jenkins-keyring.gpg

echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.gpg] \
  https://pkg.jenkins.io/debian-stable binary/" | \
  sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update && sudo apt install -y jenkins
sudo systemctl start jenkins && sudo systemctl enable jenkins
sudo systemctl status jenkins
```

> **If Jenkins package install fails on Ubuntu 24, use this WAR method instead:**
> ```bash
> mkdir -p /home/ubuntu/jenkins && cd /home/ubuntu/jenkins
> wget https://get.jenkins.io/war-stable/latest/jenkins.war
> sudo tee /etc/systemd/system/jenkins.service > /dev/null << 'EOF'
> [Unit]
> Description=Jenkins Server
> After=network.target
> [Service]
> Type=simple
> User=ubuntu
> Environment="JENKINS_HOME=/home/ubuntu/jenkins"
> ExecStart=/usr/bin/java -jar /home/ubuntu/jenkins/jenkins.war --httpPort=8080
> Restart=on-failure
> [Install]
> WantedBy=multi-user.target
> EOF
> sudo systemctl daemon-reload
> sudo systemctl start jenkins && sudo systemctl enable jenkins
> ```

### Step 3 — Install Maven

```bash
sudo apt install -y maven
mvn -version
```

### Step 4 — Install Docker

```bash
sudo apt install -y docker.io
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker ubuntu
sudo chmod 666 /var/run/docker.sock
sudo systemctl restart jenkins
```

### Step 5 — Install Trivy

```bash
sudo apt install -y wget apt-transport-https gnupg lsb-release

wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | \
  sudo apt-key add -

echo deb https://aquasecurity.github.io/trivy-repo/deb \
  $(lsb_release -sc) main | \
  sudo tee -a /etc/apt/sources.list.d/trivy.list

sudo apt update && sudo apt install -y trivy
trivy --version
```

### Step 6 — Install AWS CLI

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt install -y unzip && unzip awscliv2.zip
sudo ./aws/install
aws --version
```

### Step 7 — Configure AWS and create ECR repository

```bash
aws configure
# Enter: Access Key ID, Secret Access Key, region: us-east-1, format: json

aws ecr create-repository \
  --repository-name student-management \
  --region us-east-1
```

Note down the `repositoryUri` from the output.

### Step 8 — Unlock Jenkins UI

```bash
# Standard install
sudo cat /var/lib/jenkins/secrets/initialAdminPassword

# WAR method
cat /home/ubuntu/jenkins/secrets/initialAdminPassword
```

Open `http://<EC2-2-PUBLIC-IP>:8080` → paste password → `Install suggested plugins` → create admin user

### Step 9 — Install Jenkins plugins

`Manage Jenkins` → `Plugins` → `Available plugins` → search and install:

```
SonarQube Scanner
Docker Pipeline
Docker Commons
Amazon ECR
AWS Credentials
Pipeline: AWS Steps
Git
GitHub Integration
```

Check `Restart Jenkins when installation is complete`

### Step 10 — Configure Tools in Jenkins

`Manage Jenkins` → `Tools`

**JDK:**
- Name: `JDK-17`
- Uncheck Install automatically
- JAVA_HOME: `/usr/lib/jvm/java-17-openjdk-amd64`

**Maven:**
- Name: `Maven-3.9`
- Uncheck Install automatically
- MAVEN_HOME: `/usr/share/maven`

**SonarQube Scanner:**
- Name: `SonarQube-Scanner`
- Check Install automatically

Click `Save`

### Step 11 — Configure SonarQube in Jenkins

`Manage Jenkins` → `System` → scroll to `SonarQube servers`

- Check `Environment variables`
- Click `Add SonarQube`
- Name: `SonarQube`
- Server URL: `http://<EC2-1-PUBLIC-IP>:9000`  ← no double http://
- Under token → `Add` → `Jenkins`
  - Kind: `Secret text`
  - Secret: `<your sonarqube token from Phase 2 Step 6>`
  - ID: `sonar-token`
- Select `sonar-token` from dropdown

Click `Save`

### Step 12 — Add credentials to Jenkins

`Manage Jenkins` → `Credentials` → `System` → `Global credentials` → `Add Credentials`

**Credential 1 — AWS Account ID:**
```
Kind   : Secret text
Secret : <your 12-digit AWS account ID>
ID     : aws-account-id
```

**Credential 2 — AWS Access Keys:**
```
Kind              : AWS Credentials
Access Key ID     : <your AWS access key>
Secret Access Key : <your AWS secret key>
ID                : aws-credentials
```

**Credential 3 — GitHub:**
```
Kind     : Username with password
Username : <your GitHub username>
Password : <your GitHub personal access token>
ID       : github-credentials
```

> To create GitHub token: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token → check `repo` and `workflow`

### Step 13 — Create Jenkins pipeline job

`New Item` → name: `student-management-pipeline` → `Pipeline` → `OK`

**General:**
- Check `GitHub project`
- URL: `https://github.com/YOUR_USERNAME/student-management-system`

**Pipeline:**
- Definition: `Pipeline script from SCM`
- SCM: `Git`
- Repository URL: `https://github.com/YOUR_USERNAME/student-management-system.git`
- Credentials: `github-credentials`
- Branch: `*/main`
- Script Path: `Jenkinsfile`

Click `Save`

### Step 14 — Run the pipeline

Jenkins dashboard → `student-management-pipeline` → `Build Now`

Watch all stages pass:
```
✅ Checkout
✅ Build & Unit Tests
✅ SonarQube Analysis
✅ Quality Gate
✅ Package JAR
✅ Build Docker Image
✅ Trivy Security Scan
✅ Push to AWS ECR
✅ Update K8s Manifest
```

---

## Phase 4 — EC2 #3 Setup (KIND + ArgoCD)

SSH into EC2 #3:
```bash
ssh -i devops-key.pem ubuntu@<EC2-3-PUBLIC-IP>
```

### Step 1 — Install Docker

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker ubuntu
exit
```

SSH back in, then continue.

### Step 2 — Install kubectl

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s \
  https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
kubectl version --client
```

### Step 3 — Install KIND

```bash
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind
kind version
```

### Step 4 — Install Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
helm version
```

### Step 5 — Install AWS CLI

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt install -y unzip && unzip awscliv2.zip
sudo ./aws/install
aws --version
```

### Step 6 — Attach IAM Role to EC2 #3

In AWS Console → IAM → Policies → Create Policy → JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    }
  ]
}
```

Name: `ECR-Pull-Policy`

Then IAM → Roles → Create Role → EC2 → attach `ECR-Pull-Policy` → name: `EC2-ECR-Pull-Role`

Then EC2 Console → select EC2 #3 → `Actions` → `Security` → `Modify IAM Role` → select `EC2-ECR-Pull-Role`

Verify:
```bash
aws sts get-caller-identity
```

### Step 7 — Create KIND cluster

```bash
cat > kind-config.yaml << 'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: student-cluster
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 30080
        protocol: TCP
      - containerPort: 30081
        hostPort: 30081
        protocol: TCP
EOF

kind create cluster --config kind-config.yaml
kubectl cluster-info
kubectl get nodes
```

Node should show `Ready` status.

### Step 8 — Install ArgoCD

```bash
kubectl create namespace argocd

kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

kubectl wait --for=condition=available \
  --timeout=300s deployment/argocd-server -n argocd

kubectl get pods -n argocd
```

All pods should show `Running`.

### Step 9 — Expose ArgoCD UI on NodePort 30081

```bash
kubectl patch svc argocd-server -n argocd \
  -p '{"spec": {"type": "NodePort", "ports": [{"port": 443, "targetPort": 8080, "nodePort": 30081}]}}'

kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

Open: `http://<EC2-3-PUBLIC-IP>:30081`
Login: username `admin` / password from above command

### Step 10 — Create ECR pull secret

```bash
kubectl create namespace student-app

kubectl create secret docker-registry ecr-secret \
  --docker-server=$(aws sts get-caller-identity \
    --query Account --output text).dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region us-east-1) \
  --namespace=student-app

kubectl get secret ecr-secret -n student-app
```

### Step 11 — Clone repo and update DB secret

```bash
sudo apt install -y git
git clone https://github.com/YOUR_USERNAME/student-management-system.git
cd student-management-system

nano k8s/secret.yaml
```

Update with your real DB password:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: student-app-secret
  namespace: student-app
type: Opaque
stringData:
  DB_USERNAME: root
  DB_PASSWORD: Admin@123
```

```bash
git config user.email "you@example.com"
git config user.name "Your Name"
git add k8s/secret.yaml
git commit -m "Update DB password"
git push origin main
```

### Step 12 — Connect GitHub repo to ArgoCD

In ArgoCD UI:
- `Settings` → `Repositories` → `Connect Repo`
- Method: `HTTPS`
- URL: `https://github.com/YOUR_USERNAME/student-management-system.git`
- Username: your GitHub username
- Password: your GitHub personal access token
- Click `Connect`

Green `Successful` status confirms connection.

### Step 13 — Create ArgoCD Application

In ArgoCD UI → `New App`:

```
Application Name : student-management
Project          : default
Sync Policy      : Automatic
                   ✅ Prune Resources
                   ✅ Self Heal

Source:
  Repository URL : https://github.com/YOUR_USERNAME/student-management-system.git
  Revision       : main
  Path           : k8s

Destination:
  Cluster URL    : https://kubernetes.default.svc
  Namespace      : student-app
```

Click `Create`

### Step 14 — Verify deployment

```bash
kubectl get pods -n student-app -w
```

Expected:
```
NAME                          READY   STATUS
mysql-xxxxx                   1/1     Running
student-app-xxxxx             1/1     Running
student-app-xxxxx             1/1     Running
```

### Step 15 — Test the API

```bash
# Health check
curl http://localhost:30080/api/students/health

# Create a student
curl -X POST http://localhost:30080/api/students \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "course": "Computer Science",
    "grade": 10,
    "phoneNumber": "9876543210"
  }'

# Get all students
curl http://localhost:30080/api/students
```

Open in browser:
```
http://<EC2-3-PUBLIC-IP>:30080/api/students
```

---

## Complete CI/CD Flow

Every time you push code and run Jenkins:

```
1.  Jenkins pulls latest code from GitHub
2.  Maven compiles and runs unit tests
3.  SonarQube scans for code quality issues
4.  Quality gate check — fails pipeline if quality is poor
5.  Maven packages the JAR file
6.  Docker builds the container image
7.  Trivy scans image for security vulnerabilities
8.  Image pushed to AWS ECR with build number tag
9.  Jenkins updates k8s/deployment.yaml with new image tag
10. Jenkins pushes updated manifest to GitHub
11. ArgoCD detects the change in k8s/ folder
12. ArgoCD deploys to KIND cluster automatically
13. Kubernetes does rolling update with zero downtime
14. New version of app is live at EC2-3-IP:30080
```

---

## Troubleshooting

**SonarQube Quality Gate stuck on PENDING:**
Make sure the SonarQube webhook is configured pointing to Jenkins URL `/sonarqube-webhook/`

**Docker permission denied on Jenkins:**
```bash
sudo chmod 666 /var/run/docker.sock
sudo systemctl restart jenkins
```

**SonarQube URL double http:// error:**
In Jenkins → Manage Jenkins → System → SonarQube servers → make sure URL is `http://IP:9000` not `http://http://IP:9000`

**ArgoCD shows OutOfSync:**
Click `Sync` in ArgoCD UI to manually trigger sync.

**Pods stuck in ImagePullBackOff:**
ECR token expired. Recreate the secret:
```bash
kubectl delete secret ecr-secret -n student-app
kubectl create secret docker-registry ecr-secret \
  --docker-server=<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region us-east-1) \
  --namespace=student-app
```

**Jenkins infinite pipeline loop:**
Disable the GitHub webhook and trigger builds manually from Jenkins dashboard.
