-- The server's mirror of the SQLDelight schema, at SQLDelight schema version 14.
--
-- `SchemaDriftTest` compares this against the committed `.db` snapshot column by column,
-- so the two cannot silently diverge. Read that test before changing anything here: the
-- rules below are what it enforces.
--
-- The type mapping is deliberate rather than clever, because ADR 0008 warned that the two
-- schemas stop being mirror images the moment `server_seq` exists and that the mapping has
-- to be maintained on purpose:
--
--   SQLite TEXT     -> text
--   SQLite REAL     -> double precision
--   SQLite INTEGER  -> bigint, except `is_*` columns, which become boolean
--
-- Three tempting improvements are deliberately not made, because each one would trade a
-- mechanically checkable mirror for a nicer-looking column:
--
--   * `id` stays `text` rather than `uuid`. The ids are client-generated UUID v7 (ADR
--     0006), but a client that ever produced a non-UUID id would have its rows rejected
--     at the boundary instead of merely looking untidy.
--   * Instants stay `bigint` epoch milliseconds rather than `timestamptz`. The clients
--     store them that way, and a conversion on every read and write is a place for a
--     timezone bug to live.
--   * `incurred_on`, `travelled_on` and `purchased_on` stay `text` ISO dates rather than
--     `date`, for the same reason.
--
-- Each is worth revisiting once something other than sync reads this database.

-- --------------------------------------------------------------------------------------
-- The pull cursor
-- --------------------------------------------------------------------------------------

-- One sequence shared by every synced table, not one per table.
--
-- ADR 0008 decision 1 has clients remember a single "last server_seq seen" and pull with
-- `server_seq > ?`. A single cursor across several tables only means anything if the
-- ordering is shared, so all synced rows draw from here.
--
-- A studio therefore sees gaps, because the numbers it never sees belong to other
-- studios. Gaps are harmless: the cursor is an ordering, not a count.
CREATE SEQUENCE sync_seq AS bigint START WITH 1 INCREMENT BY 1;

-- Assigns the next sequence value on insert *and on update*.
--
-- The update half is the point. A row whose server_seq stayed still when it changed would
-- sit behind every cursor that had already passed it and would never be pulled again —
-- silently, which is the failure mode ADR 0008 exists to avoid. Applied as a trigger
-- rather than a default so that no server code can forget it.
CREATE FUNCTION assign_server_seq() RETURNS trigger AS $$
BEGIN
    NEW.server_seq := nextval('sync_seq');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- --------------------------------------------------------------------------------------
-- Accounts and people
-- --------------------------------------------------------------------------------------

-- A client account: an individual, a couple, a company, or an agency.
CREATE TABLE client (
    id           text   NOT NULL PRIMARY KEY,
    studio_id    text   NOT NULL,
    account_name text   NOT NULL,
    account_type text   NOT NULL,
    notes        text,
    tags         text   NOT NULL DEFAULT '[]',
    created_at   bigint NOT NULL,
    updated_at   bigint NOT NULL,
    deleted_at   bigint,
    version      bigint NOT NULL DEFAULT 1,
    server_seq   bigint NOT NULL
);

CREATE TABLE contact (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL,
    first_name text   NOT NULL,
    last_name  text   NOT NULL,
    company    text,
    job_title  text,
    emails     text   NOT NULL DEFAULT '[]',
    phones     text   NOT NULL DEFAULT '[]',
    notes      text,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    server_seq bigint NOT NULL
);

-- People belong to accounts through a role, rather than being the account.
CREATE TABLE client_contact (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL,
    client_id  text   NOT NULL,
    contact_id text   NOT NULL,
    role       text   NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    server_seq bigint NOT NULL,
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (contact_id) REFERENCES contact(id)
);

-- --------------------------------------------------------------------------------------
-- The work
-- --------------------------------------------------------------------------------------

CREATE TABLE project (
    id                   text   NOT NULL PRIMARY KEY,
    studio_id            text   NOT NULL,
    client_id            text   NOT NULL,
    name                 text   NOT NULL,
    service_line         text   NOT NULL,
    status               text   NOT NULL,
    service_template_id  text,
    contract_value_minor bigint,
    contract_currency    text,
    enquired_at          bigint,
    booked_at            bigint,
    notes                text,
    created_at           bigint NOT NULL,
    updated_at           bigint NOT NULL,
    deleted_at           bigint,
    version              bigint NOT NULL DEFAULT 1,
    server_seq           bigint NOT NULL,
    FOREIGN KEY (client_id) REFERENCES client(id)
);

