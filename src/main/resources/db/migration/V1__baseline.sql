-- Baseline schema for Phase 1-7, generated from the current JPA entity mappings (Hibernate
-- created this exact schema against a throwaway database via ddl-auto=create, then this file was
-- captured with `pg_dump --schema-only` and stripped of session-level SET/psql meta-commands) --
-- not hand-written, so it can't drift from what the entities actually produce. From here on,
-- every schema change is its own new Vn__description.sql; ddl-auto is `validate` in every
-- environment (see application.properties) so Hibernate only ever double-checks this against the
-- entities, never mutates it. See docs/deployment.md.

CREATE TABLE analytics_event (
    "timestamp" timestamp(6) with time zone NOT NULL,
    asset_id uuid,
    id uuid NOT NULL,
    project_id uuid,
    user_id uuid NOT NULL,
    event_type character varying(30) NOT NULL,
    CONSTRAINT analytics_event_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['ASSET_UPLOADED'::character varying, 'ASSET_PLAYED'::character varying, 'ASSET_DOWNLOADED'::character varying, 'ASSET_DELETED'::character varying, 'PROJECT_CREATED'::character varying, 'PROJECT_UPDATED'::character varying, 'PROJECT_SHARED'::character varying, 'COLLECTION_CREATED'::character varying, 'CLIENT_CREATED'::character varying])::text[])))
);

CREATE TABLE app_user (
    creator_mode_unlocked boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    google_id character varying(255),
    password_hash character varying(255),
    username character varying(255) NOT NULL
);

CREATE TABLE asset (
    bpm integer,
    duration_seconds integer,
    created_at timestamp(6) with time zone NOT NULL,
    file_size_bytes bigint,
    updated_at timestamp(6) with time zone NOT NULL,
    audio_format character varying(10),
    id uuid NOT NULL,
    project_id uuid,
    user_id uuid NOT NULL,
    asset_type character varying(20) NOT NULL,
    musical_key character varying(30),
    title character varying(200) NOT NULL,
    storage_key character varying(500),
    client_notes character varying(2000),
    description character varying(2000),
    CONSTRAINT asset_asset_type_check CHECK (((asset_type)::text = ANY ((ARRAY['BEAT'::character varying, 'COMPOSITION'::character varying, 'SAMPLE'::character varying, 'SOUND_EFFECT'::character varying, 'STEM'::character varying])::text[])))
);

