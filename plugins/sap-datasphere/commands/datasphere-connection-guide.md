---
name: datasphere-connection-guide
description: Generate step-by-step connection setup guides for SAP Datasphere
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "<source-system>"
arguments:
  - name: source
    description: "Source system: s4hana-cloud, s4hana-onprem, bw4hana, hana, aws-s3, azure-blob, gcp-storage, kafka, odata, generic-http"
    required: true
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return connection prerequisites, required credentials or certificates without exposing secrets, safe validation steps, and tenant-only checks. Default to read-only setup guidance and do not create connections.


Generate a connection setup guide for the specified source system.

## Available Source Types

### s4hana-cloud
SAP S/4HANA Cloud Public Edition via API

### s4hana-onprem
SAP S/4HANA On-Premise via Cloud Connector

### bw4hana
SAP BW/4HANA for model transfer and data replication

### hana
SAP HANA database (Cloud or On-Premise)

### aws-s3
Amazon S3 for file-based data loading

### azure-blob
Azure Blob Storage and Azure Data Lake

### gcp-storage
Google Cloud Storage

### kafka
Apache Kafka / Confluent for streaming data

### odata
Generic OData services

### generic-http
REST APIs via Generic HTTP connection

---

## Guide: s4hana-cloud

### SAP S/4HANA Cloud Connection Setup

#### Prerequisites

- [ ] SAP S/4HANA Cloud tenant with admin access
- [ ] Communication User created in S/4HANA Cloud
- [ ] Communication Arrangement configured
- [ ] Datasphere user with DW Integrator role

#### Step 1: Create Communication User (S/4HANA Cloud)

1. Navigate to **Maintain Communication Users** app
2. Click **New**
3. Enter:
   - User Name: `DATASPHERE_USER`
   - Description: `SAP Datasphere Integration`
   - Password: Set strong password
4. Save

#### Step 2: Create Communication System (S/4HANA Cloud)

1. Navigate to **Communication Systems** app
2. Click **New**
3. Configure:
   ```yaml
   System ID: DATASPHERE
   System Name: SAP Datasphere
   Host Name: <your-datasphere-tenant>.eu10.hcs.cloud.sap
   Users for Inbound Communication:
     - User: DATASPHERE_USER
     - Authentication: User ID and Password
   ```
4. Save

#### Step 3: Create Communication Arrangement (S/4HANA Cloud)

1. Navigate to **Communication Arrangements** app
2. Click **New**
3. Select Scenario: `SAP_COM_0531` (for data extraction)
4. Configure:
   ```yaml
   Communication System: DATASPHERE
   Inbound Communication User: DATASPHERE_USER
   Outbound Services: Enable required services
   ```
5. Save and note the service URL

#### Step 4: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **SAP S/4HANA Cloud**
3. Configure:
   ```yaml
   Connection Name: S4HC_PROD
   Description: S/4HANA Cloud Production

   Connection Details:
     Host: <s4hana-tenant>.s4hana.ondemand.com
     Port: 443

   Authentication:
     Method: User Name and Password
     User: DATASPHERE_USER
     Password: <password>
   ```
4. Test Connection
5. Save

#### Step 5: Validate Connection

1. Navigate to **Data Builder** > **Import Objects**
2. Select connection: `S4HC_PROD`
3. Browse available CDS views
4. Import a test view to validate

#### Available Features

| Feature | Supported |
|---------|-----------|
| Remote Tables | Yes |
| Replication Flows | Yes |
| Real-Time Replication | Yes (ODP-enabled views) |
| Data Flows | Yes |

---

## Guide: s4hana-onprem

### SAP S/4HANA On-Premise Connection Setup

#### Prerequisites

- [ ] SAP Cloud Connector installed and configured
- [ ] S/4HANA system accessible from Cloud Connector host
- [ ] RFC user with required authorizations
- [ ] Network connectivity verified

#### Step 1: Configure Cloud Connector