CREATE TABLE session (
    id               text   NOT NULL PRIMARY KEY,
    studio_id        text   NOT NULL,
    project_id       text   NOT NULL,
    title            text   NOT NULL,
    kind             text   NOT NULL,
    status           text   NOT NULL,
    starts_at        bigint NOT NULL,
    ends_at          bigint NOT NULL,
    time_zone_id     text   NOT NULL,
    location_name    text,
    location_address text,
    call_time        bigint,
    notes            text,
    created_at       bigint NOT NULL,
    updated_at       bigint NOT NULL,
    deleted_at       bigint,
    version          bigint NOT NULL DEFAULT 1,
    -- Trailing on the SQLite side because ALTER TABLE appends; kept trailing here so the
    -- two schemas read in the same order.
    latitude         double precision,
    longitude        double precision,
    server_seq       bigint NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE service_template (
    id                           text   NOT NULL PRIMARY KEY,
    studio_id                    text   NOT NULL,
    name                         text   NOT NULL,
    service_line                 text   NOT NULL,
    default_session_duration_min bigint NOT NULL,
    default_session_count        bigint NOT NULL DEFAULT 1,
    base_price_minor             bigint,
    base_price_currency          text,
    default_deliverable_count    bigint,
    default_turnaround_days      bigint,
    default_revision_rounds      bigint,
    notes                        text,
    created_at                   bigint NOT NULL,
    updated_at                   bigint NOT NULL,
    deleted_at                   bigint,
    version                      bigint NOT NULL DEFAULT 1,
    server_seq                   bigint NOT NULL
);

-- --------------------------------------------------------------------------------------
-- Money
-- --------------------------------------------------------------------------------------

CREATE TABLE lead (
    id                   text   NOT NULL PRIMARY KEY,
    studio_id            text   NOT NULL,
    name                 text   NOT NULL,
    source               text   NOT NULL,
    status               text   NOT NULL,
    received_at          bigint NOT NULL,
    email                text,
    phone                text,
    first_response_at    bigint,
    service_line         text,
    desired_date         text,
    budget_low_minor     bigint,
    budget_high_minor    bigint,
    budget_currency      text,
    referred_by          text,
    lost_reason          text,
    converted_project_id text,
    converted_client_id  text,
    notes                text,
    created_at           bigint NOT NULL,
    updated_at           bigint NOT NULL,
    deleted_at           bigint,
    version              bigint NOT NULL DEFAULT 1,
    server_seq           bigint NOT NULL
);

CREATE TABLE quote (
    id           text   NOT NULL PRIMARY KEY,
    studio_id    text   NOT NULL,
    project_id   text   NOT NULL,
    number       text   NOT NULL,
    status       text   NOT NULL,
    currency     text   NOT NULL,
    lines        text   NOT NULL DEFAULT '[]',
    issued_at    bigint,
    valid_until  bigint,
    accepted_at  bigint,
    declined_at  bigint,
    notes        text,
    terms        text,
    created_at   bigint NOT NULL,
    updated_at   bigint NOT NULL,
    deleted_at   bigint,
    version      bigint NOT NULL DEFAULT 1,
    server_seq   bigint NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE contract (
    id                     text    NOT NULL PRIMARY KEY,
    studio_id              text    NOT NULL,
    project_id             text    NOT NULL,
    title                  text    NOT NULL,
    status                 text    NOT NULL,
    sent_at                bigint,
    signed_at              bigint,
    signer_name            text,
    signer_email           text,
    retainer_minor         bigint,
    retainer_currency      text,
    is_retainer_refundable boolean NOT NULL DEFAULT false,
    turnaround_days        bigint,
    revision_rounds        bigint,
    cancellation_terms     text,
    reschedule_terms       text,
    weather_clause         text,
    usage_license          text,
    document_reference     text,
    notes                  text,
    created_at             bigint  NOT NULL,
    updated_at             bigint  NOT NULL,
    deleted_at             bigint,
    version                bigint  NOT NULL DEFAULT 1,
    server_seq             bigint  NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE invoice (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL,
    project_id text   NOT NULL,
    number     text   NOT NULL,
    kind       text   NOT NULL,
    status     text   NOT NULL,
    currency   text   NOT NULL,
    lines      text   NOT NULL DEFAULT '[]',
    issued_at  bigint,
    due_at     bigint,
    notes      text,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    server_seq bigint NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE payment (
    id              text   NOT NULL PRIMARY KEY,
    studio_id       text   NOT NULL,
    invoice_id      text   NOT NULL,
    amount_minor    bigint NOT NULL,
    amount_currency text   NOT NULL,
    paid_at         bigint NOT NULL,
    method          text   NOT NULL,
    reference       text,
    notes           text,
    created_at      bigint NOT NULL,
    updated_at      bigint NOT NULL,
    deleted_at      bigint,
    version         bigint NOT NULL DEFAULT 1,
    server_seq      bigint NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoice(id)
);

CREATE TABLE expense (
    id                text    NOT NULL PRIMARY KEY,
    studio_id         text    NOT NULL,
    category          text    NOT NULL,
    description       text    NOT NULL,
    amount_minor      bigint  NOT NULL,
    amount_currency   text    NOT NULL,
    incurred_on       text    NOT NULL,
    project_id        text,
    vendor            text,
    is_tax_deductible boolean NOT NULL DEFAULT true,
    receipt_reference text,
    notes             text,
    created_at        bigint  NOT NULL,
    updated_at        bigint  NOT NULL,
    deleted_at        bigint,
    version           bigint  NOT NULL DEFAULT 1,
    server_seq        bigint  NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE mileage (
    id            text             NOT NULL PRIMARY KEY,
    studio_id     text             NOT NULL,
    travelled_on  text             NOT NULL,
    distance      double precision NOT NULL,
    unit          text             NOT NULL,
    rate_minor    bigint           NOT NULL,
    rate_currency text             NOT NULL,
    project_id    text,
    purpose       text,
    from_location text,
    to_location   text,
    created_at    bigint           NOT NULL,
    updated_at    bigint           NOT NULL,
    deleted_at    bigint,
    version       bigint           NOT NULL DEFAULT 1,
    server_seq    bigint           NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE codb_profile (
    id                         text   NOT NULL PRIMARY KEY,
    studio_id                  text   NOT NULL,
    currency                   text   NOT NULL,
    target_annual_salary_minor bigint NOT NULL,
    billable_days_per_year     bigint NOT NULL,
    tax_rate_basis_points      bigint NOT NULL DEFAULT 0,
    annual_overhead_minor      bigint,
    profit_margin_basis_points bigint NOT NULL DEFAULT 0,
    created_at                 bigint NOT NULL,
    updated_at                 bigint NOT NULL,
    deleted_at                 bigint,
    version                    bigint NOT NULL DEFAULT 1,
    server_seq                 bigint NOT NULL
);

-- --------------------------------------------------------------------------------------
-- The shoot day
-- --------------------------------------------------------------------------------------

CREATE TABLE shot (
    id          text    NOT NULL PRIMARY KEY,
    studio_id   text    NOT NULL,
    session_id  text    NOT NULL,
    description text    NOT NULL,
    group_name  text,
    people      text,
    position    bigint  NOT NULL DEFAULT 0,
    is_captured boolean NOT NULL DEFAULT false,
    captured_at bigint,
    notes       text,
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     bigint  NOT NULL DEFAULT 1,
    server_seq  bigint  NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id)
);

CREATE TABLE crew_member (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL,
    session_id text   NOT NULL,
    name       text   NOT NULL,
    role       text   NOT NULL,
    phone      text,
    call_time  bigint,
    notes      text,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    server_seq bigint NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id)
);

CREATE TABLE talent_release (
    id                 text   NOT NULL PRIMARY KEY,
    studio_id          text   NOT NULL,
    session_id         text   NOT NULL,
    person_name        text   NOT NULL,
    kind               text   NOT NULL,
    status             text   NOT NULL,
    signed_at          bigint,
    guardian_name      text,
    email              text,
    document_reference text,
    notes              text,
    created_at         bigint NOT NULL,
    updated_at         bigint NOT NULL,
    deleted_at         bigint,
    version            bigint NOT NULL DEFAULT 1,
    server_seq         bigint NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id)
);

-- --------------------------------------------------------------------------------------
-- After the shoot
-- --------------------------------------------------------------------------------------

CREATE TABLE post_task (
    id              text             NOT NULL PRIMARY KEY,
    studio_id       text             NOT NULL,
    project_id      text             NOT NULL,
    name            text             NOT NULL,
    kind            text             NOT NULL,
    status          text             NOT NULL,
    estimated_hours double precision,
    actual_hours    double precision,
    completed_at    bigint,
    notes           text,
    created_at      bigint           NOT NULL,
    updated_at      bigint           NOT NULL,
    deleted_at      bigint,
    version         bigint           NOT NULL DEFAULT 1,
    server_seq      bigint           NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE deliverable (
    id             text   NOT NULL PRIMARY KEY,
    studio_id      text   NOT NULL,
    project_id     text   NOT NULL,
    name           text   NOT NULL,
    kind           text   NOT NULL,
    status         text   NOT NULL,
    due_at         bigint,
    delivered_at   bigint,
    approved_at    bigint,
    revisions_used bigint NOT NULL DEFAULT 0,
    notes          text,
    created_at     bigint NOT NULL,
    updated_at     bigint NOT NULL,
    deleted_at     bigint,
    version        bigint NOT NULL DEFAULT 1,
    server_seq     bigint NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE storage_volume (
    id              text    NOT NULL PRIMARY KEY,
    studio_id       text    NOT NULL,
    label           text    NOT NULL,
    kind            text    NOT NULL,
    status          text    NOT NULL DEFAULT 'InUse',
    is_offsite      boolean NOT NULL DEFAULT false,
    last_checked_at bigint,
    notes           text,
    created_at      bigint  NOT NULL,
    updated_at      bigint  NOT NULL,
    deleted_at      bigint,
    version         bigint  NOT NULL DEFAULT 1,
    server_seq      bigint  NOT NULL
);

CREATE TABLE media_copy (
    id                  text    NOT NULL PRIMARY KEY,
    studio_id           text    NOT NULL,
    session_id          text    NOT NULL,
    volume_name         text    NOT NULL,
    kind                text    NOT NULL,
    is_offsite          boolean NOT NULL DEFAULT false,
    copied_at           bigint,
    verified_at         bigint,
    notes               text,
    created_at          bigint  NOT NULL,
    updated_at          bigint  NOT NULL,
    deleted_at          bigint,
    version             bigint  NOT NULL DEFAULT 1,
    -- Trailing for the same ALTER TABLE reason as `session.latitude`.
    volume_id           text,
    path                text,
    verified_file_count bigint,
    verified_bytes      bigint,
    server_seq          bigint  NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id),
    FOREIGN KEY (volume_id) REFERENCES storage_volume(id)
);

-- --------------------------------------------------------------------------------------
-- What the studio owns
-- --------------------------------------------------------------------------------------

CREATE TABLE gear_item (
    id                   text   NOT NULL PRIMARY KEY,
    studio_id            text   NOT NULL,
    name                 text   NOT NULL,
    category             text   NOT NULL,
    status               text   NOT NULL,
    serial_number        text,
    purchase_price_minor bigint,
    purchase_currency    text,
    purchased_on         text,
    last_serviced_at     bigint,
    notes                text,
    created_at           bigint NOT NULL,
    updated_at           bigint NOT NULL,
    deleted_at           bigint,
    version              bigint NOT NULL DEFAULT 1,
    server_seq           bigint NOT NULL
);

CREATE TABLE packing_entry (
    id           text    NOT NULL PRIMARY KEY,
    studio_id    text    NOT NULL,
    session_id   text    NOT NULL,
    gear_item_id text    NOT NULL,
    is_packed    boolean NOT NULL DEFAULT false,
    is_returned  boolean NOT NULL DEFAULT false,
    created_at   bigint  NOT NULL,
    updated_at   bigint  NOT NULL,
    deleted_at   bigint,
    version      bigint  NOT NULL DEFAULT 1,
    server_seq   bigint  NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id),
    FOREIGN KEY (gear_item_id) REFERENCES gear_item(id)
);

CREATE TABLE lighting_recipe (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL,
    name       text   NOT NULL,
    lights     text   NOT NULL DEFAULT '[]',
    notes      text,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    server_seq bigint NOT NULL
);

CREATE TABLE studio_profile (
    id                   text   NOT NULL PRIMARY KEY,
    studio_id            text   NOT NULL,
    name                 text   NOT NULL,
    address              text,
    email                text,
    phone                text,
    website              text,
    tax_number           text,
    payment_instructions text,
    document_footer      text,
    created_at           bigint NOT NULL,
    updated_at           bigint NOT NULL,
    deleted_at           bigint,
    version              bigint NOT NULL DEFAULT 1,
    -- Trailing for the same ALTER TABLE reason as `session.latitude`.
    currency             text   NOT NULL DEFAULT 'USD',
    server_seq           bigint NOT NULL
);

-- --------------------------------------------------------------------------------------
-- Indexes
-- --------------------------------------------------------------------------------------
--
-- The client's indexes, carried over so the same queries are supported on both sides.

CREATE INDEX client_studio_idx ON client(studio_id, deleted_at);
CREATE INDEX contact_studio_idx ON contact(studio_id, deleted_at);
CREATE UNIQUE INDEX client_contact_unique_idx ON client_contact(client_id, contact_id, role);
CREATE INDEX client_contact_client_idx ON client_contact(client_id, deleted_at);
CREATE INDEX project_studio_idx ON project(studio_id, deleted_at);
CREATE INDEX project_client_idx ON project(client_id, deleted_at);
CREATE INDEX session_studio_starts_idx ON session(studio_id, starts_at);
CREATE INDEX session_project_idx ON session(project_id, deleted_at);
CREATE INDEX service_template_studio_idx ON service_template(studio_id, deleted_at);
CREATE INDEX lead_studio_status_idx ON lead(studio_id, status, deleted_at);
CREATE INDEX lead_received_idx ON lead(studio_id, received_at);
CREATE INDEX quote_studio_idx ON quote(studio_id, deleted_at);
CREATE INDEX quote_project_idx ON quote(project_id, deleted_at);
CREATE INDEX contract_studio_idx ON contract(studio_id, deleted_at);
CREATE INDEX contract_project_idx ON contract(project_id, deleted_at);
CREATE INDEX invoice_studio_idx ON invoice(studio_id, deleted_at);
CREATE INDEX invoice_project_idx ON invoice(project_id, deleted_at);
CREATE INDEX invoice_due_idx ON invoice(studio_id, due_at);
CREATE INDEX payment_studio_idx ON payment(studio_id, deleted_at);
CREATE INDEX payment_invoice_idx ON payment(invoice_id, deleted_at);
CREATE INDEX expense_studio_date_idx ON expense(studio_id, incurred_on);
CREATE INDEX expense_project_idx ON expense(project_id, deleted_at);
CREATE INDEX mileage_studio_date_idx ON mileage(studio_id, travelled_on);
CREATE INDEX mileage_project_idx ON mileage(project_id, deleted_at);
CREATE INDEX codb_profile_studio_idx ON codb_profile(studio_id, deleted_at);
CREATE INDEX shot_session_idx ON shot(session_id, deleted_at);
CREATE INDEX crew_session_idx ON crew_member(session_id, deleted_at);
CREATE INDEX release_session_idx ON talent_release(session_id, deleted_at);
CREATE INDEX post_task_project_idx ON post_task(project_id, deleted_at);
CREATE INDEX deliverable_project_idx ON deliverable(project_id, deleted_at);
CREATE INDEX storage_volume_studio_idx ON storage_volume(studio_id, deleted_at);
CREATE INDEX media_copy_session_idx ON media_copy(session_id, deleted_at);
CREATE INDEX media_copy_volume_idx ON media_copy(volume_id, deleted_at);
CREATE INDEX gear_studio_idx ON gear_item(studio_id, deleted_at);
CREATE INDEX packing_session_idx ON packing_entry(session_id, deleted_at);
CREATE INDEX lighting_studio_idx ON lighting_recipe(studio_id, deleted_at);
CREATE UNIQUE INDEX studio_profile_studio_idx ON studio_profile(studio_id);

-- --------------------------------------------------------------------------------------
-- Sequence assignment and the pull index
-- --------------------------------------------------------------------------------------
--
-- Driven off the presence of the `server_seq` column rather than a list of table names.
-- A list would be a second place to remember, and the table added without its trigger is
-- exactly the row that never reaches a device.
--
-- The index is the one every pull uses: `WHERE studio_id = ? AND server_seq > ?`.
DO $$
DECLARE
    synced_table text;
BEGIN
    FOR synced_table IN
        SELECT table_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND column_name = 'server_seq'
        ORDER BY table_name
    LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION assign_server_seq()',
            synced_table || '_server_seq', synced_table
        );
        EXECUTE format(
            'CREATE INDEX %I ON %I(studio_id, server_seq)',
            synced_table || '_sync_idx', synced_table
        );
    END LOOP;
END;
$$;
