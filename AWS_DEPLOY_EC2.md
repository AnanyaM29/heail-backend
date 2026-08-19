# Deploying to EC2 with Docker

Follow this after [AWS_SECRETS.md](AWS_SECRETS.md) — this assumes the
`heail/backend` secret already exists (or you'll create it in step 1 below).
Region used throughout: **ap-south-1** (Mumbai). Everything here is a command
*you* run — I'm not executing AWS CLI calls in this session.

## 0. Install & configure the AWS CLI (if not already)

```bash
# Windows: download and run the MSI
# https://awscli.amazonaws.com/AWSCLIV2.msi
aws --version
```

Then, in your own terminal (don't paste your access key/secret into chat):

```bash
aws configure
# AWS Access Key ID / Secret Access Key: from an IAM user with permissions below
# Default region name: ap-south-1
```

For this initial setup you need an IAM user/role with permissions to manage
Secrets Manager, EC2, and IAM (to create the instance role). If you don't
have one, create it in the AWS Console: IAM → Users → Add user → attach
`SecretsManagerReadWrite`, `AmazonEC2FullAccess`, `IAMFullAccess` (these are
broad — fine for you as the account owner setting this up once; the *app
itself* never gets these, it only gets the narrow policy in step 3).

## 1. Create the secret (skip if already done)

See [AWS_SECRETS.md](AWS_SECRETS.md) step 1. Quick version:

```bash
cat > secret.json <<'EOF'
{
  "DB_URL": "jdbc:postgresql://<host>:5432/<db>",
  "DB_USERNAME": "<username>",
  "DB_PASSWORD": "<password>",
  "RAZORPAY_KEY_ID": "<key id>",
  "RAZORPAY_KEY_SECRET": "<key secret>",
  "RAZORPAY_WEBHOOK_SECRET": "<webhook secret>"
}
EOF

aws secretsmanager create-secret \
  --name heail/backend \
  --secret-string file://secret.json \
  --region ap-south-1

rm secret.json
```

Get the ARN for step 3:

```bash
aws secretsmanager describe-secret --secret-id heail/backend --region ap-south-1 --query ARN --output text
```

## 2. Security group

Replace `<YOUR_IP>` with your actual IP (`curl ifconfig.me`) — don't open SSH
to the world.

```bash
aws ec2 create-security-group \
  --group-name heail-backend-sg \
  --description "heail backend EC2" \
  --region ap-south-1

aws ec2 authorize-security-group-ingress \
  --group-name heail-backend-sg \
  --protocol tcp --port 22 --cidr <YOUR_IP>/32 \
  --region ap-south-1

aws ec2 authorize-security-group-ingress \
  --group-name heail-backend-sg \
  --protocol tcp --port 8080 --cidr 0.0.0.0/0 \
  --region ap-south-1
```

(Port 8080 open to the world is fine to start; once you point a domain at
this, put nginx + certbot or an ALB in front for TLS — that's a follow-up
step, not covered here.)

## 3. Build the image and push it to ECR

Do this before launching the instance — the whole point is that EC2 comes up
already running the image, instead of you SSHing in to build it.

```bash
aws ecr create-repository --repository-name heail-backend --region ap-south-1
# note the "repositoryUri" in the output, e.g.
# <account-id>.dkr.ecr.ap-south-1.amazonaws.com/heail-backend

cd D:/heail/heail-backend
docker build -t heail-backend .

aws ecr get-login-password --region ap-south-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-south-1.amazonaws.com

docker tag heail-backend:latest <account-id>.dkr.ecr.ap-south-1.amazonaws.com/heail-backend:latest
docker push <account-id>.dkr.ecr.ap-south-1.amazonaws.com/heail-backend:latest
```

Each time you have a new build to deploy: rebuild, `docker tag`, `docker
push` with a new tag (or `:latest` again), then follow "Redeploying a new
build later" below on the running instance.

## 4. IAM role for the EC2 instance

This is what lets the app fetch the secret and pull the image with **no AWS
keys on the box at all** — the whole point of using an instance role instead
of static credentials.

```bash
cat > trust-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ec2.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role --role-name heail-backend-ec2-role \
  --assume-role-policy-document file://trust-policy.json

cat > secrets-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "secretsmanager:GetSecretValue",
    "Resource": "<PASTE THE ARN FROM STEP 1 HERE>"
  }]
}
EOF

aws iam put-role-policy --role-name heail-backend-ec2-role \
  --policy-name heail-backend-secrets-read \
  --policy-document file://secrets-policy.json

# lets the instance pull from ECR
aws iam attach-role-policy --role-name heail-backend-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

aws iam create-instance-profile --instance-profile-name heail-backend-ec2-profile
aws iam add-role-to-instance-profile \
  --instance-profile-name heail-backend-ec2-profile \
  --role-name heail-backend-ec2-role

rm trust-policy.json secrets-policy.json
```

## 5. Key pair (for SSH — optional now, but useful for `docker logs`/debugging)

```bash
aws ec2 create-key-pair --key-name heail-backend-key \
  --region ap-south-1 --query 'KeyMaterial' --output text > heail-backend-key.pem
chmod 400 heail-backend-key.pem
```

Keep `heail-backend-key.pem` — don't commit it.

## 6. Launch the instance — it pulls and runs the image on boot

`t3.micro` — free-tier eligible on a new account (750 hrs/month for 12
months), 1GB RAM. Amazon Linux 2023 has Docker in its default repos. The
`user-data` script below runs automatically on first boot: installs Docker,
logs in to ECR using the instance role, and starts the container — so by the
time the instance is running, the app is already up.

```bash
cat > user-data.sh <<'EOF'
#!/bin/bash
dnf install -y docker
systemctl enable --now docker

REGION=ap-south-1
ACCOUNT_ID=<account-id>
REPO=heail-backend

aws ecr get-login-password --region $REGION \
  | docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com

docker run -d --name heail-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  -e AWS_REGION=$REGION \
  -e AWS_SECRET_NAME=heail/backend \
  $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$REPO:latest
EOF

aws ec2 run-instances \
  --image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --instance-type t3.micro \
  --key-name heail-backend-key \
  --security-groups heail-backend-sg \
  --iam-instance-profile Name=heail-backend-ec2-profile \
  --user-data file://user-data.sh \
  --region ap-south-1 \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=heail-backend}]'

rm user-data.sh
```

Note the `InstanceId` in the output, then get its public IP:

```bash
aws ec2 describe-instances --instance-ids <INSTANCE_ID> \
  --region ap-south-1 --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
```

No `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` anywhere on the box — the SDK
picks up credentials from the instance's IAM role via the metadata service,
same code path Spring Cloud AWS already uses locally with `aws configure`.

## 7. Verify

Give it a minute or two after `run-instances` for `user-data` to finish
(Docker install + image pull), then:

```bash
curl http://<PUBLIC_IP>:8080/actuator/health   # or whatever health path exists

# if that doesn't respond yet, check progress over SSH:
ssh -i heail-backend-key.pem ec2-user@<PUBLIC_IP>
sudo cat /var/log/cloud-init-output.log   # user-data script's own output
docker logs -f heail-backend               # app logs — watch it fetch the secret and start
```

## Redeploying a new build later

```bash
# on your machine, after step 3's docker build/tag/push with a new image:
ssh -i heail-backend-key.pem ec2-user@<PUBLIC_IP>
docker pull <account-id>.dkr.ecr.ap-south-1.amazonaws.com/heail-backend:latest
docker stop heail-backend && docker rm heail-backend
docker run -d --name heail-backend --restart unless-stopped -p 8080:8080 \
  -e AWS_REGION=ap-south-1 -e AWS_SECRET_NAME=heail/backend \
  <account-id>.dkr.ecr.ap-south-1.amazonaws.com/heail-backend:latest
```

## Follow-ups (not covered here — ask if/when you want these)

- TLS + domain (nginx reverse proxy + certbot, or an ALB) so `heail.in` isn't
  served over plain HTTP on port 8080.
- CI/CD so `docker build && docker run` isn't manual every deploy.
- Rotate the DB/mail credentials that were exposed in git history (see
  [AWS_SECRETS.md](AWS_SECRETS.md)) — do this regardless of the above.