1. Access Cloud Connector Admin UI: `https://<scc-host>:8443`
2. Connect to SAP BTP subaccount
3. Add System Mapping:
   ```yaml
   Back-end Type: ABAP System
   Protocol: RFC
   Internal Host: <s4hana-host>
   Internal Port: <RFC port, typically 33XX>
   Virtual Host: s4hana-prod.virtual
   Virtual Port: 443

   Principal Propagation: Configure if needed
   ```
4. Add Resource:
   ```yaml
   URL Path: /
   Access Policy: Path and all sub-paths
   ```

#### Step 2: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **SAP S/4HANA**
3. Configure:
   ```yaml
   Connection Name: S4H_ONPREM
   Description: S/4HANA On-Premise Production

   Connection Details:
     Use Cloud Connector: Yes
     Location ID: <location-id> (if configured)
     Virtual Host: s4hana-prod.virtual
     Virtual Port: 443
     Client: <SAP Client>

   Authentication:
     Method: User Name and Password
     User: <RFC User>
     Password: <password>
   ```
4. Test Connection
5. Save

#### Step 3: Configure ODP Extraction (for Replication)

In S/4HANA, ensure:
1. ODP contexts are active (SAPI, ABAP_CDS, BW)
2. CDS views have `@Analytics.dataExtraction.enabled: true`
3. Delta queues configured for changed data capture

#### Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection timeout | Check Cloud Connector mapping and firewall |
| Authentication failed | Verify RFC user and authorizations |
| No objects found | Check ODP context and CDS view annotations |
| Replication fails | Enable delta queue for source |

---

## Guide: aws-s3

### Amazon S3 Connection Setup

#### Prerequisites

- [ ] AWS account with S3 access
- [ ] IAM user or role with S3 permissions
- [ ] Access Key ID and Secret Access Key
- [ ] Bucket name and region known

#### Step 1: Create IAM Policy (AWS)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::your-bucket-name",
        "arn:aws:s3:::your-bucket-name/*"
      ]
    }
  ]
}
```

#### Step 2: Create IAM User (AWS)

1. Navigate to IAM > Users > Add User
2. User name: `datasphere-integration`
3. Access type: Programmatic access
4. Attach policy from Step 1
5. Save Access Key ID and Secret

#### Step 3: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **Amazon Simple Storage Service**
3. Configure:
   ```yaml
   Connection Name: AWS_S3_DATA
   Description: AWS S3 Data Lake

   Connection Details:
     Region: eu-west-1
     Bucket: your-bucket-name
     Root Path: /datasphere/input/ (optional)

   Authentication:
     Access Key ID: <access-key>
     Secret Access Key: <secret-key>
   ```
4. Test Connection
5. Save

#### Step 4: Import Data

1. Create new **Data Flow**
2. Add **Cloud Storage** source
3. Select connection: `AWS_S3_DATA`
4. Configure file pattern: `*.csv` or `*.parquet`
5. Map columns and load

#### Supported File Formats

| Format | Read | Write |
|--------|------|-------|
| CSV | Yes | Yes |
| Parquet | Yes | Yes |
| JSON | Yes | No |
| ORC | Yes | No |

---

## Guide: azure-blob

### Azure Blob Storage Connection Setup

#### Prerequisites

- [ ] Azure subscription with Storage account
- [ ] Container created
- [ ] Access key or SAS token available

#### Step 1: Get Storage Account Details (Azure)

1. Navigate to Storage Account in Azure Portal
2. Note:
   - Storage account name
   - Container name
3. Go to **Access keys** and copy Key1 or connection string

#### Step 2: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **Microsoft Azure Blob Storage**
3. Configure:
   ```yaml
   Connection Name: AZURE_BLOB_DATA
   Description: Azure Blob Storage for Data Lake

   Connection Details:
     Storage Account: yourstorageaccount
     Container: datasphere-input
     Root Path: /raw/ (optional)

   Authentication:
     Method: Account Key
     Key: <storage-account-key>
   ```
4. Test Connection
5. Save

#### Alternative: Azure Data Lake Gen2

```yaml
Connection Type: Microsoft Azure Data Lake Store Gen2
Connection Details:
  Storage Account: yourdatalake
  Container: analytics
  File System: Enable hierarchical namespace
