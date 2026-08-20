# Audio file storage (Phase 4)

## Why object storage instead of PostgreSQL

Audio files are large binary objects. PostgreSQL stores relational metadata about an Asset
(title, BPM, tags, ownership, and so on); the actual audio bytes live in object storage instead.
Storing binaries in the relational database would bloat the database, slow down backups, and
isn't what a relational database is designed for.

## Why Cloudflare R2

R2 exposes an S3-compatible API, so the official AWS SDK for Java works against it directly —
just pointed at R2's endpoint instead of AWS's. R2 also has no egress fees, which matters for a
media-heavy app where users will be downloading their own audio repeatedly.

## The `StorageService` abstraction

`AssetService` depends on the `StorageService` interface, never on `R2StorageService` or any AWS
SDK type directly:

```
AssetService -> StorageService -> R2StorageService -> Cloudflare R2
```

`StorageService` exposes three provider-agnostic operations — upload, generate a temporary
download URL, delete — using only plain JDK types (`InputStream`, `URI`, `Duration`). No AWS SDK
class, R2 concept, or credential ever appears above `R2StorageService`. A future
`S3StorageService implements StorageService` would be a new class in `storage/`, plus its own
configuration — `AssetService`, `AssetController`, the `Asset` entity, and every DTO would need
zero changes.

## Local configuration

All values are read from environment variables — never hard-coded, never committed. Set these in
your shell or your IDE's Run/Debug Configuration (see `.env.example` for the full list with
placeholder values):

| Variable | Required | Purpose |
|---|---|---|
| `R2_ENDPOINT` | yes | Your R2 account's S3-compatible endpoint |
| `R2_ACCESS_KEY_ID` | yes | R2 API token access key |
| `R2_SECRET_ACCESS_KEY` | yes | R2 API token secret |
| `R2_BUCKET` | yes | Bucket name |
| `R2_REGION` | no (`auto`) | R2's own convention; rarely needs changing |
| `STORAGE_MAX_FILE_SIZE_MB` | no (`4096`) | Max upload size, enforced by Spring's multipart limit |
| `STORAGE_PRESIGNED_URL_EXPIRATION_MINUTES` | no (`15`) | How long a download URL stays valid |

If any required variable is missing, the app fails to start with a clear "could not resolve
placeholder" error rather than silently running with broken storage.

`storage.max-file-size-mb` also drives `server.tomcat.max-http-form-post-size` and
`server.tomcat.max-swallow-size` (see `application.properties`) — embedded Tomcat has its own
connector-level cap on POST body size, independent of and much lower than (2MB by default) Spring's
own multipart limit. Both have to move together, or uploads past 2MB get rejected by Tomcat before
Spring's multipart limit is ever consulted.

## Supported formats and limits

MP3, WAV, FLAC, M4A. Validated by both the file extension and the file's actual byte signature
(`AudioFileValidator`) — renaming an arbitrary file to `beat.wav` is not enough to pass. Max
upload size defaults to 4096MB (4GB), to cover full-length compositions/stems, not just short
beats/samples — configurable via `STORAGE_MAX_FILE_SIZE_MB`.

## How private file access works

The R2 bucket is never made public and no permanent URL is ever stored or returned. `Asset`
stores a `storageKey` — a stable pointer to the object, not a URL. To access a file, the backend
verifies ownership and asks `StorageService` for a fresh, short-lived presigned URL
(`GET /api/v1/assets/{id}/file`) — valid for `STORAGE_PRESIGNED_URL_EXPIRATION_MINUTES` and never
persisted, since it would be stale/expired by the time it was read back anyway.

## Endpoints

```
POST   /api/v1/assets/{id}/file   upload or replace the audio file
GET    /api/v1/assets/{id}/file   get a temporary access URL
DELETE /api/v1/assets/{id}/file   delete the audio file (not the Asset)
```

All three require authentication and enforce Asset ownership the same way every other Asset
endpoint does — a wrong id and someone else's Asset id both produce an identical 404.

## Known consistency limitation

PostgreSQL and R2 are two separate systems — `@Transactional` cannot span both. Operations are
ordered to minimize inconsistent states (upload the new object before touching the database;
delete from R2 before clearing the database reference), and cleanup of an object being abandoned
(replaced or the whole Asset deleted) is best-effort — logged on failure, not retried — since
failing the user-facing operation over an unrelated storage hiccup would be worse than one
harmless orphaned object. The one exception is the explicit `DELETE .../file` endpoint, where a
storage failure is surfaced (502) rather than silently clearing the database reference, since that
endpoint's entire purpose is the deletion the user explicitly asked for.