CREATE TABLE asset_tags (
    asset_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE client (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    linked_user_id uuid,
    user_id uuid NOT NULL,
    company character varying(150),
    name character varying(150) NOT NULL,
    email character varying(254),
    notes character varying(2000)
);

CREATE TABLE collection (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name character varying(150) NOT NULL
);

CREATE TABLE collection_assets (
    asset_id uuid NOT NULL,
    collection_id uuid NOT NULL
);

CREATE TABLE project (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    client_id uuid,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(2000),
    CONSTRAINT project_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNING'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])))
);

CREATE TABLE project_share (
    created_at timestamp(6) with time zone NOT NULL,
    permission character varying(10) NOT NULL,
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT project_share_permission_check CHECK (((permission)::text = ANY ((ARRAY['VIEW'::character varying, 'EDIT'::character varying])::text[])))
);

CREATE TABLE tag (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name character varying(50) NOT NULL
);

ALTER TABLE ONLY analytics_event ADD CONSTRAINT analytics_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_email_key UNIQUE (email);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_google_id_key UNIQUE (google_id);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_username_key UNIQUE (username);
ALTER TABLE ONLY asset ADD CONSTRAINT asset_pkey PRIMARY KEY (id);
ALTER TABLE ONLY client ADD CONSTRAINT client_pkey PRIMARY KEY (id);
ALTER TABLE ONLY collection ADD CONSTRAINT collection_pkey PRIMARY KEY (id);
ALTER TABLE ONLY project ADD CONSTRAINT project_pkey PRIMARY KEY (id);
ALTER TABLE ONLY project_share ADD CONSTRAINT project_share_pkey PRIMARY KEY (id);
ALTER TABLE ONLY tag ADD CONSTRAINT tag_pkey PRIMARY KEY (id);
ALTER TABLE ONLY asset_tags ADD CONSTRAINT uk_asset_tags_pair UNIQUE (asset_id, tag_id);
ALTER TABLE ONLY collection_assets ADD CONSTRAINT uk_collection_assets_pair UNIQUE (collection_id, asset_id);
ALTER TABLE ONLY project_share ADD CONSTRAINT uk_project_share_project_user UNIQUE (project_id, user_id);
ALTER TABLE ONLY tag ADD CONSTRAINT uk_tag_user_name UNIQUE (user_id, name);

CREATE INDEX idx_analytics_event_asset_type ON analytics_event USING btree (asset_id, event_type);
CREATE INDEX idx_analytics_event_project_type ON analytics_event USING btree (project_id, event_type);
CREATE INDEX idx_analytics_event_type_timestamp ON analytics_event USING btree (event_type, "timestamp");
CREATE INDEX idx_analytics_event_user_timestamp ON analytics_event USING btree (user_id, "timestamp");
CREATE INDEX idx_asset_project_id ON asset USING btree (project_id);
CREATE INDEX idx_asset_user_id ON asset USING btree (user_id);
CREATE INDEX idx_client_linked_user_id ON client USING btree (linked_user_id);
CREATE INDEX idx_client_user_id ON client USING btree (user_id);
CREATE INDEX idx_collection_user_id ON collection USING btree (user_id);
CREATE INDEX idx_project_client_id ON project USING btree (client_id);
CREATE INDEX idx_project_share_project_id ON project_share USING btree (project_id);
CREATE INDEX idx_project_share_user_id ON project_share USING btree (user_id);
CREATE INDEX idx_project_user_id ON project USING btree (user_id);
CREATE INDEX idx_tag_user_id ON tag USING btree (user_id);

ALTER TABLE ONLY tag ADD CONSTRAINT fk5qq6vtihb9pe0b7a9qp9wsixg FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY collection_assets ADD CONSTRAINT fk6amiwvs8gxyn3fjy8x40w5uuo FOREIGN KEY (collection_id) REFERENCES collection(id);
ALTER TABLE ONLY project ADD CONSTRAINT fk88d6ydqumdx1jhuyh3opxnvkt FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY project ADD CONSTRAINT fk8nw995uro0115f1go0dmrtn2d FOREIGN KEY (client_id) REFERENCES client(id);
ALTER TABLE ONLY analytics_event ADD CONSTRAINT fkc5n57hfy55edp5yyvotxltfg8 FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY collection ADD CONSTRAINT fkclghbvpyywq5b83wl29d7lgv FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY asset_tags ADD CONSTRAINT fkh2iwco348yq7f4481lolmov3q FOREIGN KEY (tag_id) REFERENCES tag(id);
ALTER TABLE ONLY client ADD CONSTRAINT fkknrhri28fj31rywjcof0janxk FOREIGN KEY (linked_user_id) REFERENCES app_user(id);
ALTER TABLE ONLY project_share ADD CONSTRAINT fkm4tlxu2kqkvotso1oq4twqdtj FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY asset ADD CONSTRAINT fkpc35hx07css26o6hl0s83g96u FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY asset ADD CONSTRAINT fkpkqgrihax0upcxheg8pgm6nh FOREIGN KEY (project_id) REFERENCES project(id);
ALTER TABLE ONLY client ADD CONSTRAINT fkqqdwacidjq73vuxpn95i63b5d FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ONLY asset_tags ADD CONSTRAINT fkt9hrx1cnpaeexhnn7brdka7vi FOREIGN KEY (asset_id) REFERENCES asset(id);
ALTER TABLE ONLY collection_assets ADD CONSTRAINT fktitposkjlkrb3egjea07nrt9w FOREIGN KEY (asset_id) REFERENCES asset(id);
ALTER TABLE ONLY project_share ADD CONSTRAINT fktjlx3enp9a0pqg1ch73w8l9sl FOREIGN KEY (project_id) REFERENCES project(id);
