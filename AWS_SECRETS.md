# AWS Secrets Manager setup

DB and Razorpay payment credentials are no longer stored in `application.properties`.
On startup, the app fetches them from an AWS Secrets Manager secret named
`heail/backend` (override with the `AWS_SECRET_NAME` env var) via
`spring.config.import=aws-secretsmanager:...` in
[application.properties](src/main/resources/application.properties). If the
secret can't be fetched, the app fails to start — this is intentional.

## 1. Create the secret

The secret value must be a JSON object with these exact keys:

```json
{
  "DB_URL": "jdbc:postgresql://<host>:5432/<db>",
  "DB_USERNAME": "<username>",
  "DB_PASSWORD": "<password>",
  "RAZORPAY_KEY_ID": "<key id>",
  "RAZORPAY_KEY_SECRET": "<key secret>",
  "RAZORPAY_WEBHOOK_SECRET": "<webhook secret>"
}
```

Create it (run yourself — this touches your AWS account):

```bash
aws secretsmanager create-secret \
  --name heail/backend \
  --secret-string file://secret.json \
  --region ap-south-1
```

Use **new, rotated** values for `DB_PASSWORD` (and ideally the mail app
password too, even though mail isn't moved to AWS here) — the old ones were
committed to the `heail-backend` git history and pushed to GitHub, so treat
them as compromised regardless of what happens next. Delete `secret.json`
after running the command; don't commit it.

To update the secret later:

```bash
aws secretsmanager put-secret-value \
  --secret-id heail/backend \
  --secret-string file://secret.json \
  --region ap-south-1
```

## 2. IAM permissions

Whoever/whatever runs the app (your local user, or the ECS/EC2 role in
production) needs read access to that one secret — nothing broader:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:ap-south-1:<account-id>:secret:heail/backend-*"
    }
  ]
}
```

Attach that policy to an IAM user (local dev) or an IAM role (prod — prefer
this over long-lived keys once deployed on ECS/EC2/EKS).

## 3. Local dev credentials

The app resolves AWS credentials via the standard SDK default chain, so any
of these work — set up whichever you already use:

- `aws configure` (writes `~/.aws/credentials`), or
- `aws configure sso` if your org uses IAM Identity Center, or
- env vars in your shell before `gradlew bootRun`:
  `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`

Run `aws configure` (or sso login) yourself — don't paste access keys into
chat. Also set the region if it's not already in your AWS config:

```bash
setx AWS_REGION ap-south-1
```

(use whatever region you actually created the secret in; adjust
`ap-south-1` above to match.)

## 4. Production

Don't put static AWS keys in the container. Attach an IAM role to whatever
runs the container (ECS task role, EC2 instance profile, etc.) with the
policy from step 2 — the SDK default chain picks it up automatically, same
code path as local.
