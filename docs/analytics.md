# Analytics & insights (Phase 6)

## The core split: recording vs. querying

```
User action (upload, play, download, delete, create, update, share, ...)
    v
AssetService / ProjectService / ProjectShareService / CollectionService / ClientService
    v
AnalyticsService#record(userId, eventType, assetId, projectId)   <- write side, one method
    v
analytics_event (PostgreSQL)
    v
AnalyticsQueryService                                            <- read side, aggregation only
    v
AnalyticsController -> AnalyticsOverviewResponse / AssetAnalyticsResponse / ...
```

`AnalyticsService` (recording) and `AnalyticsQueryService` (aggregation) are deliberately two
separate interfaces — the domain services that generate events never need to know how those
events get aggregated later, and the controller that serves aggregated metrics never touches the
write path. Neither is a "God service": recording is one method, querying is five, each backed by
plain database aggregates.

## Why there's no `POST /analytics/events`

Every event is a side effect of a real, already-authorized domain action — never something a
client reports about itself. `AnalyticsService#record` takes `userId` as a plain parameter, and
every call site passes the authenticated caller's id resolved from their JWT, never anything from
a request body. There is no endpoint a client could call to insert an arbitrary event, which is
what actually makes event fabrication impossible (not a permission check — the absence of a route).

## Why play vs. download is a caller-declared flag, not an observed fact

Phase 4 has exactly one endpoint, `GET /assets/{id}/file`, returning a short-lived presigned R2
URL. Once that URL is handed back, the actual file transfer happens directly between the client
and R2 — this backend never sees it again. So "played" vs. "downloaded" can only ever be *intent*,
never *observed behavior*. `?download=true` (default `false`) on the existing endpoint captures
that intent without adding a new route or changing the endpoint's actual behavior for any existing
caller.

## The one real design decision: what does "my asset analytics" mean

Every `AnalyticsEvent.user` is the person who performed the action (the authenticated requester) —
a literal reading of "the authenticated User determines event ownership." Under Phase 5
collaboration, that person isn't always the resource's owner (an EDIT collaborator can upload to,
play, or delete someone else's shared Asset).

Given that, "my asset analytics" could mean either:
- **creator-centric**: "how has content I own been engaged with, by anyone with access", or
- **actor-centric**: "what have I personally done", i.e. `WHERE event.user_id = caller` everywhere.

This app uses **actor-centric**, uniformly, across every analytics query — overview activity
counts, asset totals/rankings, project activity, collaboration activity-over-time all filter on
`event.user_id = caller`. Reasons:
1. It's the one interpretation that stays internally consistent across every metric with a single
   filter column, rather than switching lenses per field.
2. It survives Asset/Project deletion for free — no query ever needs to join to a row that might
   no longer exist to answer "is this mine".
3. It matches the spec's literal event-ownership wording without needing a second interpretation
   for edge cases like `ASSET_DELETED` (which can't be creator-scoped at all once the row is gone).

Metrics that aren't event-log questions in the first place — current library size
(`totalAssets`/`totalProjects`/etc.), current storage bytes, current share counts — are computed
directly from the live domain tables filtered by real ownership, same as every other authorized
query in this app. Those are unaffected by this decision.

## Why Asset/Project references on an event are plain UUIDs, not foreign keys

`AnalyticsEvent.assetId` / `.projectId` are plain `UUID` columns with no `@ManyToOne`/FK
constraint — deliberately, because Asset and Project *are* deletable (unlike `User`, which is
never deleted anywhere in this app, so `AnalyticsEvent.user` is a real FK). This means:

- Deleting an Asset or Project never cascades into `analytics_event`, never violates a foreign
  key, and needs zero cleanup code in `AssetServiceImpl`/`ProjectServiceImpl` — a historical event
  simply keeps pointing at an id that may no longer resolve to anything.
- Anything that needs a *name* for display (a ranking entry, an activity-by-project breakdown)
  batch-resolves ids back to current rows via `findAllById` and treats a missing result as "title
  unavailable", not an error — see `AssetRankingEntry.title` / `ProjectActivityEntry.projectName`,
  both nullable for exactly this reason. The count/history itself is never lost, only the label.

## Performance

Every `AnalyticsEventRepository`/`AssetRepository`/`ProjectShareRepository` method used here is a
single `COUNT`/`GROUP BY` aggregate — nothing in `AnalyticsQueryServiceImpl` loads raw event or
asset rows into Java to sum or sort by hand. Day-bucketed activity uses one native query (JPQL has
no portable "truncate a timestamp to a day"); everything else is plain Spring Data derived queries
or JPQL. Ranking queries return `(id, count)` pairs and resolve display names via one batch
`findAllById` call, never one query per row. Indexes on `analytics_event`:
`(user_id, timestamp)`, `(event_type, timestamp)`, `(asset_id, event_type)`,
`(project_id, event_type)` — matching the query patterns above.

## Analytics failures never break the action they're attached to

`AnalyticsServiceImpl#record` wraps its work in a try/catch and logs a warning on failure instead
of rethrowing — mirroring the existing best-effort R2 cleanup pattern in `AssetServiceImpl`. A
database hiccup while recording an event must never turn a successful upload/share/delete into a
failed HTTP response; analytics is a secondary concern layered on top of the real action, not a
precondition for it.

## Event types

```
ASSET_UPLOADED, ASSET_PLAYED, ASSET_DOWNLOADED, ASSET_DELETED
PROJECT_CREATED, PROJECT_UPDATED, PROJECT_SHARED
COLLECTION_CREATED
CLIENT_CREATED
```

Deliberately no `PROJECT_DELETED`/`CLIENT_DELETED`/`COLLECTION_DELETED` — not in the original set,
and workspace totals for those resources are live counts anyway (see above), not something a
delete event would feed into. `ASSET_DELETED` is the one deletion event, because
`totalDeletions`/upload-vs-deletion trends are genuinely useful asset-activity signals the other
resources don't have an equivalent for.

## What Phase 6 deliberately does not do

- No separate analytics microservice, no Kafka/Elasticsearch/data warehouse/BigQuery — Postgres
  and a handful of indexed aggregate queries are enough at this scale.
- No caching layer — every endpoint is a handful of cheap indexed queries; caching would be
  premature optimization with nothing to point at yet.
- No machine learning, no statistical-significance claims — "most played", "most active", and the
  activity trend percentage are plain counts and a percent-change calculation, nothing inferred.
- Tags don't get their own analytics response — the spec marks this optional ("only if it fits
  naturally"), and `totalTags` in the overview already covers the one clearly-useful number;
  per-tag usage rankings weren't asked for by any of the 33 required test scenarios.
