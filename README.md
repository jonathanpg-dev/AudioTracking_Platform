The goal of this web app is to facilitate the management of audio tracks/stems by implementing a tagging system(by genre, mood, etc.) and a analytics dashboard to keep track of what needs to be completed and what is working.

Features are developed incrementally as complete end-to-end vertical slices before expanding the domain model.

Phase 1
- User entity
- CRUD API
- PostgreSQL integration
- Postman testing

Phase 2
- Authentication
- JWT
- Password hashing

Phase 3
- Asset system
- Metadata management
- File upload pipeline

Phase 4
- Cloud object storage integration
- AWS S3 abstraction layer

See [docs/storage.md](docs/storage.md) for how audio file storage is configured and why.

Phase 5
- Clients
- Project collaboration and sharing (VIEW/EDIT permissions)

See [docs/collaboration.md](docs/collaboration.md) for the authorization model behind sharing.

Phase 6
- Analytics events and aggregated insights

See [docs/analytics.md](docs/analytics.md) for the event-recording/aggregation split and why it's designed the way it is.

Phase 7
- React + TypeScript frontend consuming the REST API

See [docs/frontend.md](docs/frontend.md) for the stack, auth flow, permission-aware UI, and how to
run it.

Phase 8
- Production deployment + CI/CD
    