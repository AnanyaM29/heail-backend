# AWS Secrets Manager setup

DB and Razorpay credentials come from an AWS Secrets Manager secret named
`heail/backend` (override via the `AWS_SECRET_NAME` env var), region
`ap-south-1` — both local dev and EC2 read from the exact same secret, so
there's only ever one place to update a credential. See
[application.properties](src/main/resources/application.properties) for the
`spring.config.import=aws-secretsmanager:...` line that pulls it in. If the
secret can't be fetched, the app fails to start — that's intentional.

## 1. Create the secret

Console: Secrets Manager → **Store a new secret** → *Other type of secret* →
switch to key/value rows → add these six keys with real values:

| Key | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host>:5432/<db>` |
| `DB_USERNAME` | your DB username |
| `DB_PASSWORD` | **a new, rotated password** — not the one in git history |
| `RAZORPAY_KEY_ID` | from Razorpay Dashboard (test or live) |
| `RAZORPAY_KEY_SECRET` | from Razorpay Dashboard |
| `RAZORPAY_WEBHOOK_SECRET` | from the webhook you create in Razorpay (see below) |

Secret name: **`heail/backend`** exactly. Region (top-right dropdown):
**Asia Pacific (Mumbai) `ap-south-1`** — double-check this, it's the one
thing that broke last time (secret got created in Stockholm by mistake).

CLI equivalent:

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

rm secret.json   # don't leave real secrets sitting in a local file
```

To update a value later (e.g. once you have real Razorpay live keys):

```bash
aws secretsmanager put-secret-value \
  --secret-id heail/backend \
  --secret-string file://secret.json \
  --region ap-south-1
```

Get the ARN (needed for the IAM policies below):

```bash
aws secretsmanager describe-secret --secret-id heail/backend --region ap-south-1 --query ARN --output text
```

## 2. IAM — two separate identities, same narrow policy

Neither ever gets more than read access to this one secret. The policy
(swap in your real ARN from step 1):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "secretsmanager:GetSecretValue",
    "Resource": "arn:aws:secretsmanager:ap-south-1:<account-id>:secret:heail/backend-*"
  }]
}
```

**Local dev (your machine)** — an IAM user with an access key:

```bash
aws iam create-user --user-name heail-local-dev
aws iam put-user-policy --user-name heail-local-dev \
  --policy-name heail-backend-secrets-read \
  --policy-document file://secrets-policy.json
aws iam create-access-key --user-name heail-local-dev
```

The last command prints an `AccessKeyId`/`SecretAccessKey` pair — put those
into `D:/heail/.env` as `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (already
has placeholder rows). `docker-compose.yml`'s `backend` service already has
`env_file: .env`, so nothing else needs to change — the AWS SDK inside the
container picks these up automatically via its default credential chain.
Don't paste the secret access key anywhere else (chat included) — treat it
like a password.

**EC2 (production)** — an IAM role attached to the instance, no static keys
at all:

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
aws iam put-role-policy --role-name heail-backend-ec2-role \
  --policy-name heail-backend-secrets-read \
  --policy-document file://secrets-policy.json
aws iam create-instance-profile --instance-profile-name heail-backend-ec2-profile
aws iam add-role-to-instance-profile \
  --instance-profile-name heail-backend-ec2-profile \
  --role-name heail-backend-ec2-role

# Attach it to the already-running instance:
aws ec2 associate-iam-instance-profile \
  --instance-id <your-instance-id> \
  --iam-instance-profile Name=heail-backend-ec2-profile

rm trust-policy.json secrets-policy.json
```

### The gotcha that actually bites here: IMDSv2 hop limit

EC2's own OS reaches the instance metadata service (where the role's
temporary credentials live) fine by default — but a **Docker container**
sitting on the bridge network is one extra network hop away, and new EC2
instances default to a hop limit of **1**, which silently blocks exactly
that. Without this fix, the container gets no credentials and the same
"secret can't be fetched" failure as a missing IAM role, even though the
role is correctly attached. Run this once per instance:

```bash
aws ec2 modify-instance-metadata-options \
  --instance-id <your-instance-id> \
  --http-put-response-hop-limit 2 \
  --http-tokens required
```

## 3. Razorpay webhook (for the `RAZORPAY_WEBHOOK_SECRET` value)

Razorpay Dashboard → Settings → Webhooks → **Add New Webhook**:
- URL: `https://heail.in/api/v1/webhooks/razorpay`
- Active events: at least `payment.captured` and `payment.failed`
- Secret: Razorpay generates one when you save — that's your
  `RAZORPAY_WEBHOOK_SECRET` value for step 1.

## 4. Local dev — no `aws configure` needed

Since the access key/secret live in `.env` (step 2) and Compose's
`env_file: .env` already passes them into the backend container, that's the
whole local setup — no `~/.aws` profile required. Just:

```bash
docker compose up -d --build backend
docker compose logs -f backend   # watch it fetch the secret and start
```

## Rollback

If this breaks and you need to unblock immediately: put the real DB/Razorpay
values back as literal defaults in `application.properties` (like before)
and remove the `spring.config.import` line — same shape as the very first
version of this file, before AWS was involved. Not recommended long-term
(same reason as always: real credentials sitting in a public git repo) but
it's a real fallback if AWS access is the thing blocking you.
