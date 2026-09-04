-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V1__init_schema.sql），
-- 两者必须保持同步：相同版本号、相同 schema，只允许 vendor 特定的类型/语法差异。

    create table achievements (
        display_order integer not null,
        is_active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        owner_id bigint not null,
        icon_variant varchar(20),
        status varchar(20),
        icon varchar(100),
        type varchar(100),
        subtitle varchar(200),
        title varchar(200) not null,
        description longtext not null,
        technologies longtext,
        primary key (id)
    ) engine=InnoDB;

    create table attachments (
        image_type integer,
        pid integer,
        att_id bigint not null auto_increment,
        att_size bigint,
        create_time datetime(6) not null,
        owner bigint,
        update_time datetime(6),
        user_id bigint,
        att_type varchar(50),
        att_dir varchar(500),
        satt_dir varchar(500),
        name varchar(255) not null,
        primary key (att_id)
    ) engine=InnoDB;

    create table bookings (
        created_at datetime(6) not null,
        end_time datetime(6),
        id bigint not null auto_increment,
        start_time datetime(6),
        updated_at datetime(6) not null,
        timezone varchar(100),
        event_slug varchar(191),
        external_id varchar(191) not null,
        email varchar(200),
        name varchar(200),
        raw_payload longtext,
        primary key (id)
    ) engine=InnoDB;

    create table bookmark_agent_job (
        completed_steps integer not null,
        total_steps integer not null,
        created_at datetime(6) not null,
        finished_at datetime(6),
        id bigint not null auto_increment,
        owner_id bigint not null,
        result_note_id bigint,
        phase_label varchar(30),
        error_message varchar(500),
        status enum ('RUNNING','DONE','FAILED') not null,
        type enum ('CLUSTER','TIMELINE') not null,
        primary key (id)
    ) engine=InnoDB;

    create table clip_search_messages (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        owner_id bigint not null,
        content TEXT not null,
        results_json TEXT,
        role enum ('USER','ASSISTANT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table clip_tag_links (
        ai_suggested bit not null,
        manually_added bit not null,
        clip_id bigint not null,
        id bigint not null auto_increment,
        linked_at datetime(6) not null,
        tag_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table contact_submission_notes (
        created_at datetime(6) not null,
        created_by_id bigint not null,
        id bigint not null auto_increment,
        submission_id bigint not null,
        content varchar(2000) not null,
        primary key (id)
    ) engine=InnoDB;

    create table contact_submissions (
        attachment_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        status varchar(20) not null,
        email varchar(200) not null,
        name varchar(200) not null,
        organization varchar(200),
        project_summary longtext not null,
        primary key (id)
    ) engine=InnoDB;

    create table feishu_document_snapshots (
        blocks_count integer,
        blocks_pages integer,
        content_size_bytes integer,
        is_compressed bit,
        raw_markdown_truncated bit,
        created_at datetime(6) not null,
        document_revision_id bigint,
        id bigint not null auto_increment,
        last_sync_at datetime(6),
        mapping_id bigint not null,
        note_id bigint,
        user_id bigint not null,
        conversion_strategy varchar(32),
        import_status varchar(32),
        sync_direction varchar(32),
        feishu_modified_time varchar(64),
        document_id varchar(128) not null,
        blocks_json longtext not null,
        converted_markdown longtext,
        import_error_message longtext,
        raw_markdown longtext,
        primary key (id)
    ) engine=InnoDB;

    create table feishu_image_mappings (
        align integer,
        height integer,
        width integer,
        attachment_id bigint,
        created_at datetime(6) not null,
        downloaded_at datetime(6),
        id bigint not null auto_increment,
        note_id bigint,
        snapshot_id bigint not null,
        user_id bigint not null,
        download_status varchar(32) not null,
        block_id varchar(128),
        document_id varchar(128) not null,
        local_url varchar(500),
        feishu_url varchar(1000),
        file_token varchar(255) not null,
        caption longtext,
        download_error_message longtext,
        primary key (id)
    ) engine=InnoDB;

    create table feishu_oauth_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6),
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        feishu_open_id varchar(64),
        feishu_user_id varchar(64),
        tenant_key varchar(64),
        access_token_enc longtext not null,
        refresh_token_enc longtext,
        primary key (id)
    ) engine=InnoDB;

    create table feishu_wiki_import_mappings (
        sync_enabled bit,
        total_snapshots integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        latest_snapshot_id bigint,
        note_id bigint,
        source_modified_at datetime(6),
        updated_at datetime(6) not null,
        user_id bigint not null,
        obj_type varchar(32),
        content_hash varchar(128),
        node_token varchar(128) not null,
        space_id varchar(128) not null,
        node_title varchar(512),
        source_url varchar(1024),
        primary key (id)
    ) engine=InnoDB;

    create table import_item (
        bookmark_added_at datetime(6),
        duplicate_of_clip_id bigint,
        id bigint not null auto_increment,
        job_id bigint not null,
        result_clip_id bigint,
        http_status varchar(20),
        folder_path varchar(500),
        raw_title varchar(500),
        normalized_url varchar(2000) not null,
        raw_url varchar(2000) not null,
        category enum ('NOISE','DUPLICATE','PENDING_CHECK','IMPORTABLE','DEAD_LINK') not null,
        noise_reason enum ('JS_BOOKMARKLET','INTERNAL_SCHEME'),
        user_decision enum ('PENDING','CONFIRMED','SKIPPED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table import_job (
        checked_count integer not null,
        total_count integer not null,
        created_at datetime(6) not null,
        finished_at datetime(6),
        id bigint not null auto_increment,
        owner_id bigint not null,
        file_name varchar(255),
        status enum ('PARSING','CHECKING','READY','DONE','FAILED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table local_doc_directories (
        document_count integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_scan_at datetime(6),
        owner_id bigint not null,
        scan_status varchar(20) not null,
        display_name varchar(200),
        dir_path varchar(1000) not null,
        last_scan_error longtext,
        primary key (id)
    ) engine=InnoDB;

    create table local_documents (
        directory_id bigint not null,
        file_last_modified datetime(6),
        file_size bigint,
        id bigint not null auto_increment,
        owner_id bigint not null,
        snapshot_created_at datetime(6) not null,
        file_type varchar(20) not null,
        file_name varchar(500) not null,
        absolute_path varchar(2000) not null,
        relative_path varchar(2000),
        primary key (id)
    ) engine=InnoDB;

    create table local_media_directories (
        file_count integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_scan_at datetime(6),
        owner_id bigint not null,
        scan_status varchar(20) not null,
        display_name varchar(200),
        dir_path varchar(1000) not null,
        last_scan_error longtext,
        primary key (id)
    ) engine=InnoDB;

    create table local_media_files (
        image_height integer,
        image_width integer,
        directory_id bigint not null,
        file_last_modified datetime(6),
        file_size bigint,
        id bigint not null auto_increment,
        owner_id bigint not null,
        snapshot_created_at datetime(6) not null,
        media_type varchar(10) not null,
        file_extension varchar(20),
        file_name varchar(500) not null,
        absolute_path varchar(2000) not null,
        relative_path varchar(2000),
        primary key (id)
    ) engine=InnoDB;

    create table news_item (
        fetch_date date not null,
        rank_order integer not null,
        fetched_at datetime(6) not null,
        id bigint not null auto_increment,
        source_key varchar(50) not null,
        title varchar(500) not null,
        url varchar(1000),
        primary key (id)
    ) engine=InnoDB;

    create table news_source_config (
        enabled bit not null,
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_fetched_at datetime(6),
        category varchar(10) not null,
        last_fetch_status varchar(20),
        source_key varchar(50) not null,
        name_en varchar(100) not null,
        name_zh varchar(100) not null,
        last_fetch_error varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table note_clip_refs (
        sort_order integer not null,
        clip_id bigint not null,
        id bigint not null auto_increment,
        linked_at datetime(6) not null,
        note_id bigint not null,
        user_note longtext,
        primary key (id)
    ) engine=InnoDB;

    create table note_section_types (
        section_order integer not null,
        note_id bigint not null,
        section_type varchar(50),
        primary key (section_order, note_id)
    ) engine=InnoDB;

    create table note_sections (
        section_order integer not null,
        note_id bigint not null,
        section_content longtext,
        primary key (section_order, note_id)
    ) engine=InnoDB;

    create table note_tags (
        note_id bigint not null,
        tag_id bigint not null,
        primary key (note_id, tag_id)
    ) engine=InnoDB;

    create table notes (
        is_public bit not null,
        view_count integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        owner_id bigint not null,
        project_id bigint,
        source_note_id bigint,
        language varchar(10),
        content_type varchar(20) not null,
        title varchar(200) not null,
        content LONGTEXT,
        summary TEXT,
        generated_type enum ('CLUSTER','TIMELINE'),
        primary key (id)
    ) engine=InnoDB;

    create table profiles (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        user_id bigint not null,
        full_name varchar(100),
        site_name varchar(100),
        email varchar(200),
        github varchar(200),
        linkedin varchar(200),
        location varchar(200),
        title varchar(200),
        twitter varchar(200),
        website varchar(200),
        wechat varchar(200),
        avatar_url varchar(500),
        logo_url varchar(500),
        wechat_qr_url varchar(500),
        bio longtext,
        education longtext,
        experience longtext,
        skills longtext,
        primary key (id)
    ) engine=InnoDB;

    create table projects (
        display_order integer not null,
        is_featured bit not null,
        is_public bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        owner_id bigint not null,
        content_type varchar(20),
        short_name varchar(20),
        category varchar(100),
        icon varchar(100),
        name varchar(200) not null,
        subtitle varchar(200),
        github_url varchar(500),
        image_url varchar(500),
        project_url varchar(500),
        content longtext,
        description longtext not null,
        technologies longtext,
        primary key (id)
    ) engine=InnoDB;

    create table reddit_publish_logs (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        note_id bigint not null,
        user_id bigint not null,
        subreddit varchar(128) not null,
        post_title varchar(300) not null,
        post_url varchar(512),
        error_message longtext,
        status enum ('SUCCESS','FAILED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table reddit_user_auth (
        created_at datetime(6) not null,
        expires_at datetime(6),
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        reddit_username varchar(128) not null,
        scope varchar(512),
        access_token_enc longtext not null,
        refresh_token_enc longtext,
        primary key (id)
    ) engine=InnoDB;

    create table resources (
        display_order integer not null,
        is_featured bit not null,
        is_public bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        owner_id bigint not null,
        category varchar(100) not null,
        icon varchar(100),
        name varchar(200) not null,
        url varchar(500) not null,
        description longtext not null,
        tags longtext,
        primary key (id)
    ) engine=InnoDB;

    create table services (
        display_order integer not null,
        is_active bit not null,
        is_featured bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        modified_at datetime(6) not null,
        owner_id bigint not null,
        category varchar(100),
        icon varchar(100),
        name varchar(200) not null,
        description longtext not null,
        detailed_description longtext,
        features longtext,
        pricing longtext,
        primary key (id)
    ) engine=InnoDB;

    create table site_settings (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        site_name varchar(100),
        logo_url varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table source_clips (
        manually_confirmed_alive bit not null,
        tags_manually_adjusted bit not null,
        was_detected_dead_link bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_accessed_at datetime(6),
        modified_at datetime(6) not null,
        original_bookmarked_at datetime(6),
        owner_id bigint not null,
        content_format varchar(20),
        ai_model varchar(100),
        source_author varchar(200),
        title varchar(200) not null,
        source_title varchar(500),
        source_url varchar(1000),
        content longtext,
        excerpt longtext,
        extraction_mode enum ('FULL','LINK_ONLY') not null,
        extraction_status enum ('SUCCESS','FAILED'),
        source_type enum ('WEBPAGE','WECHAT_ARTICLE','WECHAT_CHAT_TEXT','WECHAT_CHAT_IMAGE') not null,
        primary key (id)
    ) engine=InnoDB;

    create table tags (
        used_by_clips bit not null,
        used_by_notes bit not null,
        id bigint not null auto_increment,
        owner_id bigint not null,
        name varchar(50) not null,
        primary key (id)
    ) engine=InnoDB;

    create table timesheet_entries (
        duration_minutes integer not null,
        end_time time(6),
        entry_date date not null,
        start_time time(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        project_id bigint,
        updated_at datetime(6) not null,
        label varchar(200),
        note longtext,
        primary key (id)
    ) engine=InnoDB;

    create table user_credentials (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        uid bigint not null,
        updated_at datetime(6) not null,
        provider varchar(50) not null,
        app_id varchar(200) not null,
        app_secret varchar(2000) not null,
        primary key (id)
    ) engine=InnoDB;

    create table users (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        role varchar(20) not null,
        username varchar(50) not null,
        email varchar(100),
        password varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table wechat_bindings (
        bound_at datetime(6),
        code_expires_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        bind_code varchar(10),
        openid varchar(64),
        status enum ('PENDING','BOUND','EXPIRED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table wechat_publish_logs (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        note_id bigint not null,
        user_id bigint not null,
        mode varchar(10),
        status varchar(10),
        wx_author varchar(32),
        wx_title varchar(64),
        media_id varchar(100),
        publish_id varchar(100),
        error_message longtext,
        primary key (id)
    ) engine=InnoDB;

    alter table bookings 
       add constraint UK_mk1onpvggblu0v2xsbrlsatg9 unique (external_id);

    alter table clip_tag_links 
       add constraint UK95rgdxsrwy8j0m6wfc5r6rnqg unique (clip_id, tag_id);

    create index idx_mapping_id 
       on feishu_document_snapshots (mapping_id);

    create index idx_note_id 
       on feishu_document_snapshots (note_id);

    create index idx_document_id 
       on feishu_document_snapshots (document_id);

    create index idx_user_id 
       on feishu_document_snapshots (user_id);

    create index idx_created_at 
       on feishu_document_snapshots (created_at);

    create index idx_mapping_created 
       on feishu_document_snapshots (mapping_id, created_at);

    create index idx_document_created 
       on feishu_document_snapshots (document_id, created_at);

    create index idx_snapshot_id 
       on feishu_image_mappings (snapshot_id);

    create index idx_fim_note_id 
       on feishu_image_mappings (note_id);

    create index idx_fim_document_id 
       on feishu_image_mappings (document_id);

    create index idx_fim_user_id 
       on feishu_image_mappings (user_id);

    create index idx_download_status 
       on feishu_image_mappings (download_status);

    alter table feishu_image_mappings 
       add constraint UK_co037nv2yfiptewn8psyht4uf unique (file_token);

    alter table feishu_oauth_tokens 
       add constraint UK_stwy2xkyb2onk72c6xkh61dc9 unique (user_id);

    alter table feishu_wiki_import_mappings 
       add constraint UKbc600o2jf0bfan1l92gkpblqs unique (user_id, space_id, node_token);

    alter table feishu_wiki_import_mappings 
       add constraint UK_8frxrib0rsafh4ej2te3anu2h unique (latest_snapshot_id);

    alter table feishu_wiki_import_mappings 
       add constraint UK_eprm3g92x57svienla8rm2csj unique (note_id);

    create index idx_ldd_owner 
       on local_doc_directories (owner_id);

    alter table local_doc_directories 
       add constraint UKg0bmgl8xct5r0s6ykt5lcvs00 unique (owner_id, dir_path);

    create index idx_ld_directory 
       on local_documents (directory_id);

    create index idx_ld_owner 
       on local_documents (owner_id);

    create index idx_ld_file_type 
       on local_documents (file_type);

    create index idx_ld_file_name 
       on local_documents (file_name);

    create index idx_lmd_owner 
       on local_media_directories (owner_id);

    alter table local_media_directories 
       add constraint UK7k4ybh60o95yf2t72pml9dc5j unique (owner_id, dir_path);

    create index idx_lmf_directory 
       on local_media_files (directory_id);

    create index idx_lmf_owner 
       on local_media_files (owner_id);

    create index idx_lmf_media_type 
       on local_media_files (media_type);

    create index idx_lmf_file_name 
       on local_media_files (file_name);

    create index idx_ni_source_date 
       on news_item (source_key, fetch_date);

    create index idx_ni_fetch_date 
       on news_item (fetch_date);

    create index idx_nsc_category 
       on news_source_config (category);

    create index idx_nsc_sort 
       on news_source_config (sort_order);

    alter table news_source_config 
       add constraint UK_sk1s7w1plt3msktv2de8lorr6 unique (source_key);

    alter table note_clip_refs 
       add constraint UK1bp4kus2a826h2hrlds07515o unique (note_id, clip_id);

    alter table profiles 
       add constraint UK_4ixsj6aqve5pxrbw2u0oyk8bb unique (user_id);

    alter table reddit_user_auth 
       add constraint UK_o7ck2ob7lsin4swr4ghaq86em unique (user_id);

    alter table tags 
       add constraint UKb79b0c5nhcyj19v5obp52908f unique (name, owner_id);

    create index idx_user_credentials_uid_provider 
       on user_credentials (uid, provider);

    alter table user_credentials 
       add constraint uk_user_credentials_uid_provider unique (uid, provider);

    alter table users 
       add constraint UK_r43af9ap4edm43mmtq01oddj6 unique (username);

    alter table users 
       add constraint UK_6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table achievements 
       add constraint FK6l3b7vgp9oglk6k7104wv7kvf 
       foreign key (owner_id) 
       references users (id);

    alter table attachments 
       add constraint FKrap79tymgdjf1c5x4dla3rekl 
       foreign key (user_id) 
       references users (id);

    alter table bookmark_agent_job 
       add constraint FK4o3ex1kqpmhfnmyw11hxc33xn 
       foreign key (owner_id) 
       references users (id);

    alter table clip_search_messages 
       add constraint FKcc09h39tahamlcavaoh9wdrtr 
       foreign key (owner_id) 
       references users (id);

    alter table clip_tag_links 
       add constraint FK8nokpg6b95lmpexc79u9oah9r 
       foreign key (clip_id) 
       references source_clips (id) 
       on delete cascade;

    alter table clip_tag_links 
       add constraint FK8rjoi82vbwf6aunghy4a9updk 
       foreign key (tag_id) 
       references tags (id) 
       on delete cascade;

    alter table contact_submission_notes 
       add constraint FKbl2eu6ek5p3x4cbirn13n3gq8 
       foreign key (created_by_id) 
       references users (id);

    alter table contact_submission_notes 
       add constraint FK3p5lnsfw70inalwjvh6i38fik 
       foreign key (submission_id) 
       references contact_submissions (id);

    alter table contact_submissions 
       add constraint FKkijcnts80q614f3w7976wn20q 
       foreign key (attachment_id) 
       references attachments (att_id);

    alter table feishu_document_snapshots 
       add constraint FKs9ag5kj0piekiy980g6523ei3 
       foreign key (mapping_id) 
       references feishu_wiki_import_mappings (id);

    alter table feishu_document_snapshots 
       add constraint FK2xkd3eswsiam0q4n3r1deb43v 
       foreign key (note_id) 
       references notes (id);

    alter table feishu_document_snapshots 
       add constraint FKe50aief8qy2mynibmeu1fg8f7 
       foreign key (user_id) 
       references users (id);

    alter table feishu_image_mappings 
       add constraint FK1putipwa6vw06diqy74nnraom 
       foreign key (attachment_id) 
       references attachments (att_id);

    alter table feishu_image_mappings 
       add constraint FK7n6h80r3mbtbrdafv7qi3x5ak 
       foreign key (note_id) 
       references notes (id);

    alter table feishu_image_mappings 
       add constraint FKsh7p1g62hsrpbw8fmxxvhokd 
       foreign key (snapshot_id) 
       references feishu_document_snapshots (id);

    alter table feishu_image_mappings 
       add constraint FKndg94u94fjr1basumh7rwkw6d 
       foreign key (user_id) 
       references users (id);

    alter table feishu_oauth_tokens 
       add constraint FKp7a7qfk04kbpnw167vyyirkpo 
       foreign key (user_id) 
       references users (id);

    alter table feishu_wiki_import_mappings 
       add constraint FK9jbuye94036piq20e6tb7pj93 
       foreign key (latest_snapshot_id) 
       references feishu_document_snapshots (id);

    alter table feishu_wiki_import_mappings 
       add constraint FK8bwdi6v4daim86cbsyo0f5dsh 
       foreign key (note_id) 
       references notes (id);

    alter table feishu_wiki_import_mappings 
       add constraint FK4v0v958y39upqsn84ifimvgw5 
       foreign key (user_id) 
       references users (id);

    alter table import_item 
       add constraint FKgq6qawhfhrstk0h6q8996wlhi 
       foreign key (job_id) 
       references import_job (id);

    alter table import_job 
       add constraint FKncka7a7bpq636cmcsvrnvaj10 
       foreign key (owner_id) 
       references users (id);

    alter table local_doc_directories 
       add constraint FKfcvkb15pbj2fv3p03h4rwa4hv 
       foreign key (owner_id) 
       references users (id);

    alter table local_documents 
       add constraint FKjdrs71ru9s98b1gdm93rr6wv4 
       foreign key (directory_id) 
       references local_doc_directories (id);

    alter table local_documents 
       add constraint FK3pk4yvm7secpyg0t9p0kdbbfx 
       foreign key (owner_id) 
       references users (id);

    alter table local_media_directories 
       add constraint FKjybowpi1ncc6cmxoolqx54oof 
       foreign key (owner_id) 
       references users (id);

    alter table local_media_files 
       add constraint FK7i8juo71gj7k7s9ovem76srkt 
       foreign key (directory_id) 
       references local_media_directories (id);

    alter table local_media_files 
       add constraint FK54psjjr1h140a7wpiogctq8ey 
       foreign key (owner_id) 
       references users (id);

    alter table note_clip_refs 
       add constraint FKemkabqagq18ihc4m8vfey410a 
       foreign key (clip_id) 
       references source_clips (id);

    alter table note_clip_refs 
       add constraint FKr8qkrnv3nl5a9fw7w1qhpqwqw 
       foreign key (note_id) 
       references notes (id);

    alter table note_section_types 
       add constraint FK45f896mb6irf80lf5u1tw9h8f 
       foreign key (note_id) 
       references notes (id);

    alter table note_sections 
       add constraint FKi4h2ixa94my7d36n6klat7kf2 
       foreign key (note_id) 
       references notes (id);

    alter table note_tags 
       add constraint FK8babdwu6uqiu4rdkeuy8dkna0 
       foreign key (tag_id) 
       references tags (id);

    alter table note_tags 
       add constraint FKb15yxop81senc5xs5tjrsy4k4 
       foreign key (note_id) 
       references notes (id);

    alter table notes 
       add constraint FK5n5jgcd6tqt248r97q0yrt3xp 
       foreign key (owner_id) 
       references users (id);

    alter table notes 
       add constraint FKf5kwkuxo55mgr2vkluhrh7tth 
       foreign key (project_id) 
       references projects (id);

    alter table notes 
       add constraint FKmgt4jtaoph07qv3sfwk1m1gdu 
       foreign key (source_note_id) 
       references notes (id);

    alter table profiles 
       add constraint FK410q61iev7klncmpqfuo85ivh 
       foreign key (user_id) 
       references users (id);

    alter table projects 
       add constraint FKmueqy6cpcwpfl8gnnag4idjt9 
       foreign key (owner_id) 
       references users (id);

    alter table reddit_publish_logs 
       add constraint FK2i3wwv4047g6u0o7c4awkgj02 
       foreign key (note_id) 
       references notes (id);

    alter table reddit_publish_logs 
       add constraint FKhie1uye7tp3qhyrii4ogc5bx5 
       foreign key (user_id) 
       references users (id);

    alter table reddit_user_auth 
       add constraint FK31un24mrpkryo7ust8gv17h7s 
       foreign key (user_id) 
       references users (id);

    alter table resources 
       add constraint FK89t6p8a75xsy1pikkwcx85grf 
       foreign key (owner_id) 
       references users (id);

    alter table services 
       add constraint FKelgjlc4287c3492tviyvrv52f 
       foreign key (owner_id) 
       references users (id);

    alter table source_clips 
       add constraint FKtbrug1dirmd9myxo10o6081d2 
       foreign key (owner_id) 
       references users (id);

    alter table tags 
       add constraint FKx0nceen1ii190w7et5r042qt 
       foreign key (owner_id) 
       references users (id);

    alter table timesheet_entries 
       add constraint FK9b4mie5i1xq36af6a87iucp5t 
       foreign key (project_id) 
       references projects (id);

    alter table user_credentials 
       add constraint FK86jxhugpvlrouvsseyxd2yaun 
       foreign key (uid) 
       references users (id);

    alter table wechat_bindings 
       add constraint FKpd453sx5dl6rc63qrvi3tarx3 
       foreign key (user_id) 
       references users (id);

    alter table wechat_publish_logs 
       add constraint FKpvmwrtrlbnxht58c5fxr5n9jv 
       foreign key (note_id) 
       references notes (id);

    alter table wechat_publish_logs 
       add constraint FKgyoxnu2u15451svx1ydren2e5 
       foreign key (user_id) 
       references users (id);
