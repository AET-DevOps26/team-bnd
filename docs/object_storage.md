# SeaweedFS S3 Object Storage
Alexandria uses [SeaweedFs](https://github.com/seaweedfs/seaweedfs) as the S3 compatible object storage in Docker Compose deployments. A different provider can be configured via environment variables.



| Variable        | Default                  | Description                         |
| --------------- | ------------------------ | ----------------------------------- |
| `S3_ENDPOINT`   | `http://s3-storage:8333` | S3 gateway URL                      |
| `S3_REGION`     | `eu-central-1`           | Region label (SeaweedFS ignores it) |
| `S3_ACCESS_KEY` | `admin`                  | Access key id                       |
| `S3_SECRET_KEY` | `locals3password`        | Secret access key                   |
| `S3_BUCKET`     | `alexandria-storage`     | Bucket documents are stored in      |

Documents are stored in S3-compatible object storage. On document upload via the client, the Spring service upload the document to object storage. Requests to the GenAI service reference documents by their object key.