```

---

## Guide: kafka

### Apache Kafka Connection Setup

#### Prerequisites

- [ ] Kafka cluster accessible (Confluent Cloud or self-managed)
- [ ] Bootstrap servers known
- [ ] Authentication credentials (if secured)
- [ ] Topic names known

#### Step 1: Gather Kafka Details

```yaml
Bootstrap Servers: kafka.example.com:9092
Security Protocol: SASL_SSL (for Confluent Cloud)
SASL Mechanism: PLAIN
Topic Pattern: sales-events-*
```

#### Step 2: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **Apache Kafka**
3. Configure:
   ```yaml
   Connection Name: KAFKA_EVENTS
   Description: Event streaming from Kafka

   Connection Details:
     Bootstrap Servers: broker1.kafka.com:9092,broker2.kafka.com:9092

   Security:
     Protocol: SASL_SSL
     SASL Mechanism: PLAIN

   Authentication:
     Username: <api-key>
     Password: <api-secret>
   ```
4. Test Connection
5. Save

#### Step 3: Create Replication Flow

1. Create new **Replication Flow**
2. Source: Kafka connection
3. Select topics
4. Configure:
   ```yaml
   Load Type: Initial and Delta
   Data Format: JSON or Avro
   Target: Local Table
   ```

#### Confluent Cloud Specifics

```yaml
Bootstrap Servers: pkc-xxxxx.region.aws.confluent.cloud:9092
Security Protocol: SASL_SSL
SASL Mechanism: PLAIN
Username: <API_KEY>
Password: <API_SECRET>
Schema Registry URL: https://psrc-xxxxx.region.aws.confluent.cloud
```

---

## Guide: generic-http

### Generic HTTP (REST API) Connection Setup

#### Prerequisites

- [ ] API endpoint URL
- [ ] Authentication method known
- [ ] API documentation available

#### Step 1: Create Connection (Datasphere)

1. Navigate to **Connections** in Datasphere
2. Click **Create** > **Generic HTTP**
3. Configure:
   ```yaml
   Connection Name: REST_API_SOURCE
   Description: External REST API

   Connection Details:
     Base URL: https://api.example.com/v1

   Authentication:
     Method: OAuth 2.0 Client Credentials
     Token URL: https://auth.example.com/oauth/token
     Client ID: <client-id>
     Client Secret: <client-secret>
   ```
4. Test Connection
5. Save

#### Step 2: Use in Task Chain

1. Create **Task Chain**
2. Add **REST API** task step
3. Configure:
   ```yaml
   Connection: REST_API_SOURCE
   Method: GET
   Path: /data/records
   Headers:
     Content-Type: application/json
   Query Parameters:
     startDate: ${startDate}
     limit: 1000
   ```

#### Authentication Methods

| Method | Configuration |
|--------|---------------|
| None | No additional config |
| Basic Auth | Username + Password |
| OAuth 2.0 | Token URL + Client ID/Secret |
| API Key | Header name + Key value |

---

## Common Troubleshooting

### IP Allowlisting

Get Datasphere IP addresses:
1. Go to **System** > **Administration**
2. Find **IP Allowlist** section
3. Add IPs to your source system's allowlist

### Certificate Issues

1. Go to **System** > **Administration** > **TLS**
2. Upload server certificates if self-signed
3. Ensure certificate chain is complete

### Connection Test Fails

| Error | Solution |
|-------|----------|
| Timeout | Check network, firewall, Cloud Connector |
| SSL Error | Upload certificate, check TLS version |
| Auth Failed | Verify credentials, check user locks |
| Not Found | Verify host/port, check virtual mapping |

---

Provide the appropriate guide based on the user's requested source type. Customize the configuration values based on their specific environment.
